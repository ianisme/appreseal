package com.appreseal.web;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.appreseal.security.ClientIps;
import com.appreseal.security.CurrentUser;
import com.appreseal.service.AuthService;
import com.appreseal.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/register")
    public Dtos.AuthResponse register(@Valid @RequestBody Dtos.RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, ip(http));
    }

    @PostMapping("/login")
    public Dtos.AuthResponse login(@Valid @RequestBody Dtos.LoginRequest req, HttpServletRequest http) {
        return auth.login(req, ip(http));
    }

    @PostMapping("/forgot-password")
    public Dtos.SimpleMessage forgotPassword(@Valid @RequestBody Dtos.ForgotPasswordRequest req,
                                             HttpServletRequest http) {
        return auth.forgotPassword(req, ip(http));
    }

    @PostMapping("/reset-password")
    public Dtos.AuthResponse resetPassword(@Valid @RequestBody Dtos.ResetPasswordRequest req,
                                           HttpServletRequest http) {
        return auth.resetPassword(req, ip(http));
    }

    @GetMapping("/me")
    public Dtos.UserDto me(@AuthenticationPrincipal CurrentUser me) {
        return Dtos.UserDto.from(auth.require(require(me).id()));
    }

    @PatchMapping("/profile")
    public Dtos.UserDto profile(@AuthenticationPrincipal CurrentUser me,
                                @Valid @RequestBody Dtos.UpdateProfileRequest req) {
        return auth.updateProfile(require(me).id(), req);
    }

    @PostMapping("/password")
    public Dtos.AuthResponse password(@AuthenticationPrincipal CurrentUser me,
                                      @Valid @RequestBody Dtos.ChangePasswordRequest req,
                                      HttpServletRequest http) {
        return auth.changePassword(require(me).id(), req, ip(http));
    }

    private String ip(HttpServletRequest http) {
        return ClientIps.of(http, props.getAuth().isTrustProxy());
    }

    private static CurrentUser require(CurrentUser me) {
        if (me == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return me;
    }
}
