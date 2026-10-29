package com.appreseal.executor;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.appreseal.domain.AppleAccount;
import com.appreseal.domain.Task;
import com.appreseal.domain.TaskSource;
import com.appreseal.domain.TaskStatus;
import com.appreseal.service.IpaInspector;
import com.appreseal.service.TaskService;
import com.appreseal.service.UploadFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实执行器：
 * <ul>
 *   <li>STORE：在容器内调用 appget.sh 下载并解密，按市场筛选 Apple ID。</li>
 *   <li>UPLOAD：用户上传的加密 IPA 直接调用 fairplay-decrypt/decrypt.py，
 *       逐个尝试池内 credential.bin，直到有一份能解开 SINF。</li>
 * </ul>
 *
 * <p>脚本、解密引擎、Linux ipatool 都在镜像里；运行期只读数据卷
 * {@code /app/data/ipatool} 中的会话、凭证和口令，不访问宿主机。
 * Apple ID 切换必须全局串行，因此只有 STORE 任务持有 {@link IpatoolLock}。
 */
@Component
@ConditionalOnProperty(name = "appreseal.executor.mode", havingValue = "shell")
public class ShellDecryptJob implements DecryptJob {
    private static final Logger log = LoggerFactory.getLogger(ShellDecryptJob.class);
    private static final Pattern ARTIFACT = Pattern.compile("解密完成[:：]\\s*(.+?)(?:\\s*\\(|$)");
    private static final Pattern VERSION = Pattern.compile("App:\\s*\\S+\\s+(\\S+)");
    private static final Pattern CRYPTID = Pattern.compile("cryptid=(\\d+)");
    private static final Pattern PROGRESS = Pattern.compile("^(\\d+)/(\\d+) pages=");
    private static final Pattern SIZE_IN_PARENS = Pattern.compile("\\(([^()]*)\\)\\s*$");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");
    private static final String CREDENTIAL_MISMATCH = "credential does not decode this SINF priv record";

    private final TaskService tasks;
    private final AppProperties props;
    private final AppgetWorkspace workspace;
    private final IpatoolLock lock;
    private final JobControl jobs;

    public ShellDecryptJob(TaskService tasks, AppProperties props, AppgetWorkspace workspace, IpatoolLock lock,
                           JobControl jobs) {
        this.tasks = tasks;
        this.props = props;
        this.workspace = workspace;
        this.lock = lock;
        this.jobs = jobs;
    }

    @Override
    public void run(String taskId) {
        Task task = tasks.requireTask(taskId);
        if (task.source == TaskSource.UPLOAD) {
            runUpload(taskId, task);
        } else {
            runStore(taskId, task);
        }
    }

    /* ---------------------------------- STORE ---------------------------------- */

    private void runStore(String taskId, Task task) {
        Path script = Path.of(props.getExecutor().getAppgetPath()).toAbsolutePath().normalize();
        if (!Files.isReadable(script)) {
            tasks.logInternal(taskId, "ERROR", "执行器脚本不存在：" + script);
            tasks.fail(taskId, "执行器未就绪，请稍后重试");
            return;
        }

        if (!tasks.isActive(taskId)) {
            return;
        }
        AppgetWorkspace.Prepared prepared = null;
        if (props.getExecutor().isShellSerialized()) {
            lock.lock();
        }
        try {
            if (!tasks.isActive(taskId)) {
                return;
            }
            tasks.markLive(taskId);
            prepared = workspace.prepare(task);
            tasks.transition(taskId, TaskStatus.DOWNLOADING, 18, "INFO",
                    "正在从 App Store 下载（市场：" + marketName(task.storefront) + "）");
            tasks.logInternal(taskId, "INFO", "容器内执行 appget.sh，市场 " + marketName(task.storefront)
                    + "，候选账号 " + prepared.candidates().size() + " 个，目标 " + task.bundleId);

            List<String> cmd = List.of("/bin/bash", script.toString(), "get", task.bundleId, "--force");
            ProcessBuilder builder = new ProcessBuilder(cmd).redirectErrorStream(true);
            builder.environment().putAll(workspace.environment(prepared));
            Process process = builder.start();
            jobs.watch(taskId, process);
            try {
            AtomicReference<String> artifact = new AtomicReference<>();
            AtomicReference<String> version = new AtomicReference<>();
            AtomicReference<String> cryptid = new AtomicReference<>();
            Thread reader = new Thread(() -> pump(taskId, process, artifact, version, cryptid), "appget-" + taskId);
            reader.setDaemon(true);
            reader.start();

            int timeout = timeoutMinutes();
            boolean finished = process.waitFor(timeout, TimeUnit.MINUTES);
            if (!tasks.isActive(taskId)) {
                return;
            }
            if (!finished) {
                process.destroyForcibly();
                tasks.logInternal(taskId, "ERROR", "执行超时（" + timeout + " 分钟）");
                tasks.fail(taskId, "处理超时，请稍后重试");
                return;
            }
            reader.join(5000);
            if (!tasks.isActive(taskId)) {
                return;
            }
            int code = process.exitValue();
            if (code != 0) {
                tasks.logInternal(taskId, "ERROR", "appget 退出码 " + code + "，所有候选账号均未处理成功");
                tasks.fail(taskId, "暂时无法从 App Store 获取这个应用，请稍后重试");
                return;
            }

            Path out = artifact.get() == null ? null : Path.of(artifact.get());
            if (out == null || !Files.isRegularFile(out) || Files.size(out) == 0) {
                tasks.fail(taskId, "没有获取到可下载的产物，请稍后重试");
                return;
            }
            String verified = cryptid.get();
            if (!"0".equals(verified) && !"none".equals(verified)) {
                tasks.logInternal(taskId, "ERROR", "解密产物未通过 cryptid 校验（"
                        + (verified == null ? "未返回校验结果" : "cryptid=" + verified) + "）");
                tasks.fail(taskId, "产物校验未通过，请稍后重试");
                return;
            }

            long size = Files.size(out);
            String sha = sha256(out);
            tasks.log(taskId, "OK", "校验通过：" + ("none".equals(verified) ? "主程序无加密段" : "cryptid=0"));
            tasks.succeed(taskId, version.get(), size, sha, out.toString());
            } finally {
                jobs.unwatch(taskId, process);
            }
        } catch (AppgetWorkspace.UnavailableException e) {
            tasks.logInternal(taskId, "ERROR", e.getMessage());
            tasks.fail(taskId, "服务暂时不可用，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tasks.fail(taskId, "执行器被中断");
        } catch (Exception e) {
            log.error("store 执行失败 task={}", taskId, e);
            tasks.logInternal(taskId, "ERROR", "执行器异常：" + e.getMessage());
            tasks.fail(taskId, "处理失败，请稍后重试");
        } finally {
            workspace.cleanup(prepared);
            if (props.getExecutor().isShellSerialized()) {
                lock.unlock();
            }
        }
    }

    /* ---------------------------------- UPLOAD --------------------------------- */

    private void runUpload(String taskId, Task task) {
        Path input;
        try {
            input = UploadFiles.requireInput(props, task);
        } catch (ApiException e) {
            tasks.fail(taskId, e.getMessage());
            return;
        }

        Path python = Path.of(props.getExecutor().getDecryptPython()).toAbsolutePath().normalize();
        Path script = Path.of(props.getExecutor().getDecryptScript()).toAbsolutePath().normalize();
        if (!Files.isExecutable(python)) {
            tasks.logInternal(taskId, "ERROR", "解密解释器不存在：" + python);
            tasks.fail(taskId, "解密组件未就绪，请稍后重试");
            return;
        }
        if (!Files.isReadable(script)) {
            tasks.logInternal(taskId, "ERROR", "解密脚本不存在：" + script);
            tasks.fail(taskId, "解密组件未就绪，请稍后重试");
            return;
        }

        if (!tasks.isActive(taskId)) {
            return;
        }
        AppgetWorkspace.Prepared prepared = null;
        try {
            if (!tasks.isActive(taskId)) {
                return;
            }
            tasks.markLive(taskId);
            prepared = workspace.prepareUpload(task);
            List<AppleAccount> candidates = prepared.candidates();
            tasks.transition(taskId, TaskStatus.DECRYPTING, 55, "INFO", "开始解密上传包");
            tasks.logInternal(taskId, "INFO", "可用凭证 " + candidates.size() + " 份");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinutes());
            String lastError = null;

            for (int i = 0; i < candidates.size(); i++) {
                if (!tasks.isActive(taskId)) {
                    return;
                }
                int ordinal = i + 1;
                AppleAccount account = candidates.get(i);
                Path credential = workspace.credential(account);
                Path out = prepared.outputDir().resolve("decrypted-" + ordinal + ".ipa");
                tasks.logInternal(taskId, "INFO", "尝试凭证 " + ordinal + "/" + candidates.size()
                        + "：" + account.email + " · " + credential);
                Attempt attempt = decrypt(python, script, input, out, credential, taskId, deadline);
                if (!tasks.isActive(taskId)) {
                    return;
                }

                if (attempt.timeout()) {
                    tasks.logInternal(taskId, "ERROR", "解密超时（" + timeoutMinutes() + " 分钟）");
                    tasks.fail(taskId, "解密超时，请稍后重试");
                    return;
                }
                if (attempt.code() == 0 && Files.isRegularFile(out) && Files.size(out) > 0) {
                    try {
                        IpaInspector.Info info = IpaInspector.inspect(out, false);
                        long size = Files.size(out);
                        String sha = sha256(out);
                        tasks.log(taskId, "OK", "解密完成，产物已通过 cryptid=0 校验");
                        tasks.logInternal(taskId, "OK", "凭证 " + ordinal + " 匹配（" + account.email + "）");
                        tasks.succeed(taskId, info.version(), size, sha, out.toString());
                        UploadFiles.deleteTaskDir(input);
                        return;
                    } catch (IpaInspector.IpaException e) {
                        lastError = e.getMessage();
                        tasks.logInternal(taskId, "ERROR", "产物校验失败：" + e.getMessage());
                        safeDelete(out);
                        continue;
                    }
                }

                lastError = attempt.message();
                if (attempt.credentialMismatch()) {
                    tasks.logInternal(taskId, "WARN", "凭证 " + ordinal + " 不匹配（" + account.email + "），继续尝试");
                    continue;
                }
                // 格式/引擎错误：换凭证也不会成功，直接失败并退款。
                tasks.logInternal(taskId, "ERROR", "解密失败：" + attempt.message());
                tasks.fail(taskId, "解密失败，请确认 IPA 未损坏后重试");
                return;
            }

            tasks.logInternal(taskId, "ERROR", "没有凭证能解开这个 IPA"
                    + (lastError == null || lastError.isBlank() ? "" : "（最后一次：" + lastError + "）"));
            tasks.fail(taskId, "没有可用的凭证能解开这个上传包。请确认这个 IPA 来自本服务已绑定的 Apple ID");
        } catch (AppgetWorkspace.UnavailableException e) {
            tasks.logInternal(taskId, "ERROR", e.getMessage());
            tasks.fail(taskId, "服务暂时不可用，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tasks.fail(taskId, "执行器被中断");
        } catch (Exception e) {
            log.error("upload 执行失败 task={}", taskId, e);
            tasks.logInternal(taskId, "ERROR", "执行器异常：" + e.getMessage());
            tasks.fail(taskId, "处理失败，请稍后重试");
        } finally {
            workspace.cleanup(prepared);
        }
    }

    private record Attempt(int code, String message, boolean credentialMismatch, boolean timeout) {}

    private Attempt decrypt(Path python, Path script, Path input, Path out, Path credential,
                            String taskId, long deadline) throws Exception {
        List<String> cmd = List.of(python.toString(), script.toString(), input.toString(),
                out.toString(), "--credential", credential.toString());
        ProcessBuilder builder = new ProcessBuilder(cmd).redirectErrorStream(true);
        builder.environment().put("LC_ALL", "C.UTF-8");
        builder.environment().put("LANG", "C.UTF-8");
        Process process = builder.start();
        jobs.watch(taskId, process);

        AtomicReference<String> last = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = stdout.readLine()) != null) {
                    String line = ANSI.matcher(raw).replaceAll("").trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (line.length() > 480) {
                        line = line.substring(0, 480);
                    }
                    last.set(line);
                    Matcher progress = PROGRESS.matcher(line);
                    if (progress.find()) {
                        tasks.log(taskId, "INFO", "解密进度 " + progress.group(1) + "/" + progress.group(2));
                        tasks.logInternal(taskId, "INFO", line);
                    } else if (line.contains(CREDENTIAL_MISMATCH)) {
                        // 凭证不匹配由调用方统一记录，这里不重复刷错误栈
                    } else if (line.startsWith("Traceback") || line.startsWith("  File ")
                            || line.startsWith("    ") || line.startsWith("    ^")) {
                        // 跳过 Python 调用栈，只保留真正的错误信息
                    } else if (line.startsWith("ValueError") || line.contains("Error")) {
                        tasks.logInternal(taskId, "ERROR", line);
                    } else if (line.startsWith("decrypted-binaries") || line.contains("pages=")) {
                        tasks.logInternal(taskId, "INFO", line);
                    }
                }
            } catch (Exception e) {
                log.debug("读取 decrypt 输出结束 task={} err={}", taskId, e.getMessage());
            }
        }, "decrypt-" + taskId);
        reader.setDaemon(true);
        reader.start();

        try {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            process.destroyForcibly();
            return new Attempt(-1, "解密超时", false, true);
        }
        boolean finished = process.waitFor(remaining, TimeUnit.NANOSECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return new Attempt(-1, "解密超时", false, true);
        }
        reader.join(3000);
        if (!process.isAlive() && !tasks.isActive(taskId)) {
            return new Attempt(-1, "已取消", false, false);
        }
        int code = process.exitValue();
        String message = last.get() == null ? "未返回错误信息" : last.get();
        if (message.startsWith("ValueError: ")) {
            message = message.substring("ValueError: ".length());
        }
        if (message.length() > 240) {
            message = message.substring(0, 240);
        }
        boolean mismatch = message.contains(CREDENTIAL_MISMATCH);
        return new Attempt(code, message, mismatch, false);
        } finally {
            jobs.unwatch(taskId, process);
        }
    }

    private int timeoutMinutes() {
        return Math.max(5, props.getExecutor().getTimeoutMinutes());
    }

    private static void safeDelete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 清理失败不影响任务
        }
    }

    /* --------------------------------- helpers --------------------------------- */

    private static String marketName(String storefront) {
        return storefront == null || storefront.isBlank()
                ? "未指定" : com.appreseal.service.Storefronts.name(storefront);
    }

    private void pump(String taskId, Process process, AtomicReference<String> artifact,
                      AtomicReference<String> version, AtomicReference<String> cryptid) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = ANSI.matcher(raw).replaceAll("").trim();
                if (line.isEmpty()) continue;
                if (line.length() > 480) line = line.substring(0, 480);

                Matcher ver = VERSION.matcher(line);
                if (ver.find()) {
                    version.set(ver.group(1));
                }

                Matcher cid = CRYPTID.matcher(line);
                if (cid.find()) {
                    cryptid.set(cid.group(1));
                } else if (line.contains("无加密段")) {
                    cryptid.set("none");
                }

                // 原始输出只给管理员看：里面包含绝对路径、Apple ID、脚本名。
                boolean errorLine = line.startsWith("✘") || line.startsWith("x ")
                        || line.contains("所有账号都没能处理") || line.contains("校验异常");
                tasks.logInternal(taskId, errorLine ? "ERROR" : "INFO", line);

                if (line.contains("下载中")) {
                    tasks.transition(taskId, TaskStatus.DOWNLOADING, 38, "INFO", "正在从 App Store 下载安装包");
                } else if (line.contains("解密中")) {
                    tasks.transition(taskId, TaskStatus.DECRYPTING, 65, "INFO", "正在解密主程序");
                } else if (line.contains("解密完成")) {
                    Matcher m = ARTIFACT.matcher(line);
                    if (m.find()) artifact.set(m.group(1).trim());
                    Matcher size = SIZE_IN_PARENS.matcher(line);
                    String suffix = size.find() ? "（" + size.group(1) + "）" : "";
                    tasks.transition(taskId, TaskStatus.VERIFYING, 92, "OK",
                            "解密完成" + suffix + "，正在校验产物");
                }
            }
        } catch (Exception e) {
            log.debug("读取 appget 输出结束 task={} err={}", taskId, e.getMessage());
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
