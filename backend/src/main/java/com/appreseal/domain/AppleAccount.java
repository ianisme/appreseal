package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Apple ID 池。密码/凭证不落这张表，只记录状态。 */
@Entity
@Table(name = "apple_accounts")
public class AppleAccount {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 180)
    public String email;

    /** App Store 市场，如 cn / us。空表示还没标记，不参与匹配。 */
    @Column(length = 8)
    public String storefront;

    @Column(nullable = false)
    public int priority;

    @Column(nullable = false)
    public boolean credentialOk;

    @Column(nullable = false)
    public boolean sessionOk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public AppleStatus status = AppleStatus.READY;

    @Column(nullable = false)
    public int boundApps;

    public Instant lastUsedAt;
}
