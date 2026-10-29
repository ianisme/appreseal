package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 用户。字段直接公开（JPA field access），避免大量 getter/setter 样板代码；
 * 对外一律通过 DTO 输出，绝不序列化实体。
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
public class User {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, unique = true, length = 180)
    public String email;

    @Column(nullable = false, length = 100)
    public String passwordHash;

    @Column(nullable = false, length = 40)
    public String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, length = 16)
    public String avatarColor = "#0071e3";

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant lastLoginAt;

    /**
     * 写入 JWT 的版本号。改密、找回密码、封号时 +1，旧 token 立即失效。
     * 可空：旧库升级时新增列没有默认值，读到 null 按 0 处理。
     */
    public Integer tokenVersion;

    /** 找回密码验证码的 HMAC，不明文落库。 */
    @Column(length = 88)
    public String resetCodeHash;

    public Instant resetCodeExpiresAt;

    public Integer resetCodeAttempts;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public int tokenVersionValue() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public void bumpTokenVersion() {
        tokenVersion = tokenVersionValue() + 1;
    }

    public int resetAttempts() {
        return resetCodeAttempts == null ? 0 : resetCodeAttempts;
    }

    public void clearResetCode() {
        resetCodeHash = null;
        resetCodeExpiresAt = null;
        resetCodeAttempts = 0;
    }
}
