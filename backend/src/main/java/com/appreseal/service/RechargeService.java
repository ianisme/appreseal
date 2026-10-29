package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.domain.*;
import com.appreseal.repo.RechargeOrderRepository;
import com.appreseal.repo.RechargePackageRepository;
import com.appreseal.repo.UserRepository;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 充值套餐与订单。
 *
 * <p>当前没有接入真实支付网关：{@code POST /recharges/{id}/pay} 直接把订单标记为
 * 已支付并给钱包入账（含赠送金额）。接入支付宝/微信时，把 {@link #pay} 换成
 * 「下单 + 异步回调确认」即可，其余逻辑不变。
 */
@Service
public class RechargeService {
    private final RechargePackageRepository packages;
    private final RechargeOrderRepository orders;
    private final UserRepository users;
    private final WalletService wallets;

    public RechargeService(RechargePackageRepository packages, RechargeOrderRepository orders,
                           UserRepository users, WalletService wallets) {
        this.packages = packages;
        this.orders = orders;
        this.users = users;
        this.wallets = wallets;
    }

    @Transactional(readOnly = true)
    public List<Dtos.PackageDto> packages() {
        return packages.findAllByOrderBySortOrderAsc().stream().map(Dtos.PackageDto::from).toList();
    }

    @Transactional
    public Dtos.OrderDto create(String userId, Dtos.CreateRechargeRequest req) {
        RechargePackage pkg = packages.findById(req.packageId())
                .orElseThrow(() -> ApiException.badRequest("套餐不存在"));
        RechargeOrder order = new RechargeOrder();
        order.id = Ids.of("ro");
        order.userId = userId;
        order.amountCents = pkg.amountCents;
        order.bonusCents = pkg.bonusCents;
        order.method = req.method();
        order.status = OrderStatus.PENDING;
        orders.save(order);
        return Dtos.OrderDto.from(order);
    }

    @Transactional
    public Dtos.PayResult pay(String orderId, String userId) {
        RechargeOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("订单不存在"));
        if (!order.userId.equals(userId)) {
            throw ApiException.forbidden("无权操作该订单");
        }
        if (order.status == OrderStatus.REFUNDED) {
            throw ApiException.badRequest("订单已退款");
        }
        if (order.status == OrderStatus.PAID) {
            return new Dtos.PayResult(wallets.balance(userId));
        }
        long total = order.amountCents + order.bonusCents;
        String remark = "充值 ¥" + yuan(order.amountCents)
                + (order.bonusCents > 0 ? "（赠送 ¥" + yuan(order.bonusCents) + "）" : "");
        wallets.recharge(order.userId, total, remark, order.id);
        order.status = OrderStatus.PAID;
        order.paidAt = Instant.now();
        orders.save(order);
        return new Dtos.PayResult(wallets.balance(userId));
    }

    @Transactional(readOnly = true)
    public Dtos.OrderDto get(String orderId, String userId, boolean admin) {
        RechargeOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("订单不存在"));
        if (!admin && !order.userId.equals(userId)) {
            throw ApiException.forbidden("无权查看该订单");
        }
        return Dtos.OrderDto.from(order);
    }

    @Transactional(readOnly = true)
    public List<Dtos.AdminOrderDto> allForAdmin() {
        return orders.findAll().stream()
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .map(o -> new Dtos.AdminOrderDto(o.id, o.userId, emailOf(o.userId), o.amountCents, o.bonusCents,
                        o.method, o.status, o.createdAt, o.paidAt))
                .toList();
    }

    @Transactional
    public void refund(String orderId) {
        RechargeOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("订单不存在"));
        if (order.status != OrderStatus.PAID) {
            throw ApiException.badRequest("只有已支付的订单可以退款");
        }
        long total = order.amountCents + order.bonusCents;
        wallets.reverseRecharge(order.userId, total, "充值退款 · " + order.id, order.id);
        order.status = OrderStatus.REFUNDED;
        orders.save(order);
    }

    @Transactional(readOnly = true)
    public RechargeOrder require(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> ApiException.notFound("订单不存在"));
    }

    private String emailOf(String userId) {
        return users.findById(userId).map(u -> u.email).orElse("—");
    }

    private static String yuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
