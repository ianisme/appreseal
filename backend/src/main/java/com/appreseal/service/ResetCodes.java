package com.appreseal.service;

import com.appreseal.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 6 位找回密码验证码。库里只存 HMAC，密钥由 JWT secret 派生。 */
@Component
public class ResetCodes {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ResetCodes(AppProperties props) {
        byte[] secret = ("reset-code:" + props.getJwt().getSecret()).getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hash(String userId, String code) {
        return B64.encodeToString(sign(userId + ":" + code));
    }

    /** 缺失的验证码也走一次 HMAC，减少「有没有码」的时间差。 */
    public boolean matches(String userId, String code, String expectedHash) {
        String actual = hash(userId == null ? "" : userId, code == null ? "" : code);
        String expected = expectedHash == null ? hash("none", "000000") : expectedHash;
        return expectedHash != null
                && MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("验证码签名不可用", e);
        }
    }
}
