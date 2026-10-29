package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 资金流水：任何余额变动都必须留下一条，amountCents 正数入账、负数出账。 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_user", columnList = "userId"),
        @Index(name = "idx_tx_ref", columnList = "refId")
})
public class Transaction {
    @Id
    @Column(length = 40)
    public String id;

    @Column(nullable = false, length = 40)
    public String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public TxType type;

    @Column(nullable = false)
    public long amountCents;

    @Column(nullable = false)
    public long balanceAfterCents;

    @Column(nullable = false, length = 200)
    public String remark;

    @Column(length = 40)
    public String refId;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
