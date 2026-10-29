package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 市场上架的应用（已完成脱壳的成品）。 */
@Entity
@Table(name = "apps", indexes = @Index(name = "idx_apps_bundle", columnList = "bundleId", unique = true))
public class AppItem {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, unique = true, length = 160)
    public String bundleId;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(nullable = false, length = 120)
    public String developer;

    @Column(nullable = false, length = 32)
    public String category = "工具";

    @Column(nullable = false, length = 8)
    public String iconText = "A";

    @Column(nullable = false)
    public int iconHue = 215;

    @Column(nullable = false, length = 32)
    public String version = "1.0";

    @Column(nullable = false)
    public long sizeBytes;

    @Column(nullable = false, length = 16)
    public String minIOS = "13.0";

    @Column(length = 120)
    public String executable;

    @Column(length = 64)
    public String sha256;

    @Column(nullable = false)
    public long priceDownloadCents = 300;

    @Column(nullable = false)
    public long priceDecryptCents = 600;

    @Column(nullable = false)
    public long downloads;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public AppStatus status = AppStatus.ON_SALE;

    @Column(nullable = false)
    public boolean verified = true;

    /** 逗号分隔，如 "arm64,arm64e"。 */
    @Column(nullable = false, length = 64)
    public String arch = "arm64";

    @Column(length = 2000)
    public String description = "";

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();
}
