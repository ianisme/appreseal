package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 审计日志：管理员的所有敏感操作。 */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_created", columnList = "createdAt"))
public class AuditLog {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 60)
    public String actor;

    @Column(nullable = false, length = 40)
    public String action;

    @Column(nullable = false, length = 180)
    public String target;

    @Column(nullable = false, length = 500)
    public String detail;

    @Column(nullable = false, length = 64)
    public String ip = "-";

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
