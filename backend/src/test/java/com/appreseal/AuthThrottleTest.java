package com.appreseal;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.appreseal.security.ClientIps;
import com.appreseal.service.AuthThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 限流计数和客户端 IP 解析，不启动 Spring。 */
class AuthThrottleTest {

    @Test
    void loginRegisterAndResetLockIndependently() {
        AppProperties props = new AppProperties();
        props.getAuth().setLoginEmailMaxFailures(2);
        props.getAuth().setLoginIpMaxFailures(3);
        props.getAuth().setLoginWindowSeconds(60);
        props.getAuth().setLoginLockSeconds(60);
        props.getAuth().setRegisterMaxPerIp(2);
        props.getAuth().setRegisterWindowSeconds(60);
        props.getAuth().setResetMaxPerEmail(2);
        props.getAuth().setResetMaxPerIp(5);
        props.getAuth().setResetWindowSeconds(60);
        AuthThrottle throttle = new AuthThrottle(props);

        throttle.recordLoginFailure("a@b.c", "10.0.0.1");
        assertThrows(ApiException.class, () -> throttle.recordLoginFailure("a@b.c", "10.0.0.1"));
        assertThrows(ApiException.class, () -> throttle.checkLogin("a@b.c", "10.9.9.9"));

        throttle.clearLoginEmail("a@b.c");
        throttle.checkLogin("a@b.c", "10.9.9.9");

        throttle.acquireRegister("10.1.0.1");
        assertThrows(ApiException.class, () -> throttle.acquireRegister("10.1.0.1"));
        assertThrows(ApiException.class, () -> throttle.acquireRegister("10.1.0.1"));
        throttle.acquireRegister("10.1.0.2");

        throttle.acquireReset("reset@b.c", "10.2.0.1");
        assertThrows(ApiException.class, () -> throttle.acquireReset("reset@b.c", "10.2.0.2"));
        assertThrows(ApiException.class, () -> throttle.acquireReset("reset@b.c", "10.2.0.2"));
    }

    @Test
    void realIpIsTrustedOnlyFromPrivateProxies() {
        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.setRemoteAddr("172.18.0.1");
        proxied.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", ClientIps.of(proxied, true));
        assertEquals("172.18.0.1", ClientIps.of(proxied, false));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("203.0.113.5");
        direct.addHeader("X-Real-IP", "198.51.100.1");
        assertEquals("203.0.113.5", ClientIps.of(direct, true));

        MockHttpServletRequest junk = new MockHttpServletRequest();
        junk.setRemoteAddr("127.0.0.1");
        junk.addHeader("X-Real-IP", "not an ip");
        assertEquals("127.0.0.1", ClientIps.of(junk, true));
    }
}
