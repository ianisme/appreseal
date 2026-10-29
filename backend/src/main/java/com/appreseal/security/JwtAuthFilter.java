package com.appreseal.security;

import com.appreseal.config.JwtService;
import com.appreseal.domain.Role;
import com.appreseal.domain.User;
import com.appreseal.domain.UserStatus;
import com.appreseal.repo.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwt.parse(token);
                users.findById(claims.getSubject()).ifPresent(user -> authenticate(user, claims));
            } catch (Exception ignored) {
                // token 无效/过期：不设置认证，由入口点返回 401
            }
        }
        chain.doFilter(req, res);
    }

    private void authenticate(User user, Claims claims) {
        if (user.status != UserStatus.ACTIVE) {
            return;
        }
        // 改密、找回密码、封号会抬高 tokenVersion，旧 token 立刻失效。
        if (jwt.version(claims) != user.tokenVersionValue()) {
            return;
        }
        Role role = user.role;
        CurrentUser principal = new CurrentUser(user.id, user.email, role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
