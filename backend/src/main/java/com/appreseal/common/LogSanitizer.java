package com.appreseal.common;

import java.util.regex.Pattern;

/**
 * 普通用户可见日志的兜底脱敏：管理员看原始细节，其他用户只看处理结果。
 *
 * <p>执行器已经尽量把内部细节写成 internal 日志；这里再对残留的绝对路径、
 * Apple ID 邮箱和工具名做一次替换，避免新增日志时漏掉敏感字段。
 */
public final class LogSanitizer {
    private static final String HIDDEN_PATH = "（内部路径）";
    private static final String HIDDEN_ACCOUNT = "已绑定账号";

    private static final Pattern PATH = Pattern.compile(
            "(?<![\\w.-])/(?:app|opt|tmp|var|home|Users|root|srv|mnt|private)(?:/[^\\s,，;；:：)）\\]】'\"]*)?");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern TOOL = Pattern.compile(
            "(?i)(fairplay-tools|fairplay-decrypt|appget\\.sh|appget|accounts\\.py|accounts\\.json"
                    + "|decrypt\\.py|credential\\.bin|ipatool|fairplay|mach-o|sinf|supf)");

    /** 引擎原始错误先换成用户能理解的说法，再做通用替换。 */
    private static final String[][] PHRASES = {
            {"凭证不匹配：credential does not decode this SINF priv record", "凭证与这个包不匹配"},
            {"credential does not decode this SINF priv record", "凭证与这个包不匹配"},
            {"no encrypted arm64 binaries with supported SINF/SUPF", "包内没有可解密的加密段"},
            {"SUPF lacks binary/SINF", "包结构不完整"},
            {"unsupported cryptid/size", "包的加密格式暂不支持"},
    };

    private LogSanitizer() {}

    public static String forUser(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String out = PATH.matcher(message).replaceAll(HIDDEN_PATH);
        out = EMAIL.matcher(out).replaceAll(HIDDEN_ACCOUNT);
        for (String[] phrase : PHRASES) {
            out = out.replace(phrase[0], phrase[1]);
        }
        out = TOOL.matcher(out).replaceAll("服务组件");
        return out;
    }
}
