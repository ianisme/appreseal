package com.appreseal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 全局设置：单行表（id 固定为 1）。 */
@Entity
@Table(name = "app_settings")
public class AppSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id = SINGLETON_ID;

    @Column(nullable = false)
    public long decryptPriceCents = 600;

    @Column(nullable = false)
    public long downloadPriceCents = 300;

    @Column(nullable = false)
    public int concurrency = 2;

    @Column(nullable = false)
    public boolean maintenance;

    @Column(nullable = false, length = 500)
    public String announcement = "";

    @Column(nullable = false)
    public int freeDownloadDays = 30;

    /** 脱壳成功后首下免费的小时数。 */
    @Column(nullable = false)
    public int firstDownloadFreeHours = 24;

    /** 脱壳产物保留天数，超过后自动删除文件。 */
    @Column(nullable = false)
    public int artifactRetentionDays = 30;

    /** 失败/取消任务的上传包保留天数，便于用户重试。 */
    @Column(nullable = false)
    public int uploadRetentionDays = 14;

    /** 任务日志保留天数（任务记录本身保留）。 */
    @Column(nullable = false)
    public int logRetentionDays = 90;

    /** 是否允许后台按小时自动清理。 */
    @Column(nullable = false)
    public boolean autoCleanup = true;

    /** 数据卷使用率告警阈值（百分比）。 */
    @Column(nullable = false)
    public int diskWarnPercent = 80;
}
