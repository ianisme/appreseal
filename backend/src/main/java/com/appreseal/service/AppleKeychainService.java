package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.config.AppProperties;
import com.appreseal.domain.AppleAccount;
import com.appreseal.domain.AppleStatus;
import com.appreseal.executor.IpatoolLock;
import com.appreseal.repo.AppleAccountRepository;
import com.appreseal.web.dto.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在 Linux 容器里用 ipatool 登录，并把文件密钥（{@code ~/.ipatool/account}）
 * 快照到 {@code accounts/<邮箱>.session}。密码只传给这一次进程，不写数据库、不写日志。
 */
@Service
public class AppleKeychainService {
    private static final Logger log = LoggerFactory.getLogger(AppleKeychainService.class);

    private final AppProperties props;
    private final AppleAccountRepository accounts;
    private final AuditService audit;
    private final IpatoolLock lock;
    private final TransactionTemplate tx;

    public AppleKeychainService(AppProperties props, AppleAccountRepository accounts, AuditService audit,
                                IpatoolLock lock, PlatformTransactionManager transactions) {
        this.props = props;
        this.accounts = accounts;
        this.audit = audit;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactions);
    }

    public Dtos.AppleLoginResult login(String email, String password, String authCode, String storefront,
                                      String actor, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String code = authCode == null ? "" : authCode.trim();
        if (!code.isEmpty() && !code.matches("[0-9]{6}")) {
            throw ApiException.badRequest("双重验证码必须是 6 位数字");
        }
        Path binary = resolveBinary();
        if (binary == null) {
            throw ApiException.badRequest("当前环境没有 ipatool，无法生成文件密钥");
        }

        lock.lock();
        try {
            Path home = Path.of(props.getIpatool().getHome()).toAbsolutePath().normalize();
            Files.createDirectories(home.resolve(".ipatool"));
            Files.createDirectories(home.resolve("accounts"));
            String passphrase = ensurePassphrase(home);

            List<String> cmd = new ArrayList<>();
            cmd.add(binary.toString());
            cmd.add("auth");
            cmd.add("login");
            cmd.add("--non-interactive");
            cmd.add("--format");
            cmd.add("json");
            cmd.add("-e");
            cmd.add(normalized);
            cmd.add("-p");
            cmd.add(password);
            if (!code.isEmpty()) {
                cmd.add("--auth-code");
                cmd.add(code);
            }
            cmd.add("--keychain-passphrase");
            cmd.add(passphrase);

            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.environment().put("HOME", home.toString());
            builder.environment().put("DBUS_SESSION_BUS_ADDRESS", "unix:path=/nonexistent-appget");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder captured = new StringBuilder();
            Thread pump = new Thread(() -> captured.append(readQuietly(process.getInputStream())), "ipatool-login");
            pump.setDaemon(true);
            pump.start();
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                pump.join(2000);
                throw ApiException.badRequest("登录超时");
            }
            pump.join(2000);
            String output = captured.toString();
            String safe = redact(output, password, code);
            // ipatool 在非交互模式遇到 2FA 时会打印提示并返回 0，不能只看退出码。
            if (needs2fa(safe)) {
                return new Dtos.AppleLoginResult("NEED_2FA", "Apple 要求双重验证码，请填写后再次提交", null);
            }
            if (process.exitValue() != 0 || !loginSucceeded(safe)) {
                log.warn("ipatool 登录失败 email={} out={}", normalized, safe);
                throw ApiException.badRequest(mapError(safe));
            }
            snapshot(home, normalized);
            String market = Storefronts.normalize(storefront);
            AppleAccount account = tx.execute(status -> markReady(normalized, market));
            audit.log(actor, "APPLE_LOGIN", normalized, "已生成文件密钥（密码未保存）", ip);
            return new Dtos.AppleLoginResult("READY", "已登录并生成文件密钥", Dtos.AppleAccountDto.from(account));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("生成文件密钥失败 email={}: {}", normalized, e.getMessage());
            throw ApiException.badRequest("生成文件密钥失败");
        } finally {
            lock.unlock();
        }
    }

    private Path resolveBinary() {
        String configured = props.getIpatool().getPath();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isExecutable(path)) {
                return path;
            }
        }
        Path bundled = Path.of("/opt/ipatool/ipatool");
        return Files.isExecutable(bundled) ? bundled : null;
    }

    private String ensurePassphrase(Path home) throws Exception {
        Path file = home.resolve(".ipatool-passphrase");
        if (Files.isRegularFile(file)) {
            String existing = Files.readString(file).trim();
            if (!existing.isEmpty()) {
                return existing;
            }
        }
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String value = HexFormat.of().formatHex(random);
        Files.writeString(file, value);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows 测试环境没有 POSIX 权限
        }
        return value;
    }

    private void snapshot(Path home, String email) throws Exception {
        Path account = home.resolve(".ipatool").resolve("account");
        if (!Files.isRegularFile(account) || Files.size(account) == 0) {
            throw ApiException.badRequest("登录返回成功，但没有写出文件密钥");
        }
        Path session = IpatoolFiles.session(home, email);
        Files.writeString(session, Base64.getEncoder().encodeToString(Files.readAllBytes(account)));
        IpatoolFiles.restrict(session);
        Path cookies = home.resolve(".ipatool").resolve("cookies");
        if (Files.isRegularFile(cookies)) {
            Path copy = home.resolve("accounts").resolve(IpatoolFiles.slug(email) + ".cookies");
            Files.copy(cookies, copy, StandardCopyOption.REPLACE_EXISTING);
            IpatoolFiles.restrict(copy);
        }
    }

    /** 把 16 字节 credential.bin 绑到 Apple ID。文件只落在数据卷，不入库、不回显。 */
    public Dtos.AppleAccountDto bindCredential(String email, String storefront, byte[] bytes, String actor, String ip) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw ApiException.badRequest("请输入 Apple ID");
        }
        if (bytes == null || bytes.length != 16) {
            throw ApiException.badRequest("credential.bin 必须是 16 字节");
        }
        lock.lock();
        try {
            Path home = IpatoolFiles.home(props);
            Path file = IpatoolFiles.credential(home, normalized);
            Path dir = file.getParent();
            Files.createDirectories(dir);
            Path tmp = dir.resolve("credential.bin.tmp");
            Files.write(tmp, bytes);
            IpatoolFiles.restrict(tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            IpatoolFiles.restrict(file);
            String market = Storefronts.normalize(storefront);
            AppleAccount account = tx.execute(status -> markCredential(normalized, market));
            audit.log(actor, "APPLE_CREDENTIAL", normalized, "已绑定 credential.bin（16 字节，内容未记录）", ip);
            return toDto(account);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("绑定凭证失败 email={}: {}", normalized, e.getMessage());
            throw ApiException.badRequest("绑定凭证失败");
        } finally {
            lock.unlock();
        }
    }

    public boolean credentialPresent(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Path file = credentialFile(email.trim().toLowerCase(Locale.ROOT));
        try {
            return Files.isRegularFile(file) && Files.size(file) == 16;
        } catch (Exception e) {
            return false;
        }
    }

    public Dtos.AppleAccountDto toDto(AppleAccount account) {
        boolean credentialOk = credentialPresent(account.email) || account.credentialOk;
        return new Dtos.AppleAccountDto(account.id, account.email, account.storefront, account.priority, credentialOk,
                account.sessionOk, account.status, account.boundApps, account.lastUsedAt);
    }

    public AppleAccount markReady(String email, String storefront) {
        AppleAccount account = accounts.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppleAccount created = new AppleAccount();
            created.id = Ids.of("aa");
            created.email = email;
            created.priority = accounts.findAll().stream().mapToInt(a -> a.priority).max().orElse(0) + 1;
            created.credentialOk = false;
            return created;
        });
        account.storefront = storefront;
        account.credentialOk = credentialPresent(email);
        account.sessionOk = true;
        if (account.status != AppleStatus.DISABLED) {
            account.status = AppleStatus.READY;
        }
        account.lastUsedAt = Instant.now();
        return accounts.save(account);
    }

    private AppleAccount markCredential(String email, String storefront) {
        AppleAccount account = accounts.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppleAccount created = new AppleAccount();
            created.id = Ids.of("aa");
            created.email = email;
            created.priority = accounts.findAll().stream().mapToInt(a -> a.priority).max().orElse(0) + 1;
            created.sessionOk = false;
            created.status = AppleStatus.NEED_LOGIN;
            return created;
        });
        account.storefront = storefront;
        account.credentialOk = true;
        return accounts.save(account);
    }

    private Path credentialFile(String email) {
        return IpatoolFiles.credential(IpatoolFiles.home(props), email);
    }

    private static boolean needs2fa(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        return lower.contains("2fa") || lower.contains("auth-code") || lower.contains("auth code");
    }

    private static boolean loginSucceeded(String output) {
        String compact = output.replace(" ", "").toLowerCase(Locale.ROOT);
        return compact.contains("\"success\":true") || compact.contains("success=true");
    }

    private static String mapError(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("password") || lower.contains("credential") || lower.contains("invalid")) {
            return "Apple ID 或密码不正确";
        }
        String line = output.lines().filter(s -> !s.isBlank()).reduce((a, b) -> b).orElse("登录失败");
        if (line.length() > 160) {
            line = line.substring(0, 160);
        }
        return "登录失败：" + line;
    }

    private static String redact(String output, String password, String code) {
        String safe = output == null ? "" : output;
        if (password != null && password.length() >= 3) {
            safe = safe.replace(password, "******");
        }
        if (code != null && code.length() == 6) {
            safe = safe.replace(code, "******");
        }
        return safe;
    }

    private static String readQuietly(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
