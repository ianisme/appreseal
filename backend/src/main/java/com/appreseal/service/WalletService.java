package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.domain.*;
import com.appreseal.repo.TransactionRepository;
import com.appreseal.repo.WalletRepository;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 钱包与流水。全部金额单位：分。
 * 所有余额变动必须走这里，保证「余额 + 流水」同时落库。
 */
@Service
public class WalletService {
    private final WalletRepository wallets;
    private final TransactionRepository txs;

    public WalletService(WalletRepository wallets, TransactionRepository txs) {
        this.wallets = wallets;
        this.txs = txs;
    }

    @Transactional
    public Wallet ensure(String userId) {
        return wallets.findById(userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.userId = userId;
            return wallets.save(w);
        });
    }

    @Transactional(readOnly = true)
    public Dtos.WalletDto view(String userId) {
        Wallet w = wallets.findById(userId).orElseThrow(() -> ApiException.notFound("钱包不存在"));
        return new Dtos.WalletDto(w.balanceCents, w.totalRechargeCents, w.totalSpendCents);
    }

    @Transactional(readOnly = true)
    public long balance(String userId) {
        return wallets.findById(userId).map(w -> w.balanceCents).orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<Dtos.TransactionDto> transactions(String userId) {
        return txs.findByUserIdOrderByCreatedAtDesc(userId).stream().map(Dtos.TransactionDto::from).toList();
    }

    /** 预扣：余额不足直接拒绝。 */
    @Transactional
    public void charge(String userId, long cents, TxType type, String remark, String refId) {
        if (cents <= 0) {
            return;
        }
        Wallet w = lock(userId);
        if (w.balanceCents < cents) {
            throw ApiException.badRequest("余额不足");
        }
        w.balanceCents -= cents;
        w.totalSpendCents += cents;
        wallets.save(w);
        record(userId, type, -cents, w.balanceCents, remark, refId);
    }

    /**
     * 退款：同一个 refId 只退一次（幂等），避免 worker 重试导致重复入账。
     */
    @Transactional
    public boolean refund(String userId, long cents, String remark, String refId) {
        if (cents <= 0) {
            return false;
        }
        if (refId != null && txs.existsByRefIdAndType(refId, TxType.REFUND)) {
            return false;
        }
        Wallet w = lock(userId);
        w.balanceCents += cents;
        wallets.save(w);
        record(userId, TxType.REFUND, cents, w.balanceCents, remark, refId);
        return true;
    }

    /** 充值到账（含赠送）。 */
    @Transactional
    public void recharge(String userId, long totalCents, String remark, String refId) {
        if (totalCents <= 0) {
            return;
        }
        Wallet w = lock(userId);
        w.balanceCents += totalCents;
        w.totalRechargeCents += totalCents;
        wallets.save(w);
        record(userId, TxType.RECHARGE, totalCents, w.balanceCents, remark, refId);
    }

    /** 充值退款（余额冲正）。 */
    @Transactional
    public void reverseRecharge(String userId, long totalCents, String remark, String refId) {
        Wallet w = lock(userId);
        if (w.balanceCents < totalCents) {
            throw ApiException.badRequest("用户余额不足，无法回收本次充值");
        }
        w.balanceCents -= totalCents;
        w.totalRechargeCents -= totalCents;
        wallets.save(w);
        record(userId, TxType.REFUND, -totalCents, w.balanceCents, remark, refId);
    }

    /** 管理员调账（可正可负）。 */
    @Transactional
    public long adjust(String userId, long deltaCents, String remark) {
        Wallet w = lock(userId);
        if (w.balanceCents + deltaCents < 0) {
            throw ApiException.badRequest("调整后余额不能为负");
        }
        w.balanceCents += deltaCents;
        if (deltaCents > 0) {
            w.totalRechargeCents += deltaCents;
        } else {
            w.totalSpendCents += -deltaCents;
        }
        wallets.save(w);
        record(userId, TxType.ADJUST, deltaCents, w.balanceCents, remark, null);
        return w.balanceCents;
    }

    private Wallet lock(String userId) {
        return wallets.lockById(userId).orElseThrow(() -> ApiException.badRequest("钱包不存在"));
    }

    private void record(String userId, TxType type, long amount, long balanceAfter, String remark, String refId) {
        Transaction t = new Transaction();
        t.id = Ids.of("tx");
        t.userId = userId;
        t.type = type;
        t.amountCents = amount;
        t.balanceAfterCents = balanceAfter;
        t.remark = remark;
        t.refId = refId;
        txs.save(t);
    }
}
