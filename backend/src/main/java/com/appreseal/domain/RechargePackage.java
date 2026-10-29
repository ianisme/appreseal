package com.appreseal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recharge_packages")
public class RechargePackage {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false)
    public long amountCents;

    @Column(nullable = false)
    public long bonusCents;

    @Column(nullable = false)
    public boolean recommended;

    @Column(nullable = false)
    public int sortOrder;
}
