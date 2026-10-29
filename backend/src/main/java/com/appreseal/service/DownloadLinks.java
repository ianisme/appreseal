package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 短时签名下载链接：不把长效 JWT 暴露在下载 URL 里，也便于交给浏览器原生下载器做断点续传。
 *
 * <p>载荷 {@code 类型|资源 id|用户 id|管理员标记|过期时间} 用 HMAC-SHA256 签名，
 * 密钥由 JWT secret 派生（加固定前缀做域分离）。链接默认 15 分钟过期，可在配置里调整。
 */
@Service
public class DownloadLinks {
    private static final String SEP = "|";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    public enum Kind { TASK, LIBRARY }

    public record Issued(String token, Instant expiresAt) {}

    public record Grant(Kind kind, String resourceId, String userId, boolean admin, Instant expiresAt) {}

    private final SecretKeySpec key;
    private final Duration ttl;

    public DownloadLinks(AppProperties props) {
        byte[] secret = ("download-link:" + props.getJwt().getSecret()).getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.ttl = Duration.ofMinutes(Math.max(1, props.getDownload().getLinkTtlMinutes()));
    }

    public Issued issue(Kind kind, String resourceId, String userId, boolean admin) {
        Instant expiresAt = Instant.now().plus(ttl);
        String payload = String.join(SEP, kind.name(), resourceId, userId, admin ? "1" : "0",
                Long.toString(expiresAt.getEpochSecond()));
        String body = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new Issued(body + "." + B64.encodeToString(sign(body)), expiresAt);
    }

    public Grant verify(String token) {
        if (token == null || token.isBlank() || token.length() > 1024) {
            throw invalid();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            throw invalid();
        }
        String body = token.substring(0, dot);
        byte[] given = decode(token.substring(dot + 1));
        if (given == null || !MessageDigest.isEqual(sign(body), given)) {
            throw invalid();
        }
        byte[] raw = decode(body);
        if (raw == null) {
            throw invalid();
        }
        String[] parts = new String(raw, StandardCharsets.UTF_8).split("\\" + SEP, -1);
        if (parts.length != 5) {
            throw invalid();
        }
        Kind kind;
        try {
            kind = Kind.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        long exp;
        try {
            exp = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        Instant expiresAt = Instant.ofEpochSecond(exp);
        if (Instant.now().isAfter(expiresAt)) {
            throw ApiException.badRequest("下载链接已过期，请重新获取");
        }
        if (parts[1].isBlank() || parts[2].isBlank()) {
            throw invalid();
        }
        return new Grant(kind, parts[1], parts[2], "1".equals(parts[3]), expiresAt);
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("下载签名不可用", e);
        }
    }

    private static byte[] decode(String value) {
        try {
            return B64D.decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("下载链接无效，请重新获取");
    }
}
