package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recharge_orders", indexes = @Index(name = "idx_orders_user", columnList = "userId"))
public class RechargeOrder {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 40)
    public String userId;

    @Column(nullable = false)
    public long amountCents;

    @Column(nullable = false)
    public long bonusCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public PayMethod method = PayMethod.ALIPAY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant paidAt;
}
