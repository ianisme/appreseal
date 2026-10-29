package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录、注册、找回密码的滑动窗口限流。计数只在本进程内，和 {@code IpatoolLock} 一样，
 * 扩到多个 backend 副本后需要换成共享存储。
 */
@Service
public class AuthThrottle {
    private static final Logger log = LoggerFactory.getLogger(AuthThrottle.class);
    private static final String LOGIN_LOCKED = "尝试次数过多，请稍后再试";
    private static final String REGISTER_LIMITED = "注册过于频繁，请稍后再试";
    private static final String RESET_LIMITED = "请求过于频繁，请稍后再试";

    private final AppProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthThrottle(AppProperties props) {
        this.props = props;
    }

    public void checkLogin(String email, String ip) {
        if (locked("login:email:" + email) || locked("login:ip:" + ip)) {
            throw ApiException.tooManyRequests(LOGIN_LOCKED);
        }
    }

    /** 记一次失败。刚跨过阈值时直接拒绝，避免调用方再返回「密码不正确」。 */
    public void recordLoginFailure(String email, String ip) {
        AppProperties.Auth cfg = props.getAuth();
        boolean emailLocked = hit("login:email:" + email, cfg.getLoginEmailMaxFailures(),
                cfg.getLoginWindowSeconds(), cfg.getLoginLockSeconds());
        boolean ipLocked = hit("login:ip:" + ip, cfg.getLoginIpMaxFailures(),
                cfg.getLoginWindowSeconds(), cfg.getLoginLockSeconds());
        if (emailLocked || ipLocked) {
            throw ApiException.tooManyRequests(LOGIN_LOCKED);
        }
    }

    public void clearLoginEmail(String email) {
        buckets.remove("login:email:" + email);
    }

    public void acquireRegister(String ip) {
        AppProperties.Auth cfg = props.getAuth();
        String key = "register:ip:" + ip;
        hit(key, cfg.getRegisterMaxPerIp(), cfg.getRegisterWindowSeconds(), cfg.getRegisterWindowSeconds());
        if (locked(key)) {
            throw ApiException.tooManyRequests(REGISTER_LIMITED);
        }
    }

    /** 发码请求：未知邮箱也计数，避免用 429/200 的差别探测账号是否存在。 */
    public void acquireReset(String email, String ip) {
        AppProperties.Auth cfg = props.getAuth();
        String emailKey = "reset:email:" + email;
        String ipKey = "reset:ip:" + ip;
        hit(emailKey, cfg.getResetMaxPerEmail(), cfg.getResetWindowSeconds(), cfg.getResetWindowSeconds());
        hit(ipKey, cfg.getResetMaxPerIp(), cfg.getResetWindowSeconds(), cfg.getResetWindowSeconds());
        if (locked(emailKey) || locked(ipKey)) {
            throw ApiException.tooManyRequests(RESET_LIMITED);
        }
    }

    /* ------------------------------- 内部实现 ------------------------------- */

    private boolean locked(String key) {
        Bucket bucket = buckets.get(key);
        return bucket != null && bucket.locked(System.currentTimeMillis());
    }

    /** @return 这次命中是否刚刚触发锁定 */
    private boolean hit(String key, int max, long windowSeconds, long lockSeconds) {
        int limit = Math.max(1, max);
        long windowMs = Math.max(1, windowSeconds) * 1000L;
        long lockMs = Math.max(1, lockSeconds) * 1000L;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        boolean newlyLocked = bucket.hit(System.currentTimeMillis(), windowMs, limit, lockMs);
        if (newlyLocked) {
            log.warn("认证限流已触发：{}，锁定 {} 秒", key, lockSeconds);
        }
        prune();
        return newlyLocked;
    }

    private void prune() {
        if (buckets.size() < 4000) {
            return;
        }
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().idle(now));
    }

    static final class Bucket {
        private final Deque<Long> hits = new ArrayDeque<>();
        private long lockedUntil;

        synchronized boolean hit(long now, long windowMs, int max, long lockMs) {
            if (lockedUntil > now) {
                return false;
            }
            prune(now, windowMs);
            hits.add(now);
            if (hits.size() >= max) {
                lockedUntil = now + lockMs;
                hits.clear();
                return true;
            }
            return false;
        }

        synchronized boolean locked(long now) {
            return lockedUntil > now;
        }

        synchronized boolean idle(long now) {
            return lockedUntil <= now && hits.isEmpty();
        }

        private void prune(long now, long windowMs) {
            while (!hits.isEmpty() && hits.peekFirst() <= now - windowMs) {
                hits.removeFirst();
            }
        }
    }
}
