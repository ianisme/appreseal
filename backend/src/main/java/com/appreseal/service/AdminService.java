package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.common.Pages;
import com.appreseal.domain.*;
import com.appreseal.repo.*;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** 管理后台的聚合查询与敏感操作（全部写审计日志）。 */
@Service
public class AdminService {
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final UserRepository users;
    private final WalletRepository wallets;
    private final TaskRepository tasks;
    private final TransactionRepository txs;
    private final RechargePackageRepository packages;
    private final AppleAccountRepository appleAccounts;
    private final WalletService walletService;
    private final SettingsService settingsService;
    private final CatalogService catalog;
    private final TaskService taskService;
    private final RechargeService rechargeService;
    private final AuditService audit;
    private final AppleKeychainService keychain;

    public AdminService(UserRepository users, WalletRepository wallets, TaskRepository tasks,
                        TransactionRepository txs, RechargePackageRepository packages,
                        AppleAccountRepository appleAccounts, WalletService walletService,
                        SettingsService settingsService, CatalogService catalog, TaskService taskService,
                        RechargeService rechargeService, AuditService audit, AppleKeychainService keychain) {
        this.users = users;
        this.wallets = wallets;
        this.tasks = tasks;
        this.txs = txs;
        this.packages = packages;
        this.appleAccounts = appleAccounts;
        this.walletService = walletService;
        this.settingsService = settingsService;
        this.catalog = catalog;
        this.taskService = taskService;
        this.rechargeService = rechargeService;
        this.audit = audit;
        this.keychain = keychain;
    }

    /* --------------------------------- 概览 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.OverviewDto overview() {
        LocalDate today = LocalDate.now(ZONE);
        Instant dayStart = today.atStartOfDay(ZONE).toInstant();
        AppSettings s = settingsService.get();

        long tasksToday = tasks.countByCreatedAtAfter(dayStart);

        List<Dtos.SeriesPoint> series = new ArrayList<>(14);
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Instant from = day.atStartOfDay(ZONE).toInstant();
            Instant to = day.plusDays(1).atStartOfDay(ZONE).toInstant();
            series.add(new Dtos.SeriesPoint(day.getMonthValue() + "/" + day.getDayOfMonth(),
                    tasksBetween(from, to)));
        }

        long succeeded = tasks.countByStatus(TaskStatus.SUCCEEDED);
        long failed = tasks.countByStatus(TaskStatus.FAILED);
        long finished = succeeded + failed;
        double successRate = finished == 0 ? 100.0 : Math.round(succeeded * 1000.0 / finished) / 10.0;

        long running = tasks.countByStatusIn(List.of(TaskStatus.DOWNLOADING, TaskStatus.DECRYPTING,
                TaskStatus.VERIFYING));
        long queued = tasks.countByStatus(TaskStatus.QUEUED);
        long canceled = tasks.countByStatus(TaskStatus.CANCELED);
        long activeUsers = users.countByLastLoginAtAfter(Instant.now().minusSeconds(7 * 24 * 3600));

        List<Dtos.DistributionItem> distribution = List.of(
                new Dtos.DistributionItem("成功", succeeded, "green"),
                new Dtos.DistributionItem("失败", failed, "red"),
                new Dtos.DistributionItem("进行中", running + queued, "blue"),
                new Dtos.DistributionItem("已取消", canceled, "gray"));

        return new Dtos.OverviewDto(0, tasksToday, successRate, activeUsers, series, distribution,
                new Dtos.QueueInfo((int) running, (int) queued, s.concurrency));
    }

    private long tasksBetween(Instant from, Instant to) {
        return tasks.findByCreatedAtAfterOrderByCreatedAtDesc(from).stream()
                .filter(t -> t.createdAt != null && t.createdAt.isBefore(to))
                .count();
    }

    /* --------------------------------- 用户 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.UserPageDto users(String q, String role, String status, Integer page, Integer size) {
        int p = Pages.page(page);
        int s = Pages.size(size);
        String key = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<User> all = users.findAll();
        long active = all.stream().filter(u -> u.status == UserStatus.ACTIVE).count();
        long banned = all.stream().filter(u -> u.status == UserStatus.BANNED).count();
        long balance = wallets.findAll().stream().mapToLong(w -> w.balanceCents).sum();
        List<User> filtered = all.stream()
                .filter(u -> key.isEmpty()
                        || u.email.toLowerCase(java.util.Locale.ROOT).contains(key)
                        || (u.nickname != null && u.nickname.toLowerCase(java.util.Locale.ROOT).contains(key)))
                .filter(u -> role == null || role.isBlank() || "ALL".equals(role) || u.role.name().equals(role))
                .filter(u -> status == null || status.isBlank() || "ALL".equals(status) || u.status.name().equals(status))
                .sorted((a, b) -> a.createdAt.compareTo(b.createdAt))
                .toList();
        List<Dtos.AdminUserDto> items = Pages.slice(filtered, p, s).stream()
                .map(u -> new Dtos.AdminUserDto(u.id, u.email, u.nickname, u.role, u.status, u.avatarColor,
                        u.createdAt, u.lastLoginAt,
                        wallets.findById(u.id).map(w -> w.balanceCents).orElse(0L),
                        tasks.countByUserId(u.id)))
                .toList();
        return new Dtos.UserPageDto(items, p, s, filtered.size(), Pages.pageCount(filtered.size(), s),
                all.size(), active, banned, balance);
    }

    @Transactional
    public void updateUser(String actorId, String targetId, Dtos.UpdateUserRequest req, String actor, String ip) {
        User u = users.findById(targetId).orElseThrow(() -> ApiException.notFound("用户不存在"));
        boolean self = u.id.equals(actorId);
        if (self && req.role() != null && req.role() != Role.ADMIN) {
            throw ApiException.badRequest("不能降级当前登录的管理员");
        }
        if (self && req.status() == UserStatus.BANNED) {
            throw ApiException.badRequest("不能停用当前登录的管理员");
        }
        if (req.status() == UserStatus.BANNED && u.status != UserStatus.BANNED) {
            // 解封后旧 token 也不能复活。
            u.bumpTokenVersion();
        }
        if (req.status() != null) {
            u.status = req.status();
        }
        if (req.role() != null) {
            u.role = req.role();
        }
        users.save(u);
        if (u.status == UserStatus.BANNED) {
            audit.log(actor, "USER_BAN", u.email, "账号已停用", ip);
        } else {
            audit.log(actor, "USER_UPDATE", u.email, "role=" + u.role + " status=" + u.status, ip);
        }
    }

    @Transactional
    public long adjustBalance(String targetId, Dtos.AdjustBalanceRequest req, String actor, String ip) {
        User u = users.findById(targetId).orElseThrow(() -> ApiException.notFound("用户不存在"));
        String remark = req.remark() == null || req.remark().isBlank() ? "管理员调整" : req.remark().trim();
        long balance = walletService.adjust(targetId, req.deltaCents(), remark);
        audit.log(actor, "BALANCE_ADJUST", u.email,
                (req.deltaCents() > 0 ? "+" : "") + "¥" + String.format("%.2f", req.deltaCents() / 100.0) + " " + remark,
                ip);
        return balance;
    }

    /* --------------------------------- 任务 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.TaskPageDto<Dtos.AdminTaskDto> tasks(String status, String q, Integer page, Integer size) {
        return taskService.adminPage(status, q, page, size);
    }

    @Transactional
    public Dtos.TaskDto retryTask(String taskId, String actor, String ip) {
        Dtos.TaskDto dto = taskService.retry(taskId, null, true);
        audit.log(actor, "TASK_RETRY", taskId, "管理员补偿重试 " + dto.appName() + " " + dto.version(), ip);
        return dto;
    }

    @Transactional
    public Dtos.TaskDto cancelTask(String taskId, String actor, String ip) {
        Dtos.TaskDto dto = taskService.cancel(taskId, null, true);
        audit.log(actor, "TASK_CANCEL", taskId, "管理员取消任务并退款", ip);
        return dto;
    }

    /* --------------------------------- 应用 --------------------------------- */

    @Transactional(readOnly = true)
    public List<Dtos.AppDto> apps() {
        return catalog.allForAdmin().stream().map(Dtos.AppDto::from).toList();
    }

    @Transactional
    public Dtos.AppDto saveApp(Dtos.SaveAppRequest req, String actor, String ip) {
        validatePrices(req.priceDownloadCents(), req.priceDecryptCents());
        AppItem app = catalog.saveApp(req);
        audit.log(actor, "APP_UPDATE", app.name + " " + app.version,
                req.id() == null ? "新建应用 " + app.bundleId : "编辑应用 " + app.bundleId, ip);
        return Dtos.AppDto.from(app);
    }

    @Transactional
    public void setShelf(String id, AppStatus status, String actor, String ip) {
        catalog.setShelf(id, status);
        audit.log(actor, "APP_SHELF", id, status == AppStatus.ON_SALE ? "上架" : "下架：" + status, ip);
    }

    /* --------------------------------- 订单 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.AdminOrdersResponse orders() {
        List<Dtos.AdminTransactionDto> rows = txs.findAllByOrderByCreatedAtDesc().stream()
                .map(t -> new Dtos.AdminTransactionDto(t.id, t.userId,
                        users.findById(t.userId).map(u -> u.email).orElse("—"),
                        t.type, t.amountCents, t.balanceAfterCents, t.remark, t.refId, t.createdAt))
                .toList();
        return new Dtos.AdminOrdersResponse(rechargeService.allForAdmin(), rows);
    }

    @Transactional
    public void refundOrder(String orderId, String actor, String ip) {
        RechargeOrder order = rechargeService.require(orderId);
        rechargeService.refund(orderId);
        String email = users.findById(order.userId).map(u -> u.email).orElse(order.userId);
        audit.log(actor, "ORDER_REFUND", orderId,
                "退款 ¥" + String.format("%.2f", (order.amountCents + order.bonusCents) / 100.0)
                        + "（" + email + "）", ip);
    }

    /* --------------------------------- 定价 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.PricingDto pricing() {
        AppSettings s = settingsService.get();
        return new Dtos.PricingDto(s.decryptPriceCents, s.downloadPriceCents, s.concurrency,
                rechargeService.packages());
    }

    @Transactional
    public void savePricing(Dtos.SavePricingRequest req, String actor, String ip) {
        validatePrices(req.decryptPriceCents(), req.downloadPriceCents());
        if (req.decryptPriceCents() <= 0 || req.downloadPriceCents() <= 0) {
            throw ApiException.badRequest("价格必须大于 0");
        }
        int concurrency = Math.max(1, Math.min(8, req.concurrency()));

        settingsService.update(new Dtos.UpdateSettingsRequest(req.decryptPriceCents(), req.downloadPriceCents(),
                concurrency, null, null, null, null, null, null, null, null, null));

        if (req.packages() != null) {
            List<RechargePackage> next = new ArrayList<>();
            int i = 0;
            for (Dtos.PackageDto p : req.packages()) {
                if (p.amountCents() <= 0) {
                    throw ApiException.badRequest("套餐金额必须大于 0");
                }
                RechargePackage pkg = new RechargePackage();
                pkg.id = p.id() == null || p.id().isBlank() ? Ids.of("pkg") : p.id().trim();
                pkg.amountCents = p.amountCents();
                pkg.bonusCents = Math.max(0, p.bonusCents());
                pkg.recommended = p.recommended();
                pkg.sortOrder = i++;
                next.add(pkg);
            }
            packages.deleteAll();
            packages.saveAll(next);
        }

        audit.log(actor, "PRICING_UPDATE", "定价配置",
                "脱壳 ¥" + yuan(req.decryptPriceCents()) + " / 下载 ¥" + yuan(req.downloadPriceCents())
                        + " / 并发 " + concurrency, ip);
    }

    /* ------------------------------- Apple ID ------------------------------- */

    @Transactional(readOnly = true)
    public List<Dtos.AppleAccountDto> appleAccounts() {
        return appleAccounts.findAllByOrderByPriorityAsc().stream().map(keychain::toDto).toList();
    }

    @Transactional
    public List<Dtos.AppleAccountDto> syncAppleAccounts(String actor, String ip) {
        List<AppleAccount> all = appleAccounts.findAllByOrderByPriorityAsc();
        for (AppleAccount a : all) {
            if (a.sessionOk) {
                a.lastUsedAt = Instant.now().minusSeconds(ThreadLocalRandom.current().nextInt(5, 125) * 60L);
            }
        }
        appleAccounts.saveAll(all);
        long ready = all.stream().filter(a -> a.status == AppleStatus.READY).count();
        audit.log(actor, "APPLE_SYNC", "Apple ID 池", ready + " 个可用 / " + all.size() + " 个账号", ip);
        return all.stream().map(keychain::toDto).toList();
    }

    @Transactional
    public Dtos.AppleAccountDto updateApple(String id, Dtos.UpdateAppleRequest req, String actor, String ip) {
        AppleAccount a = appleAccounts.findById(id).orElseThrow(() -> ApiException.notFound("账号不存在"));
        if (req.status() != null) {
            a.status = req.status();
            if (req.status() == AppleStatus.DISABLED) {
                a.sessionOk = false;
            }
            if (req.status() == AppleStatus.NEED_LOGIN) {
                a.sessionOk = false;
            }
            if (req.status() == AppleStatus.READY && a.credentialOk) {
                a.sessionOk = true;
            }
        }
        if (req.priority() != null) {
            a.priority = Math.max(1, req.priority());
        }
        if (req.storefront() != null && !req.storefront().isBlank()) {
            a.storefront = Storefronts.normalize(req.storefront());
        }
        appleAccounts.save(a);
        audit.log(actor, "APPLE_UPDATE", a.email,
                "status=" + a.status + " priority=" + a.priority + " storefront=" + a.storefront, ip);
        return keychain.toDto(a);
    }

    /* --------------------------------- 设置 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.SettingsDto settings() {
        return Dtos.SettingsDto.from(settingsService.get());
    }

    @Transactional
    public Dtos.SettingsDto saveSettings(Dtos.UpdateSettingsRequest req, String actor, String ip) {
        validatePrices(req.decryptPriceCents(), req.downloadPriceCents());
        AppSettings s = settingsService.update(req);
        audit.log(actor, "SETTINGS_UPDATE", "系统设置",
                "维护=" + s.maintenance + " 并发=" + s.concurrency + " 免费下载=" + s.freeDownloadDays
                        + "天" + (req.announcement() == null ? "" : " 公告已更新"), ip);
        return Dtos.SettingsDto.from(s);
    }

    /* --------------------------------- 日志 --------------------------------- */

    @Transactional(readOnly = true)
    public Dtos.LogPageDto logs(String action, String q, Integer page, Integer size) {
        int p = Pages.page(page);
        int s = Pages.size(size);
        List<AuditLog> all = audit.list(null);
        Instant startOfDay = java.time.LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        long today = all.stream().filter(l -> !l.createdAt.isBefore(startOfDay)).count();
        String key = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<AuditLog> filtered = audit.list(action).stream()
                .filter(l -> key.isEmpty()
                        || contains(l.actor, key) || contains(l.target, key)
                        || contains(l.detail, key) || contains(l.ip, key))
                .toList();
        List<Dtos.AuditLogDto> items = Pages.slice(filtered, p, s).stream().map(Dtos.AuditLogDto::from).toList();
        return new Dtos.LogPageDto(items, p, s, filtered.size(), Pages.pageCount(filtered.size(), s), all.size(), today);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(q);
    }

    /* -------------------------------- helpers ------------------------------- */

    private static void validatePrices(Long download, Long decrypt) {
        if (download != null && (download < 0 || download > 1_000_000)) {
            throw ApiException.badRequest("下载价格不合法");
        }
        if (decrypt != null && (decrypt < 0 || decrypt > 1_000_000)) {
            throw ApiException.badRequest("脱壳价格不合法");
        }
    }

    private static String yuan(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }
}
