package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_user", columnList = "userId"),
        @Index(name = "idx_tasks_status", columnList = "status")
})
public class Task {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 40)
    public String userId;

    @Column(nullable = false, length = 120)
    public String appName;

    @Column(nullable = false, length = 160)
    public String bundleId;

    @Column(nullable = false, length = 32)
    public String version = "latest";

    /** 下单时选择的 App Store 市场，用于匹配同一市场的 Apple ID。 */
    @Column(length = 8)
    public String storefront;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public TaskSource source = TaskSource.STORE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public TaskStatus status = TaskStatus.QUEUED;

    @Column(nullable = false)
    public int progress = 6;

    @Column(nullable = false, length = 24)
    public String stage = "排队中";

    @Column(nullable = false)
    public long priceCents;

    @Column(nullable = false)
    public long refundedCents;

    /** 第几次执行（重试会 +1）。退款幂等键按「任务 + 次数」生成，避免重试后无法再次退款。 */
    @Column(nullable = false)
    public int attempt = 1;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant finishedAt;

    @Column(length = 500)
    public String error;

    public Long sizeBytes;

    @Column(length = 64)
    public String sha256;

    @Column(length = 500)
    public String artifactPath;

    /** 上传任务的输入包路径（仅 UPLOAD，位于 /app/data/uploads/{taskId}/input.ipa）。 */
    @Column(length = 500)
    public String uploadPath;

    /** 产物已按保留期清理：界面显示「已过期」，不再重新生成清单。 */
    @Column(nullable = false)
    public boolean artifactPurged;

    /** true 表示由模拟执行器产生（仅用于演示/测试）。 */
    @Column(nullable = false)
    public boolean simulated;
}
