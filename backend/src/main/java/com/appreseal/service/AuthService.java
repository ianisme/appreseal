package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.config.AppProperties;
import com.appreseal.config.JwtService;
import com.appreseal.domain.Role;
import com.appreseal.domain.User;
import com.appreseal.domain.UserStatus;
import com.appreseal.repo.UserRepository;
import com.appreseal.web.dto.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String RESET_SENT = "如果该邮箱已注册，验证码已发送，请查收邮件";
    private static final String RESET_INVALID = "验证码不正确或已过期";

    private final UserRepository users;
    private final WalletService wallets;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthThrottle throttle;
    private final ResetCodes codes;
    private final MailGateway mail;
    private final AuditService audit;
    private final AppProperties props;

    public AuthService(UserRepository users, WalletService wallets, PasswordEncoder encoder, JwtService jwt,
                       AuthThrottle throttle, ResetCodes codes, MailGateway mail, AuditService audit,
                       AppProperties props) {
        this.users = users;
        this.wallets = wallets;
        this.encoder = encoder;
        this.jwt = jwt;
        this.throttle = throttle;
        this.codes = codes;
        this.mail = mail;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public Dtos.AuthResponse register(Dtos.RegisterRequest req, String ip) {
        throttle.acquireRegister(ip);
        String email = normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("该邮箱已注册");
        }
        User u = new User();
        u.id = Ids.of("u");
        u.email = email;
        u.passwordHash = encoder.encode(req.password());
        u.nickname = req.nickname() == null || req.nickname().isBlank()
                ? email.split("@")[0]
                : req.nickname().trim();
        u.role = Role.USER;
        u.status = UserStatus.ACTIVE;
        u.lastLoginAt = Instant.now();
        users.save(u);
        wallets.ensure(u.id);
        return session(u);
    }

    @Transactional
    public Dtos.AuthResponse login(Dtos.LoginRequest req, String ip) {
        String email = normalize(req.email());
        throttle.checkLogin(email, ip);
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.passwordHash)) {
            throttle.recordLoginFailure(email, ip);
            throw ApiException.badRequest("邮箱或密码不正确");
        }
        if (u.status == UserStatus.BANNED) {
            throw ApiException.forbidden("账号已被停用，请联系管理员");
        }
        throttle.clearLoginEmail(email);
        u.lastLoginAt = Instant.now();
        users.save(u);
        wallets.ensure(u.id);
        return session(u);
    }

    @Transactional(readOnly = true)
    public User require(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("登录已过期，请重新登录"));
        if (u.status != UserStatus.ACTIVE) {
            throw ApiException.forbidden("账号已被停用，请联系管理员");
        }
        return u;
    }

    @Transactional
    public Dtos.UserDto updateProfile(String userId, Dtos.UpdateProfileRequest req) {
        User u = require(userId);
        if (req.nickname() != null) {
            String nickname = req.nickname().trim();
            if (nickname.isEmpty()) {
                throw ApiException.badRequest("昵称不能为空");
            }
            u.nickname = nickname;
        }
        if (req.avatarColor() != null && req.avatarColor().matches("^#[0-9a-fA-F]{6}$")) {
            u.avatarColor = req.avatarColor();
        }
        return Dtos.UserDto.from(users.save(u));
    }

    /** 改密后抬高 token 版本，并给当前会话换一张新 token。其他设备的旧 token 立即失效。 */
    @Transactional
    public Dtos.AuthResponse changePassword(String userId, Dtos.ChangePasswordRequest req, String ip) {
        User u = require(userId);
        if (!encoder.matches(req.oldPassword(), u.passwordHash)) {
            throw ApiException.badRequest("当前密码不正确");
        }
        if (encoder.matches(req.newPassword(), u.passwordHash)) {
            throw ApiException.badRequest("新密码不能与当前密码相同");
        }
        u.passwordHash = encoder.encode(req.newPassword());
        u.bumpTokenVersion();
        users.save(u);
        audit.log(u.email, "PASSWORD_CHANGE", u.email, "已更新密码并吊销其他登录", ip);
        return session(u);
    }

    /**
     * 无论邮箱是否存在都返回同一句话，避免被用来探测注册情况。
     * 邮件没配好时对所有请求返回 503，同样不区分邮箱。
     */
    @Transactional
    public Dtos.SimpleMessage forgotPassword(Dtos.ForgotPasswordRequest req, String ip) {
        String email = normalize(req.email());
        throttle.acquireReset(email, ip);
        if (!mail.canSend()) {
            throw ApiException.unavailable("找回密码暂不可用，请联系管理员");
        }
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u != null && u.status == UserStatus.ACTIVE) {
            String code = codes.generate();
            u.resetCodeHash = codes.hash(u.id, code);
            u.resetCodeExpiresAt = Instant.now().plusSeconds(Math.max(60, props.getAuth().getResetCodeTtlSeconds()));
            u.resetCodeAttempts = 0;
            users.save(u);
            try {
                mail.sendResetCode(u.email, code, Math.max(1, props.getAuth().getResetCodeTtlSeconds() / 60));
            } catch (RuntimeException e) {
                log.warn("重置邮件发送失败：{}", e.getMessage());
                u.clearResetCode();
                users.save(u);
            }
        }
        return new Dtos.SimpleMessage(RESET_SENT);
    }

    /** 错误验证码也要记下次数；ApiException 不能把这次计数回滚掉。 */
    @Transactional(noRollbackFor = ApiException.class)
    public Dtos.AuthResponse resetPassword(Dtos.ResetPasswordRequest req, String ip) {
        String email = normalize(req.email());
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (!resetValid(u, req.code())) {
            if (u != null && u.status == UserStatus.ACTIVE) {
                noteBadCode(u);
            } else {
                codes.matches("none", req.code(), null);
            }
            throw ApiException.badRequest(RESET_INVALID);
        }
        u.passwordHash = encoder.encode(req.newPassword());
        u.bumpTokenVersion();
        u.clearResetCode();
        u.lastLoginAt = Instant.now();
        users.save(u);
        wallets.ensure(u.id);
        audit.log(u.email, "PASSWORD_RESET", u.email, "通过邮箱验证码重置密码", ip);
        return session(u);
    }

    /* ------------------------------- 内部实现 ------------------------------- */

    private boolean resetValid(User u, String code) {
        if (u == null || u.status != UserStatus.ACTIVE || u.resetCodeHash == null || u.resetCodeExpiresAt == null) {
            return false;
        }
        if (!u.resetCodeExpiresAt.isAfter(Instant.now()) || u.resetAttempts() >= maxAttempts()) {
            return false;
        }
        return codes.matches(u.id, code, u.resetCodeHash);
    }

    private void noteBadCode(User u) {
        if (u.resetCodeHash == null) {
            return;
        }
        boolean expired = u.resetCodeExpiresAt == null || !u.resetCodeExpiresAt.isAfter(Instant.now());
        int attempts = u.resetAttempts() + 1;
        u.resetCodeAttempts = attempts;
        if (expired || attempts >= maxAttempts()) {
            u.clearResetCode();
        }
        users.save(u);
    }

    private int maxAttempts() {
        return Math.max(1, props.getAuth().getResetMaxAttempts());
    }

    private Dtos.AuthResponse session(User u) {
        return new Dtos.AuthResponse(jwt.issue(u), Dtos.UserDto.from(u));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
