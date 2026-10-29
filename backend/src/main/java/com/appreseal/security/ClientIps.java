package com.appreseal.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 限流用的客户端地址。
 *
 * <p>不信任 {@code X-Forwarded-For} 的第一跳（调用方可以自己填）。
 * 只有连接来自私网、且打开了 trust-proxy 时，才采用 nginx 写入的 {@code X-Real-IP}。
 * 公网直连后端端口时忽略该头，避免伪造地址绕过 IP 限流。
 */
public final class ClientIps {
    private ClientIps() {}

    public static String of(HttpServletRequest req, boolean trustProxy) {
        String remote = normalize(req.getRemoteAddr());
        if (trustProxy && isPrivate(remote)) {
            String real = normalize(req.getHeader("X-Real-IP"));
            if (real != null) {
                return real;
            }
        }
        return remote == null ? "unknown" : remote;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 64 || value.indexOf(',') >= 0 || value.indexOf(' ') >= 0) {
            return null;
        }
        if (!value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return "127.0.0.1";
        }
        return value;
    }

    static boolean isPrivate(String ip) {
        if (ip == null) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        String lower = ip.toLowerCase();
        return lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80:");
    }
}
