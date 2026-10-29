package com.appreseal.web;

import com.appreseal.common.ApiException;
import com.appreseal.domain.AppStatus;
import com.appreseal.security.CurrentUser;
import com.appreseal.service.AdminService;
import com.appreseal.service.AppleKeychainService;
import com.appreseal.service.AuditService;
import com.appreseal.service.MaintenanceService;
import com.appreseal.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理后台。入口在 {@code SecurityConfig} 里被限定为 ROLE_ADMIN；
 * 每个写操作都会写一条审计日志（actor 为管理员邮箱，ip 取 X-Forwarded-For 首段）。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final AppleKeychainService keychain;
    private final AuditService audit;
    private final MaintenanceService maintenance;

    public AdminController(AdminService admin, AppleKeychainService keychain, AuditService audit,
                           MaintenanceService maintenance) {
        this.admin = admin;
        this.keychain = keychain;
        this.audit = audit;
        this.maintenance = maintenance;
    }

    @GetMapping("/overview")
    public Dtos.OverviewDto overview() {
        return admin.overview();
    }

    /* --------------------------------- 用户 --------------------------------- */

    @GetMapping("/users")
    public Dtos.UserPageDto users(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String role,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        return admin.users(q, role, status, page, size);
    }

    @PatchMapping("/users/{id}")
    public Dtos.SimpleMessage updateUser(@PathVariable String id,
                                         @RequestBody Dtos.UpdateUserRequest req,
                                         @AuthenticationPrincipal CurrentUser me,
                                         HttpServletRequest http) {
        CurrentUser actor = require(me);
        admin.updateUser(actor.id(), id, req, actor.email(), ip(http));
        return new Dtos.SimpleMessage("用户已更新");
    }

    @PostMapping("/users/{id}/balance")
    public void adjustBalance() {
        throw ApiException.badRequest("团队版不提供钱包和充值");
    }

    @RequestMapping({"/orders", "/orders/**", "/pricing"})
    public void paymentsGone() {
        throw ApiException.badRequest("团队版不提供订单和定价");
    }

    /* --------------------------------- 任务 --------------------------------- */

    @GetMapping("/tasks")
    public Dtos.TaskPageDto<Dtos.AdminTaskDto> tasks(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return admin.tasks(status, q, page, size);
    }

    @PostMapping("/tasks/{id}/retry")
    public Dtos.TaskDto retryTask(@PathVariable String id,
                                  @AuthenticationPrincipal CurrentUser me,
                                  HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.retryTask(id, actor.email(), ip(http));
    }

    @PostMapping("/tasks/{id}/cancel")
    public Dtos.TaskDto cancelTask(@PathVariable String id,
                                   @AuthenticationPrincipal CurrentUser me,
                                   HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.cancelTask(id, actor.email(), ip(http));
    }

    /* --------------------------------- 应用 --------------------------------- */

    @GetMapping("/apps")
    public List<Dtos.AppDto> apps() {
        return admin.apps();
    }

    @PostMapping("/apps")
    public Dtos.AppDto saveApp(@RequestBody Dtos.SaveAppRequest req,
                               @AuthenticationPrincipal CurrentUser me,
                               HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.saveApp(req, actor.email(), ip(http));
    }

    @PatchMapping("/apps/{id}/shelf")
    public Dtos.SimpleMessage shelf(@PathVariable String id,
                                    @Valid @RequestBody Dtos.ShelfRequest req,
                                    @AuthenticationPrincipal CurrentUser me,
                                    HttpServletRequest http) {
        CurrentUser actor = require(me);
        AppStatus status = req.status();
        admin.setShelf(id, status, actor.email(), ip(http));
        return new Dtos.SimpleMessage(status == AppStatus.ON_SALE ? "已上架" : "已下架");
    }

    /* ------------------------------- Apple ID ------------------------------- */

    @GetMapping("/apple-accounts")
    public List<Dtos.AppleAccountDto> appleAccounts() {
        return admin.appleAccounts();
    }

    /** 用 ipatool 登录并写出 Linux 文件密钥。密码不落库。 */
    @PostMapping("/apple-accounts/login")
    public Dtos.AppleLoginResult loginApple(@Valid @RequestBody Dtos.AppleLoginRequest req,
                                            @AuthenticationPrincipal CurrentUser me,
                                            HttpServletRequest http) {
        CurrentUser actor = require(me);
        return keychain.login(req.email(), req.password(), req.authCode(), req.storefront(), actor.email(), ip(http));
    }

    /** 导入 16 字节 credential.bin 并绑定到 Apple ID。内容不入库、不回显。 */
    @PostMapping("/apple-accounts/credential")
    public Dtos.AppleAccountDto bindCredential(@RequestParam String email,
                                               @RequestParam(required = false) String storefront,
                                               @RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal CurrentUser me,
                                               HttpServletRequest http) throws java.io.IOException {
        CurrentUser actor = require(me);
        if (file.getSize() > 16) {
            throw com.appreseal.common.ApiException.badRequest("credential.bin 必须是 16 字节");
        }
        return keychain.bindCredential(email, storefront, file.getBytes(), actor.email(), ip(http));
    }

    @PostMapping("/apple-accounts/sync")
    public List<Dtos.AppleAccountDto> syncAppleAccounts(@AuthenticationPrincipal CurrentUser me,
                                                        HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.syncAppleAccounts(actor.email(), ip(http));
    }

    @PatchMapping("/apple-accounts/{id}")
    public Dtos.AppleAccountDto updateApple(@PathVariable String id,
                                            @RequestBody Dtos.UpdateAppleRequest req,
                                            @AuthenticationPrincipal CurrentUser me,
                                            HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.updateApple(id, req, actor.email(), ip(http));
    }

    /* --------------------------------- 设置 --------------------------------- */

    @GetMapping("/settings")
    public Dtos.SettingsDto settings() {
        return admin.settings();
    }

    @PatchMapping("/settings")
    public Dtos.SettingsDto saveSettings(@RequestBody Dtos.UpdateSettingsRequest req,
                                         @AuthenticationPrincipal CurrentUser me,
                                         HttpServletRequest http) {
        CurrentUser actor = require(me);
        return admin.saveSettings(req, actor.email(), ip(http));
    }

    /* ------------------------------ 存储与清理 ------------------------------ */

    /** 数据卷容量、保留期与最近一次清理结果。 */
    @GetMapping("/maintenance")
    public Dtos.MaintenanceDto maintenance() {
        return maintenance.status();
    }

    /** 立即执行一次清理；磁盘告警时管理员可以手动释放空间。 */
    @PostMapping("/maintenance/cleanup")
    public Dtos.CleanupReportDto cleanup(@AuthenticationPrincipal CurrentUser me, HttpServletRequest http) {
        CurrentUser actor = require(me);
        Dtos.CleanupReportDto report = maintenance.runNow();
        audit.log(actor.email(), "MAINTENANCE_CLEANUP", "storage",
                "产物 " + report.artifactsDeleted() + " 个，上传包 " + report.uploadsDeleted()
                        + " 个，日志 " + report.logsDeleted() + " 条，释放 "
                        + (report.artifactBytesFreed() + report.uploadBytesFreed() + report.orphanBytesFreed()) + " 字节",
                ip(http));
        return report;
    }

    /* --------------------------------- 日志 --------------------------------- */

    @GetMapping("/logs")
    public Dtos.LogPageDto logs(@RequestParam(required = false) String action,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        return admin.logs(action, q, page, size);
    }

    /* -------------------------------- helpers ------------------------------- */

    private static CurrentUser require(CurrentUser me) {
        if (me == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return me;
    }

    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
