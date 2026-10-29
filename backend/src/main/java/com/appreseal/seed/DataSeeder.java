package com.appreseal.seed;

import com.appreseal.common.Ids;
import com.appreseal.domain.*;
import com.appreseal.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.appreseal.config.AppProperties;

/**
 * 演示数据。与前端 mock({@code src/mock/db.ts}) 保持同一套账号、应用、任务与流水，
 * 便于把前端从 {@code VITE_API_MODE=mock} 切到真实后端时视觉不变。
 *
 * <p>仅在 {@code appreseal.seed.enabled=true} 且 users 表为空时写入一次。
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final AppRepository apps;
    private final TaskRepository tasks;
    private final TaskLogRepository taskLogs;
    private final TransactionRepository txs;
    private final RechargeOrderRepository orders;
    private final RechargePackageRepository packages;
    private final LibraryRepository library;
    private final AppleAccountRepository appleAccounts;
    private final AppSettingsRepository settings;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;

    public DataSeeder(AppProperties props, UserRepository users, WalletRepository wallets, AppRepository apps,
                      TaskRepository tasks, TaskLogRepository taskLogs, TransactionRepository txs,
                      RechargeOrderRepository orders, RechargePackageRepository packages,
                      LibraryRepository library, AppleAccountRepository appleAccounts,
                      AppSettingsRepository settings, AuditLogRepository auditLogs, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.wallets = wallets;
        this.apps = apps;
        this.tasks = tasks;
        this.taskLogs = taskLogs;
        this.txs = txs;
        this.orders = orders;
        this.packages = packages;
        this.library = library;
        this.appleAccounts = appleAccounts;
        this.settings = settings;
        this.auditLogs = auditLogs;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.getSeed().isEnabled()) {
            log.info("演示数据已关闭（appreseal.seed.enabled=false）");
            return;
        }
        if (users.count() > 0) {
            log.info("已有 {} 个用户，跳过演示数据写入", users.count());
            return;
        }
        log.info("写入演示数据：9 个用户 / 12 个应用 / 5 个任务");
        seedUsers();
        seedApps();
        seedTasks();
        seedTransactions();
        seedOrders();
        seedLibrary();
        seedPackages();
        seedAppleAccounts();
        seedSettings();
        seedAuditLogs();
        log.info("演示数据写入完成");
    }

    /* ---------------------------------- 用户 ---------------------------------- */

    private void seedUsers() {
        User demo = user("u_demo", "demo@appreseal.com", "demo1234", "Yin", Role.USER, UserStatus.ACTIVE,
                "#0071e3", 60 * 24 * 92, 38);
        User admin = user("u_admin", "admin@appreseal.com", "admin1234", "管理员", Role.ADMIN,
                UserStatus.ACTIVE, "#1d1d1f", 60 * 24 * 180, 12);
        users.save(demo);
        users.save(admin);
        wallet("u_demo", 4200, 30000, 25800);
        wallet("u_admin", 0, 0, 0);

        String[][] others = {
                {"u_10", "lin@example.com", "林深", "#ff375f", "12800"},
                {"u_11", "zhou@example.com", "周予安", "#30d158", "4600"},
                {"u_12", "chen@example.com", "陈默", "#ff9f0a", "0"},
                {"u_13", "ada@example.com", "Ada", "#5e5ce6", "21300"},
                {"u_14", "wang@example.com", "王野", "#64d2ff", "900"},
                {"u_15", "zhao@example.com", "赵四", "#bf5af2", "3200"},
                {"u_16", "ken@example.com", "Ken", "#ff453a", "0"}
        };
        for (int i = 0; i < others.length; i++) {
            String[] o = others[i];
            User u = user(o[0], o[1], "demo1234", o[2], Role.USER,
                    i == 6 ? UserStatus.BANNED : UserStatus.ACTIVE, o[3],
                    60 * 24 * (40 - i * 3), 90L * (i + 1));
            users.save(u);
            long balance = Long.parseLong(o[4]);
            wallet(u.id, balance, balance + i * 1200L, i * 1200L);
        }
    }

    private User user(String id, String email, String password, String nickname, Role role, UserStatus status,
                      String color, long createdMinutesAgo, long loginMinutesAgo) {
        User u = new User();
        u.id = id;
        u.email = email;
        u.passwordHash = encoder.encode(password);
        u.nickname = nickname;
        u.role = role;
        u.status = status;
        u.avatarColor = color;
        u.createdAt = Ids.minutesAgo(createdMinutesAgo);
        u.lastLoginAt = Ids.minutesAgo(loginMinutesAgo);
        return u;
    }

    private void wallet(String userId, long balance, long recharge, long spend) {
        Wallet w = new Wallet();
        w.userId = userId;
        w.balanceCents = balance;
        w.totalRechargeCents = recharge;
        w.totalSpendCents = spend;
        wallets.save(w);
    }

    /* ---------------------------------- 应用 ---------------------------------- */

    private record SeedApp(String bundleId, String name, String developer, String category, String iconText,
                           int iconHue, String version, long sizeBytes, String minIOS, String executable,
                           long downloadCents, long decryptCents, long downloads, String description) {}

    private List<AppItem> seededApps = List.of();

    private void seedApps() {
        List<SeedApp> seeds = List.of(
                new SeedApp("com.example.qingjian", "青笺", "青屿工作室", "社交", "笺", 145, "1.4.2", 86_016_000L, "15.0",
                        "QingJian", 0, 0, 128,
                        "虚构的消息样本，只用于把界面跑起来。不对应任何已上架应用。"),
                new SeedApp("com.example.ripple", "短波", "北岸实验室", "影音", "波", 330, "3.2.1", 120_586_240L, "15.0",
                        "Ripple", 0, 0, 96,
                        "虚构的短视频样本。名称、开发者和包名都是编的。"),
                new SeedApp("com.example.stargaze", "观星", "灯塔软件", "影音", "星", 18, "2.1.4", 98_304_000L, "15.0",
                        "StarGaze", 0, 0, 74,
                        "虚构的视频样本，用来演示成品库。"),
                new SeedApp("com.example.paperbird", "纸鹤", "南风通讯", "社交", "鹤", 200, "1.1.3", 64_225_280L, "15.0",
                        "PaperBird", 0, 0, 52,
                        "虚构的通讯样本。"),
                new SeedApp("com.example.melodybox", "旋律盒", "弦月音频", "影音", "律", 120, "4.0.8", 77_594_624L, "15.0",
                        "MelodyBox", 0, 0, 43,
                        "虚构的音频样本。"),
                new SeedApp("com.example.noteboard", "便签坞", "格子工坊", "效率", "坞", 30, "2.2.5", 54_525_952L, "15.0",
                        "NoteBoard", 0, 0, 31,
                        "虚构的笔记样本。"),
                new SeedApp("com.example.askstone", "问石", "山海计算", "工具", "石", 225, "0.9.4", 48_234_496L, "15.0",
                        "AskStone", 0, 0, 68,
                        "虚构的对话样本。"),
                new SeedApp("com.example.lamplang", "灯语", "湖畔计算", "工具", "灯", 20, "1.3.0", 70_123_520L, "15.0",
                        "LampLang", 0, 0, 25,
                        "虚构的助手样本。"),
                new SeedApp("com.example.framebox", "画框", "镜湖影像", "社交", "框", 320, "3.5.2", 91_750_400L, "15.0",
                        "FrameBox", 0, 0, 40,
                        "虚构的图片社区样本。"),
                new SeedApp("com.example.brief", "简讯", "西窗网络", "社交", "讯", 210, "1.0.6", 61_865_984L, "15.0",
                        "Brief", 0, 0, 23,
                        "虚构的动态时间线样本。"),
                new SeedApp("com.example.waymark", "路标", "途中地图", "工具", "路", 205, "1.5.2", 110_100_480L, "15.0",
                        "WayMark", 0, 0, 19,
                        "虚构的地图样本。"),
                new SeedApp("com.example.journal", "手帐", "纸页文化", "社交", "帐", 350, "2.8.0", 83_886_080L, "15.0",
                        "Journal", 0, 0, 35,
                        "虚构的图文样本。当前为下架状态，用来演示后台上下架。")
        );

        List<AppItem> items = new ArrayList<>();
        int index = 0;
        for (SeedApp s : seeds) {
            AppItem a = new AppItem();
            a.id = "app_" + (index + 1);
            a.bundleId = s.bundleId();
            a.name = s.name();
            a.developer = s.developer();
            a.category = s.category();
            a.iconText = s.iconText();
            a.iconHue = s.iconHue();
            a.version = s.version();
            a.sizeBytes = s.sizeBytes();
            a.minIOS = s.minIOS();
            a.executable = s.executable();
            a.sha256 = sha256(s.bundleId() + s.version());
            a.priceDownloadCents = s.downloadCents();
            a.priceDecryptCents = s.decryptCents();
            a.downloads = s.downloads();
            a.status = index == 11 ? AppStatus.OFF_SHELF : AppStatus.ON_SALE;
            a.verified = true;
            a.arch = "arm64";
            a.description = s.description();
            a.updatedAt = Ids.minutesAgo(60L * 24 * (index + 1) + 130);
            items.add(a);
            index++;
        }
        apps.saveAll(items);
        this.seededApps = items;
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "0".repeat(64);
        }
    }

    /* ---------------------------------- 任务 ---------------------------------- */

    private void seedTasks() {
        if (seededApps.size() < 12) {
            return;
        }
        task("t_seed_1", seededApps.get(0), TaskStatus.SUCCEEDED, 60 * 26, null, true);
        task("t_seed_2", seededApps.get(7), TaskStatus.SUCCEEDED, 60 * 24 * 4, null, true);
        task("t_seed_3", seededApps.get(6), TaskStatus.FAILED, 60 * 30,
                "凭证不匹配：credential does not decode this SINF priv record", false);
        task("t_seed_4", seededApps.get(2), TaskStatus.SUCCEEDED, 60 * 24 * 9, null, true);
        task("t_seed_5", seededApps.get(4), TaskStatus.CANCELED, 60 * 24 * 12, null, false);
    }

    private void task(String id, AppItem app, TaskStatus status, long minutesAgo, String error, boolean success) {
        Instant createdAt = Ids.minutesAgo(minutesAgo);
        Task t = new Task();
        t.id = id;
        t.userId = "u_demo";
        t.appName = app.name;
        t.bundleId = app.bundleId;
        t.version = app.version;
        t.source = TaskSource.STORE;
        t.status = status;
        t.progress = status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED ? 100 : 0;
        t.stage = status.stage();
        t.priceCents = app.priceDecryptCents;
        t.refundedCents = status == TaskStatus.FAILED || status == TaskStatus.CANCELED ? t.priceCents : 0;
        t.createdAt = createdAt;
        t.finishedAt = createdAt.plusSeconds(180);
        t.error = error;
        t.sizeBytes = success ? app.sizeBytes : null;
        t.sha256 = success ? app.sha256 : null;
        t.simulated = false;
        tasks.save(t);

        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"INFO", "任务已创建，进入队列"});
        if (status == TaskStatus.FAILED) {
            lines.add(new String[]{"INFO", "分配 Apple ID 池账号 id-01"});
            lines.add(new String[]{"INFO", "开始下载加密包…"});
            lines.add(new String[]{"ERROR", "✘ credential does not decode this SINF priv record"});
            lines.add(new String[]{"WARN", "已自动退款 " + (t.priceCents / 100.0) + " 元"});
        } else if (status == TaskStatus.CANCELED) {
            lines.add(new String[]{"WARN", "任务已取消，已自动退款 " + (t.priceCents / 100.0) + " 元"});
        } else {
            lines.add(new String[]{"INFO", "分配 Apple ID 池账号 id-01（优先级 1）"});
            lines.add(new String[]{"INFO", "开始下载加密包…"});
            lines.add(new String[]{"OK", "下载完成，开始解析 SINF / SUPF"});
            lines.add(new String[]{"INFO", "FairPlay 解密中，cryptsize=4096"});
            lines.add(new String[]{"OK", "Mach-O 加密页已还原"});
            lines.add(new String[]{"OK", "校验通过：cryptid=0"});
            lines.add(new String[]{"OK", "脱壳完成，产物已入库"});
        }
        int seq = 1;
        for (String[] line : lines) {
            TaskLog l = new TaskLog(id, seq++, line[0], line[1]);
            l.at = createdAt.plusSeconds(seq * 3L);
            taskLogs.save(l);
        }
    }

    /* --------------------------------- 流水 ---------------------------------- */

    private void seedTransactions() {
        tx("tx_1", "u_demo", TxType.RECHARGE, 10000, 10000, "充值 ¥100（赠送 ¥5）", null, 60 * 24 * 20);
        tx("tx_2", "u_demo", TxType.DECRYPT, -600, 9400, "任务 · 青笺 1.4.2", "t_seed_1", 60 * 26);
        tx("tx_3", "u_demo", TxType.DOWNLOAD, -300, 9100, "下载成品 · 观星 2.1.4", null, 60 * 24 * 6);
        tx("tx_4", "u_demo", TxType.REFUND, 800, 9900, "任务失败 · 问石 0.9.4", "t_seed_3", 60 * 29);
        tx("tx_5", "u_demo", TxType.DECRYPT, -600, 9300, "任务 · 灯语 1.3.0", "t_seed_2", 60 * 24 * 4);
        tx("tx_6", "u_demo", TxType.RECHARGE, 20000, 29300, "演示入账", null, 60 * 24 * 3);
        tx("tx_7", "u_demo", TxType.DECRYPT, -800, 28500, "任务 · 问石 0.9.4", "t_seed_3", 60 * 30);
        tx("tx_8", "u_demo", TxType.DOWNLOAD, -300, 28200, "下载成品 · 纸鹤 1.1.3", null, 60 * 24 * 2);
        tx("tx_9", "u_demo", TxType.DOWNLOAD, -300, 27900, "下载成品 · 便签坞 2.2.5", null, 60 * 20);
    }

    private void tx(String id, String userId, TxType type, long amount, long balanceAfter, String remark,
                    String refId, long minutesAgo) {
        Transaction t = new Transaction();
        t.id = id;
        t.userId = userId;
        t.type = type;
        t.amountCents = amount;
        t.balanceAfterCents = balanceAfter;
        t.remark = remark;
        t.refId = refId;
        t.createdAt = Ids.minutesAgo(minutesAgo);
        txs.save(t);
    }

    /* --------------------------------- 订单 ---------------------------------- */

    private void seedOrders() {
        order("ro_1", "u_demo", 20000, 0, PayMethod.ALIPAY, OrderStatus.PAID, 60 * 24 * 3, true);
        order("ro_2", "u_demo", 10000, 500, PayMethod.WECHAT, OrderStatus.PAID, 60 * 24 * 20, true);
        order("ro_3", "u_10", 30000, 2000, PayMethod.ALIPAY, OrderStatus.PAID, 60 * 5, false);
        order("ro_4", "u_13", 50000, 5000, PayMethod.WECHAT, OrderStatus.PAID, 60 * 9, false);
    }

    private void order(String id, String userId, long amount, long bonus, PayMethod method, OrderStatus status,
                       long minutesAgo, boolean paid) {
        RechargeOrder o = new RechargeOrder();
        o.id = id;
        o.userId = userId;
        o.amountCents = amount;
        o.bonusCents = bonus;
        o.method = method;
        o.status = status;
        o.createdAt = Ids.minutesAgo(minutesAgo);
        o.paidAt = paid ? o.createdAt : null;
        orders.save(o);
    }

    /* -------------------------------- 下载库 --------------------------------- */

    private void seedLibrary() {
        if (seededApps.size() < 12) {
            return;
        }
        lib("lib_1", seededApps.get(0), "DECRYPT", 60 * 26, "t_seed_1");
        lib("lib_2", seededApps.get(2), "PURCHASE", 60 * 24 * 6, null);
        lib("lib_3", seededApps.get(7), "DECRYPT", 60 * 24 * 4, "t_seed_2");
    }

    private void lib(String id, AppItem app, String source, long minutesAgo, String taskId) {
        LibraryItem l = new LibraryItem();
        l.id = id;
        l.userId = "u_demo";
        l.appId = app.id;
        l.bundleId = app.bundleId;
        l.name = app.name;
        l.version = app.version;
        l.source = "DECRYPT".equals(source) ? LibrarySource.DECRYPT : LibrarySource.PURCHASE;
        l.acquiredAt = Ids.minutesAgo(minutesAgo);
        l.freeUntil = l.acquiredAt.plusSeconds(30L * 24 * 3600);
        l.taskId = taskId;
        library.save(l);
    }

    /* --------------------------------- 套餐 ---------------------------------- */

    private void seedPackages() {
        pkg("pkg_50", 5000, 0, false, 0);
        pkg("pkg_100", 10000, 500, true, 1);
        pkg("pkg_300", 30000, 2000, false, 2);
        pkg("pkg_500", 50000, 5000, false, 3);
    }

    private void pkg(String id, long amount, long bonus, boolean recommended, int sort) {
        RechargePackage p = new RechargePackage();
        p.id = id;
        p.amountCents = amount;
        p.bonusCents = bonus;
        p.recommended = recommended;
        p.sortOrder = sort;
        packages.save(p);
    }

    /* -------------------------------- Apple ID ------------------------------- */

    private void seedAppleAccounts() {
        apple("aa_1", "id-01@appreseal.com", "cn", 1, true, true, AppleStatus.READY, 7, 18);
        apple("aa_2", "id-02@appreseal.com", "us", 2, true, true, AppleStatus.READY, 4, 60 * 7);
        apple("aa_3", "id-03@appreseal.com", "jp", 3, true, false, AppleStatus.NEED_LOGIN, 1, 60 * 26);
        apple("aa_4", "id-04@appreseal.com", "hk", 4, false, false, AppleStatus.DISABLED, 0, 60 * 24 * 40);
    }

    private void apple(String id, String email, String storefront, int priority, boolean credentialOk, boolean sessionOk,
                       AppleStatus status, int boundApps, long minutesAgo) {
        AppleAccount a = new AppleAccount();
        a.id = id;
        a.email = email;
        a.storefront = storefront;
        a.priority = priority;
        a.credentialOk = credentialOk;
        a.sessionOk = sessionOk;
        a.status = status;
        a.boundApps = boundApps;
        a.lastUsedAt = Ids.minutesAgo(minutesAgo);
        appleAccounts.save(a);
    }

    /* --------------------------------- 设置 ---------------------------------- */

    private void seedSettings() {
        AppSettings s = settings.findById(AppSettings.SINGLETON_ID).orElseGet(AppSettings::new);
        s.id = AppSettings.SINGLETON_ID;
        s.decryptPriceCents = 600;
        s.downloadPriceCents = 300;
        s.concurrency = 2;
        s.maintenance = false;
        s.announcement = "本周六 02:00–04:00 进行存储扩容，期间任务排队时间可能延长，已提交的任务不受影响。";
        s.freeDownloadDays = 30;
        s.firstDownloadFreeHours = 24;
        settings.save(s);
    }

    /* --------------------------------- 日志 ---------------------------------- */

    private void seedAuditLogs() {
        audit("al_1", "管理员", "PRICING_UPDATE", "定价配置", "脱壳 ¥5.00 → ¥6.00", "10.0.2.14", 60 * 6);
        audit("al_2", "管理员", "APP_SHELF", "手帐 2.8.0", "下架：等待重新校验", "10.0.2.14", 60 * 11);
        audit("al_3", "管理员", "BALANCE_ADJUST", "zhao@example.com", "+¥20.00 补偿", "10.0.2.14", 60 * 22);
        audit("al_4", "管理员", "TASK_RETRY", "t_seed_3", "问石 0.9.4 重试", "10.0.2.14", 60 * 28);
        audit("al_5", "管理员", "USER_BAN", "ken@example.com", "异常下载频率", "10.0.2.14", 60 * 40);
        audit("al_6", "系统", "APPLE_SYNC", "Apple ID 池", "4 个账号，1 个需重新登录", "127.0.0.1", 60 * 2);
    }

    private void audit(String id, String actor, String action, String target, String detail, String ip,
                       long minutesAgo) {
        AuditLog l = new AuditLog();
        l.id = id;
        l.actor = actor;
        l.action = action;
        l.target = target;
        l.detail = detail;
        l.ip = ip;
        l.createdAt = Ids.minutesAgo(minutesAgo);
        auditLogs.save(l);
    }
}
