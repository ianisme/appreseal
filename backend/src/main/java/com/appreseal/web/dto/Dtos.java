package com.appreseal.web.dto;

import com.appreseal.common.LogSanitizer;
import com.appreseal.domain.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 全部 HTTP 契约。字段命名与前端 types.ts 一一对应（金额单位为分）。 */
public final class Dtos {
    private Dtos() {}

    /* ---------------------------------- auth ---------------------------------- */

    public record UserDto(String id, String email, String nickname, Role role, UserStatus status,
                          String avatarColor, Instant createdAt, Instant lastLoginAt) {
        public static UserDto from(User u) {
            return new UserDto(u.id, u.email, u.nickname, u.role, u.status, u.avatarColor, u.createdAt, u.lastLoginAt);
        }
    }

    public record AuthResponse(String token, UserDto user) {}

    public record LoginRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请输入邮箱") String email,
                               @NotBlank(message = "请输入密码") String password) {}

    public record RegisterRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请输入邮箱") String email,
                                  @Size(min = 8, max = 72, message = "密码至少 8 位") String password,
                                  @Size(max = 24, message = "昵称不能超过 24 个字符") String nickname) {}

    public record UpdateProfileRequest(@Size(max = 24, message = "昵称不能超过 24 个字符") String nickname,
                                       String avatarColor) {}

    public record ChangePasswordRequest(@NotBlank(message = "请输入当前密码") String oldPassword,
                                        @Size(min = 8, max = 72, message = "新密码至少 8 位") String newPassword) {}

    public record ForgotPasswordRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请输入邮箱") String email) {}

    public record ResetPasswordRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请输入邮箱") String email,
                                       @NotBlank(message = "请输入验证码") @Size(min = 6, max = 6, message = "验证码不正确或已过期") String code,
                                       @Size(min = 8, max = 72, message = "新密码至少 8 位") String newPassword) {}

    /* --------------------------------- 分页 --------------------------------- */

    public record PageDto<T>(List<T> items, int page, int size, long total, int pages) {}

    public record TaskPageDto<T>(List<T> items, int page, int size, long total, int pages,
                                 long running, long succeeded, long failed, long canceled, long revenueCents) {}

    public record UserPageDto(List<AdminUserDto> items, int page, int size, long total, int pages,
                              long registered, long active, long banned, long balanceCents) {}

    public record LogPageDto(List<AuditLogDto> items, int page, int size, long total, int pages,
                             long totalAll, long today) {}

    /* ------------------------------- 分片上传 ------------------------------- */

    public record CreateUploadRequest(@NotBlank(message = "缺少文件名") @Size(max = 180) String filename,
                                      @NotNull(message = "缺少文件大小") @Min(value = 1, message = "文件是空的") Long size) {}

    public record UploadStatusDto(String id, String filename, long size, long received, int chunkSize,
                                  Instant expiresAt) {}

    /* ---------------------------------- apps ---------------------------------- */

    public record AppDto(String id, String bundleId, String name, String developer, String category,
                         String iconText, int iconHue, String version, long sizeBytes, String minIOS,
                         String executable, String sha256, long priceDownloadCents, long priceDecryptCents,
                         long downloads, AppStatus status, boolean verified, List<String> arch,
                         String description, Instant updatedAt) {
        public static AppDto from(AppItem a) {
            List<String> arch = a.arch == null || a.arch.isBlank() ? List.of() : List.of(a.arch.split(","));
            return new AppDto(a.id, a.bundleId, a.name, a.developer, a.category, a.iconText, a.iconHue, a.version,
                    a.sizeBytes, a.minIOS, a.executable, a.sha256, a.priceDownloadCents, a.priceDecryptCents,
                    a.downloads, a.status, a.verified, arch, a.description, a.updatedAt);
        }
    }

    public record StoreHit(String bundleId, String name, String developer, String version,
                           String iconText, int iconHue, boolean inLibrary, String iconUrl, Long trackId) {}

    public record StoreSearchResult(String storefront, String storefrontName, int matchedAccounts,
                                    List<StoreHit> hits) {}

    /** 池子里已标记、且未禁用的市场。新建脱壳只展示这些。 */
    public record StoreMarket(String code, String name, int accounts) {}

    public record DownloadResult(AppDto app, long balanceCents, String libraryId) {}

    public record SiteDto(boolean maintenance, String announcement, long decryptPriceCents, long downloadPriceCents) {
        public static SiteDto from(AppSettings s) {
            return new SiteDto(s.maintenance, s.announcement, s.decryptPriceCents, s.downloadPriceCents);
        }
    }

    public record SaveAppRequest(String id, String bundleId, String name, String developer, String category,
                                 String iconText, Integer iconHue, String version, Long sizeBytes, String minIOS,
                                 String executable, String sha256, Long priceDownloadCents, Long priceDecryptCents,
                                 AppStatus status, Boolean verified, List<String> arch, String description) {}

    public record ShelfRequest(@NotNull(message = "缺少上架状态") AppStatus status) {}

    /* ---------------------------------- tasks --------------------------------- */

    public record TaskLogDto(Instant at, String level, String message) {
        public static TaskLogDto from(TaskLog l, boolean admin) {
            String message = admin ? l.message : LogSanitizer.forUser(l.message);
            return new TaskLogDto(l.at, l.level, message);
        }
    }

    public record TaskDto(String id, String userId, String appName, String bundleId, String version,
                          String storefront, TaskSource source, TaskStatus status, int progress, String stage,
                          long priceCents, long refundedCents, Instant createdAt, Instant finishedAt,
                          String error, Long sizeBytes, String sha256, boolean simulated, boolean artifactPurged,
                          List<TaskLogDto> logs) {
        public static TaskDto from(Task t, List<TaskLog> logs, boolean admin) {
            return new TaskDto(t.id, t.userId, t.appName, t.bundleId, t.version, t.storefront, t.source, t.status,
                    t.progress, t.stage, t.priceCents, t.refundedCents, t.createdAt, t.finishedAt,
                    admin ? t.error : LogSanitizer.forUser(t.error),
                    t.sizeBytes, t.sha256, t.simulated, t.artifactPurged,
                    logs.stream()
                            .filter(l -> admin || !l.internal)
                            .map(l -> TaskLogDto.from(l, admin))
                            .toList());
        }
    }

    public record CreateTaskRequest(@NotNull(message = "缺少来源") TaskSource source,
                                    @NotBlank(message = "缺少 bundle id") String bundleId,
                                    String appName, String version, String storefront) {}

    /* --------------------------------- wallet --------------------------------- */

    public record WalletDto(long balanceCents, long totalRechargeCents, long totalSpendCents) {}

    public record TransactionDto(String id, String userId, TxType type, long amountCents, long balanceAfterCents,
                                 String remark, String refId, Instant createdAt) {
        public static TransactionDto from(Transaction t) {
            return new TransactionDto(t.id, t.userId, t.type, t.amountCents, t.balanceAfterCents, t.remark, t.refId, t.createdAt);
        }
    }

    public record PackageDto(String id, long amountCents, long bonusCents, boolean recommended) {
        public static PackageDto from(RechargePackage p) {
            return new PackageDto(p.id, p.amountCents, p.bonusCents, p.recommended);
        }
    }

    public record CreateRechargeRequest(@NotBlank(message = "缺少套餐") String packageId,
                                        @NotNull(message = "请选择支付方式") PayMethod method) {}

    public record OrderDto(String id, String userId, long amountCents, long bonusCents, PayMethod method,
                           OrderStatus status, Instant createdAt, Instant paidAt) {
        public static OrderDto from(RechargeOrder o) {
            return new OrderDto(o.id, o.userId, o.amountCents, o.bonusCents, o.method, o.status, o.createdAt, o.paidAt);
        }
    }

    public record PayResult(long balanceCents) {}

    public record LibraryDto(String id, String userId, String appId, String bundleId, String name, String version,
                             LibrarySource source, Instant acquiredAt, Instant freeUntil, String taskId) {
        public static LibraryDto from(LibraryItem l) {
            return new LibraryDto(l.id, l.userId, l.appId, l.bundleId, l.name, l.version, l.source,
                    l.acquiredAt, l.freeUntil, l.taskId);
        }
    }

    /* ---------------------------------- admin --------------------------------- */

    public record AdminUserDto(String id, String email, String nickname, Role role, UserStatus status,
                               String avatarColor, Instant createdAt, Instant lastLoginAt,
                               long balanceCents, long tasks) {}

    public record UpdateUserRequest(UserStatus status, Role role) {}

    public record AdjustBalanceRequest(@NotNull(message = "缺少调整金额") Long deltaCents,
                                       @Size(max = 200, message = "备注过长") String remark) {}

    /** 与前端 {@code Task & {email}} 对齐的扁平结构（不嵌套 task）。 */
    public record AdminTaskDto(String id, String userId, String email, String appName, String bundleId,
                               String version, TaskSource source, TaskStatus status, int progress, String stage,
                               long priceCents, long refundedCents, Instant createdAt, Instant finishedAt,
                               String error, Long sizeBytes, String sha256, boolean simulated,
                               List<TaskLogDto> logs) {
        public static AdminTaskDto of(Task t, List<TaskLog> logs, String email) {
            return new AdminTaskDto(t.id, t.userId, email, t.appName, t.bundleId, t.version, t.source, t.status,
                    t.progress, t.stage, t.priceCents, t.refundedCents, t.createdAt, t.finishedAt, t.error,
                    t.sizeBytes, t.sha256, t.simulated, logs.stream().map(l -> TaskLogDto.from(l, true)).toList());
        }
    }

    public record AdminOrderDto(String id, String userId, String email, long amountCents, long bonusCents,
                                PayMethod method, OrderStatus status, Instant createdAt, Instant paidAt) {}

    public record AdminTransactionDto(String id, String userId, String email, TxType type, long amountCents,
                                      long balanceAfterCents, String remark, String refId, Instant createdAt) {}

    public record AdminOrdersResponse(List<AdminOrderDto> orders, List<AdminTransactionDto> transactions) {}

    public record PricingDto(long decryptPriceCents, long downloadPriceCents, int concurrency,
                             List<PackageDto> packages) {}

    public record SavePricingRequest(long decryptPriceCents, long downloadPriceCents, int concurrency,
                                     List<PackageDto> packages) {}

    public record AppleAccountDto(String id, String email, String storefront, int priority, boolean credentialOk,
                                  boolean sessionOk, AppleStatus status, int boundApps, Instant lastUsedAt) {
        public static AppleAccountDto from(AppleAccount a) {
            return new AppleAccountDto(a.id, a.email, a.storefront, a.priority, a.credentialOk, a.sessionOk,
                    a.status, a.boundApps, a.lastUsedAt);
        }
    }

    public record UpdateAppleRequest(AppleStatus status, Integer priority, String storefront) {}

    /** 密码只用于这一次 ipatool 登录，不落库、不写审计。 */
    public record AppleLoginRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请输入 Apple ID") String email,
                                    @NotBlank(message = "请输入密码") @Size(max = 200, message = "密码过长") String password,
                                    String authCode, String storefront) {}

    /** status = READY 已生成文件密钥；NEED_2FA 需要再提交 6 位验证码。 */
    public record AppleLoginResult(String status, String message, AppleAccountDto account) {}

    public record SettingsDto(long decryptPriceCents, long downloadPriceCents, int concurrency, boolean maintenance,
                              String announcement, int freeDownloadDays, int firstDownloadFreeHours,
                              int artifactRetentionDays, int uploadRetentionDays, int logRetentionDays,
                              boolean autoCleanup, int diskWarnPercent) {
        public static SettingsDto from(AppSettings s) {
            return new SettingsDto(s.decryptPriceCents, s.downloadPriceCents, s.concurrency, s.maintenance,
                    s.announcement, s.freeDownloadDays, s.firstDownloadFreeHours,
                    s.artifactRetentionDays, s.uploadRetentionDays, s.logRetentionDays,
                    s.autoCleanup, s.diskWarnPercent);
        }
    }

    public record UpdateSettingsRequest(Long decryptPriceCents, Long downloadPriceCents, Integer concurrency,
                                        Boolean maintenance, String announcement, Integer freeDownloadDays,
                                        Integer firstDownloadFreeHours, Integer artifactRetentionDays,
                                        Integer uploadRetentionDays, Integer logRetentionDays,
                                        Boolean autoCleanup, Integer diskWarnPercent) {}

    /* ------------------------------- 下载链接 ------------------------------- */

    public record DownloadLinkDto(String url, Instant expiresAt) {}

    /* ------------------------------ 存储与清理 ------------------------------ */

    public record DiskDto(long totalBytes, long usableBytes, long usedBytes, int usedPercent, String level) {}

    public record CleanupReportDto(int artifactsDeleted, long artifactBytesFreed, int uploadsDeleted,
                                   long uploadBytesFreed, int workDirsDeleted, int logsDeleted,
                                   int orphanFilesDeleted, long orphanBytesFreed, int errors,
                                   Instant startedAt, Instant finishedAt) {
        public static CleanupReportDto empty() {
            return new CleanupReportDto(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
        }
    }

    public record MaintenanceDto(DiskDto disk, boolean autoCleanup, int artifactRetentionDays,
                                 int uploadRetentionDays, int logRetentionDays, int diskWarnPercent,
                                 CleanupReportDto lastRun) {}

    public record AuditLogDto(String id, String actor, String action, String target, String detail, String ip,
                              Instant createdAt) {
        public static AuditLogDto from(AuditLog l) {
            return new AuditLogDto(l.id, l.actor, l.action, l.target, l.detail, l.ip, l.createdAt);
        }
    }

    public record SeriesPoint(String label, long value) {}

    public record DistributionItem(String label, long value, String tone) {}

    public record QueueInfo(int running, int queued, int concurrency) {}

    public record OverviewDto(long revenueTodayCents, long tasksToday, double successRate, long activeUsers,
                              List<SeriesPoint> series, List<DistributionItem> distribution, QueueInfo queue) {}

    public record UserRowDto(UserDto user, long balanceCents, long tasks) {}

    public record SimpleMessage(String message) {}
}
