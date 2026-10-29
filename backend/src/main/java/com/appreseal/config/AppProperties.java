package com.appreseal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "appreseal")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Seed seed = new Seed();
    private final Executor executor = new Executor();
    private final Mock mock = new Mock();
    private final Ipatool ipatool = new Ipatool();
    private final Itunes itunes = new Itunes();
    private final Download download = new Download();
    private final Storage storage = new Storage();
    private final Auth auth = new Auth();
    private final Mail mail = new Mail();

    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }
    public Seed getSeed() { return seed; }
    public Executor getExecutor() { return executor; }
    public Mock getMock() { return mock; }
    public Ipatool getIpatool() { return ipatool; }
    public Itunes getItunes() { return itunes; }
    public Download getDownload() { return download; }
    public Storage getStorage() { return storage; }
    public Auth getAuth() { return auth; }
    public Mail getMail() { return mail; }

    public static class Jwt {
        private String secret;
        private long ttlHours = 168;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getTtlHours() { return ttlHours; }
        public void setTtlHours(long ttlHours) { this.ttlHours = ttlHours; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Seed {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Executor {
        /** false 时关闭调度线程池（测试/只读部署）。 */
        private boolean enabled = true;
        /** mock | shell */
        private String mode = "mock";
        private String appgetPath = "/opt/fairplay/fairplay-tools/appget.sh";
        private String outputDir = "./data/artifacts";
        private String workDir = "./data/work";
        private String uploadDir = "./data/uploads";
        private String decryptPython = "/opt/fairplay/fairplay-decrypt/.venv/bin/python";
        private String decryptScript = "/opt/fairplay/fairplay-decrypt/decrypt.py";
        private long maxUploadBytes = 2L * 1024 * 1024 * 1024;
        private String proxy = "";
        private boolean shellSerialized = true;
        private int timeoutMinutes = 45;
        private long pollIntervalMs = 1000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getAppgetPath() { return appgetPath; }
        public void setAppgetPath(String appgetPath) { this.appgetPath = appgetPath; }
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
        public String getWorkDir() { return workDir; }
        public void setWorkDir(String workDir) { this.workDir = workDir; }
        public String getUploadDir() { return uploadDir; }
        public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
        public String getDecryptPython() { return decryptPython; }
        public void setDecryptPython(String decryptPython) { this.decryptPython = decryptPython; }
        public String getDecryptScript() { return decryptScript; }
        public void setDecryptScript(String decryptScript) { this.decryptScript = decryptScript; }
        public long getMaxUploadBytes() { return maxUploadBytes; }
        public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
        public String getProxy() { return proxy; }
        public void setProxy(String proxy) { this.proxy = proxy; }
        public boolean isShellSerialized() { return shellSerialized; }
        public void setShellSerialized(boolean shellSerialized) { this.shellSerialized = shellSerialized; }
        public int getTimeoutMinutes() { return timeoutMinutes; }
        public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    /** Linux 容器里的 ipatool 文件密钥。密码不写这里。 */
    public static class Ipatool {
        private String path = "";
        private String home = "./data/ipatool";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getHome() { return home; }
        public void setHome(String home) { this.home = home; }
    }

    /** 苹果官方 iTunes Search API。proxy 例：http://host.docker.internal:7897 */
    public static class Itunes {
        private boolean enabled = true;
        private String proxy = "";
        private int timeoutSeconds = 8;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProxy() { return proxy; }
        public void setProxy(String proxy) { this.proxy = proxy; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /** 下载：短时签名链接 + Range 断点续传。 */
    public static class Download {
        private long linkTtlMinutes = 15;

        public long getLinkTtlMinutes() { return linkTtlMinutes; }
        public void setLinkTtlMinutes(long linkTtlMinutes) { this.linkTtlMinutes = linkTtlMinutes; }
    }

    /** 存储治理：定时清理产物/上传包/日志，并监控数据卷容量。 */
    public static class Storage {
        /** 测试环境可关闭，手动清理接口仍然可用。 */
        private boolean enabled = true;
        /** 数据卷根目录；留空时取产物目录的上级。 */
        private String dataDir = "";
        private long cleanupIntervalMs = 3_600_000L;
        private long cleanupInitialDelayMs = 300_000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDataDir() { return dataDir; }
        public void setDataDir(String dataDir) { this.dataDir = dataDir; }
        public long getCleanupIntervalMs() { return cleanupIntervalMs; }
        public void setCleanupIntervalMs(long cleanupIntervalMs) { this.cleanupIntervalMs = cleanupIntervalMs; }
        public long getCleanupInitialDelayMs() { return cleanupInitialDelayMs; }
        public void setCleanupInitialDelayMs(long cleanupInitialDelayMs) {
            this.cleanupInitialDelayMs = cleanupInitialDelayMs;
        }
    }

    /**
     * 登录 / 注册 / 找回密码的限流。计数在进程内，多副本不会共享。
     * 邮箱维度是主要防线；IP 维度在 Docker 端口映射后可能被收成同一个网关地址。
     */
    public static class Auth {
        /** 为 true 时，只接受来自私网地址的 X-Real-IP（nginx 设置）。公网直连不信任该头。 */
        private boolean trustProxy;
        private int loginEmailMaxFailures = 8;
        private int loginIpMaxFailures = 40;
        private long loginWindowSeconds = 900;
        private long loginLockSeconds = 900;
        private int registerMaxPerIp = 8;
        private long registerWindowSeconds = 3600;
        private int resetMaxPerEmail = 5;
        private int resetMaxPerIp = 20;
        private long resetWindowSeconds = 3600;
        private long resetCodeTtlSeconds = 600;
        private int resetMaxAttempts = 5;

        public boolean isTrustProxy() { return trustProxy; }
        public void setTrustProxy(boolean trustProxy) { this.trustProxy = trustProxy; }
        public int getLoginEmailMaxFailures() { return loginEmailMaxFailures; }
        public void setLoginEmailMaxFailures(int loginEmailMaxFailures) { this.loginEmailMaxFailures = loginEmailMaxFailures; }
        public int getLoginIpMaxFailures() { return loginIpMaxFailures; }
        public void setLoginIpMaxFailures(int loginIpMaxFailures) { this.loginIpMaxFailures = loginIpMaxFailures; }
        public long getLoginWindowSeconds() { return loginWindowSeconds; }
        public void setLoginWindowSeconds(long loginWindowSeconds) { this.loginWindowSeconds = loginWindowSeconds; }
        public long getLoginLockSeconds() { return loginLockSeconds; }
        public void setLoginLockSeconds(long loginLockSeconds) { this.loginLockSeconds = loginLockSeconds; }
        public int getRegisterMaxPerIp() { return registerMaxPerIp; }
        public void setRegisterMaxPerIp(int registerMaxPerIp) { this.registerMaxPerIp = registerMaxPerIp; }
        public long getRegisterWindowSeconds() { return registerWindowSeconds; }
        public void setRegisterWindowSeconds(long registerWindowSeconds) { this.registerWindowSeconds = registerWindowSeconds; }
        public int getResetMaxPerEmail() { return resetMaxPerEmail; }
        public void setResetMaxPerEmail(int resetMaxPerEmail) { this.resetMaxPerEmail = resetMaxPerEmail; }
        public int getResetMaxPerIp() { return resetMaxPerIp; }
        public void setResetMaxPerIp(int resetMaxPerIp) { this.resetMaxPerIp = resetMaxPerIp; }
        public long getResetWindowSeconds() { return resetWindowSeconds; }
        public void setResetWindowSeconds(long resetWindowSeconds) { this.resetWindowSeconds = resetWindowSeconds; }
        public long getResetCodeTtlSeconds() { return resetCodeTtlSeconds; }
        public void setResetCodeTtlSeconds(long resetCodeTtlSeconds) { this.resetCodeTtlSeconds = resetCodeTtlSeconds; }
        public int getResetMaxAttempts() { return resetMaxAttempts; }
        public void setResetMaxAttempts(int resetMaxAttempts) { this.resetMaxAttempts = resetMaxAttempts; }
    }

    /** 找回密码邮件。未配置 SMTP 时接口明确返回暂不可用，而不是假装已发送。 */
    public static class Mail {
        private boolean enabled;
        /** 测试用：把验证码留在内存里，不发信、也不出现在 HTTP 响应中。 */
        private boolean capture;
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "";
        private boolean starttls = true;
        private boolean ssl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isCapture() { return capture; }
        public void setCapture(boolean capture) { this.capture = capture; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public boolean isStarttls() { return starttls; }
        public void setStarttls(boolean starttls) { this.starttls = starttls; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
    }

    public static class Mock {
        private long downloadMs = 2500;
        private long decryptMs = 4000;
        private long verifyMs = 2000;

        public long getDownloadMs() { return downloadMs; }
        public void setDownloadMs(long downloadMs) { this.downloadMs = downloadMs; }
        public long getDecryptMs() { return decryptMs; }
        public void setDecryptMs(long decryptMs) { this.decryptMs = decryptMs; }
        public long getVerifyMs() { return verifyMs; }
        public void setVerifyMs(long verifyMs) { this.verifyMs = verifyMs; }
    }
}
