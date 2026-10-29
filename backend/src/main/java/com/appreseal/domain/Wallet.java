package com.appreseal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 钱包：与用户一对一，主键即 userId。金额一律为「分」。 */
@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    @Column(length = 40)
    public String userId;

    @Column(nullable = false)
    public long balanceCents;

    @Column(nullable = false)
    public long totalRechargeCents;

    @Column(nullable = false)
    public long totalSpendCents;
}
