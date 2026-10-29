package com.appreseal.executor;

import com.appreseal.config.AppProperties;
import com.appreseal.domain.AppleAccount;
import com.appreseal.domain.AppleStatus;
import com.appreseal.domain.Task;
import com.appreseal.repo.AppleAccountRepository;
import com.appreseal.service.IpatoolFiles;
import com.appreseal.service.Storefronts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为单个任务准备 appget 的临时工作区。
 *
 * <p>账号只从数据库里匹配任务所选市场，并确认文件密钥与 credential.bin 都在数据卷里。
 * 每次任务都写一份独立 accounts.json，避免不同市场、不同任务之间互相污染。
 */
@Component
public class AppgetWorkspace {
    private static final Logger log = LoggerFactory.getLogger(AppgetWorkspace.class);

    private final AppProperties props;
    private final AppleAccountRepository accounts;
    private final ObjectMapper json;

    public AppgetWorkspace(AppProperties props, AppleAccountRepository accounts, ObjectMapper json) {
        this.props = props;
        this.accounts = accounts;
        this.json = json;
    }

    public record Prepared(Path workDir, Path config, Path outputDir, Path downloadDir,
                           List<AppleAccount> candidates) {}

    /** 配置不可执行时抛出，调用方按任务失败处理并退款。 */
    public static class UnavailableException extends Exception {
        public UnavailableException(String message) {
            super(message);
        }
    }

    public Prepared prepare(Task task) throws IOException, UnavailableException {
        Path home = IpatoolFiles.home(props);
        Path work = Path.of(props.getExecutor().getWorkDir()).toAbsolutePath().normalize().resolve(task.id);
        Path out = work.resolve("out");
        Path downloads = work.resolve("downloads");
        Files.createDirectories(out);
        Files.createDirectories(downloads);

        List<AppleAccount> matching = accounts.findAllByOrderByPriorityAsc().stream()
                .filter(a -> a.status != AppleStatus.DISABLED)
                .filter(a -> task.storefront == null || task.storefront.isBlank()
                        || task.storefront.equals(a.storefront))
                .toList();
        if (matching.isEmpty()) {
            String market = task.storefront == null || task.storefront.isBlank()
                    ? "" : "「" + Storefronts.name(task.storefront) + "」";
            throw new UnavailableException("Apple ID 池没有" + market + "市场的可用账号，请先在后台登录并标记市场");
        }

        List<AppleAccount> usable = matching.stream()
                .filter(a -> sessionPresent(home, a.email))
                .filter(a -> credentialPresent(home, a.email))
                .toList();
        if (usable.isEmpty()) {
            throw new UnavailableException("匹配的 Apple ID 缺少文件密钥或 credential.bin，请到后台重新登录并导入凭证");
        }

        Path config = work.resolve("accounts.json");
        ObjectNode root = json.createObjectNode();
        ArrayNode items = root.putArray("accounts");
        for (AppleAccount account : usable) {
            ObjectNode item = items.addObject();
            item.put("email", account.email);
            item.put("credential", IpatoolFiles.credential(home, account.email).toString());
            item.put("priority", account.priority);
        }
        root.putObject("apps");
        json.writerWithDefaultPrettyPrinter().writeValue(config.toFile(), root);
        IpatoolFiles.restrict(config);

        log.info("任务 {} 使用市场 {}，匹配到 {} 个可用 Apple ID", task.id,
                task.storefront == null ? "未指定" : Storefronts.name(task.storefront), usable.size());
        return new Prepared(work, config, out, downloads, usable);
    }

    /** 上传包只需要 credential.bin，不需要会话，也不按市场过滤。 */
    public Prepared prepareUpload(Task task) throws IOException, UnavailableException {
        Path home = IpatoolFiles.home(props);
        Path work = Path.of(props.getExecutor().getWorkDir()).toAbsolutePath().normalize().resolve(task.id);
        Path out = work.resolve("out");
        Path downloads = work.resolve("downloads");
        Files.createDirectories(out);
        Files.createDirectories(downloads);

        List<AppleAccount> usable = accounts.findAllByOrderByPriorityAsc().stream()
                .filter(a -> a.status != AppleStatus.DISABLED)
                .filter(a -> credentialPresent(home, a.email))
                .toList();
        if (usable.isEmpty()) {
            throw new UnavailableException("Apple ID 池里没有可用的 credential.bin，请先在后台导入凭证");
        }
        log.info("上传任务 {} 可用凭证 {} 份", task.id, usable.size());
        return new Prepared(work, null, out, downloads, usable);
    }

    public Path credential(AppleAccount account) {
        return IpatoolFiles.credential(IpatoolFiles.home(props), account.email);
    }

    /** 传给 appget.sh 的容器内环境。密钥只从数据卷读，不写数据库、不写日志。 */
    public Map<String, String> environment(Prepared prepared) throws IOException, UnavailableException {
        Path home = IpatoolFiles.home(props);
        Path binary = ipatoolBinary();
        String passphrase = IpatoolFiles.readPassphrase(home);
        if (passphrase.isBlank()) {
            throw new UnavailableException("数据卷里没有 ipatool 文件密钥口令，请先在后台登录 Apple ID");
        }

        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", home.toString());
        env.put("DBUS_SESSION_BUS_ADDRESS", "unix:path=/nonexistent-appget");
        env.put("IPATOOL", binary.toString());
        env.put("IPATOOL_KEYCHAIN_PASSPHRASE", passphrase);
        env.put("ACC_CONFIG", prepared.config().toString());
        env.put("SESS_DIR", home.resolve("accounts").toString());
        env.put("DL_DIR", prepared.downloadDir().toString());
        env.put("OUT_DIR", prepared.outputDir().toString());
        env.put("LC_ALL", "C.UTF-8");
        env.put("LANG", "C.UTF-8");

        String proxy = props.getExecutor().getProxy();
        if (proxy != null && !proxy.isBlank()) {
            env.put("HTTP_PROXY", proxy.trim());
            env.put("HTTPS_PROXY", proxy.trim());
            env.put("http_proxy", proxy.trim());
            env.put("https_proxy", proxy.trim());
        }
        return env;
    }

    public Path ipatoolBinary() throws UnavailableException {
        String configured = props.getIpatool().getPath();
        Path path = configured == null || configured.isBlank()
                ? Path.of("/opt/ipatool/ipatool")
                : Path.of(configured);
        if (!Files.isExecutable(path)) {
            throw new UnavailableException("容器内没有可执行的 ipatool");
        }
        return path;
    }

    public void cleanup(Prepared prepared) {
        if (prepared == null) {
            return;
        }
        try (var paths = Files.walk(prepared.workDir())) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响任务结果
                }
            });
        } catch (IOException e) {
            log.debug("清理任务工作区失败 {}: {}", prepared.workDir(), e.getMessage());
        }
    }

    private static boolean sessionPresent(Path home, String email) {
        Path session = IpatoolFiles.session(home, email);
        try {
            return Files.isRegularFile(session) && Files.size(session) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean credentialPresent(Path home, String email) {
        Path credential = IpatoolFiles.credential(home, email);
        try {
            return Files.isRegularFile(credential) && Files.size(credential) == 16;
        } catch (IOException e) {
            return false;
        }
    }
}
