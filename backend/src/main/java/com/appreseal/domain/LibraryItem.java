package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 用户可下载的成品（脱壳产物或市场购买）。 */
@Entity
@Table(name = "library_items", indexes = @Index(name = "idx_lib_user", columnList = "userId"))
public class LibraryItem {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 40)
    public String userId;

    @Column(length = 40)
    public String appId;

    @Column(nullable = false, length = 160)
    public String bundleId;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(nullable = false, length = 32)
    public String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public LibrarySource source = LibrarySource.PURCHASE;

    @Column(nullable = false)
    public Instant acquiredAt = Instant.now();

    @Column(nullable = false)
    public Instant freeUntil = Instant.now();

    @Column(length = 40)
    public String taskId;

    @Column(length = 500)
    public String artifactPath;
}
