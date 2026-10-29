package com.appreseal;

import com.appreseal.config.AppProperties;
import com.appreseal.service.MailGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 登录限流、改密吊销 token、邮箱验证码重置、封号后旧 token 不能复活。 */
class AuthSecurityApiTest extends ApiTestSupport {

    @Autowired
    MailGateway mail;

    @Autowired
    AppProperties props;

    @Test
    void repeatedLoginFailuresLockThatEmail() throws Exception {
        String email = unique("lock");
        postJson("/api/v1/auth/register", null, Map.of(
                "email", email, "password", "secret1234", "nickname", "限流"));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-pass"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("邮箱或密码不正确"));
        }
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-pass"))))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "secret1234"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("尝试次数过多，请稍后再试"));
    }

    @Test
    void changePasswordRevokesPreviousToken() throws Exception {
        String email = unique("pwd");
        var created = postJson("/api/v1/auth/register", null, Map.of(
                "email", email, "password", "secret1234", "nickname", "改密"));
        String oldToken = created.path("token").asText();

        var changed = mvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("oldPassword", "secret1234", "newPassword", "secret5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String fresh = body(changed).path("token").asText();
        assertNotEquals(oldToken, fresh);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "secret1234"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetCodeSignsInAndKillsOldSessions() throws Exception {
        String email = unique("reset");
        String oldToken = postJson("/api/v1/auth/register", null, Map.of(
                "email", email, "password", "secret1234", "nickname", "重置")).path("token").asText();

        String unknown = unique("missing");
        var known = postJson("/api/v1/auth/forgot-password", null, Map.of("email", email));
        var missing = postJson("/api/v1/auth/forgot-password", null, Map.of("email", unknown));
        assertEquals(known.path("message").asText(), missing.path("message").asText());
        assertNull(mail.capturedCode(unknown));
        String code = mail.capturedCode(email);
        assertNotNull(code);

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "code", "000000", "newPassword", "secret9999"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码不正确或已过期"));

        var reset = postJson("/api/v1/auth/reset-password", null, Map.of(
                "email", email, "code", code, "newPassword", "secret9999"));
        String fresh = reset.path("token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void tooManyWrongCodesInvalidateTheReset() throws Exception {
        String email = unique("burn");
        postJson("/api/v1/auth/register", null, Map.of(
                "email", email, "password", "secret1234", "nickname", "烧码"));
        postJson("/api/v1/auth/forgot-password", null, Map.of("email", email));
        String code = mail.capturedCode(email);
        assertNotNull(code);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", email, "code", "111111", "newPassword", "secret9999"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "code", code, "newPassword", "secret9999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unbanDoesNotReviveTokenIssuedBeforeBan() throws Exception {
        String email = unique("ban");
        var created = postJson("/api/v1/auth/register", null, Map.of(
                "email", email, "password", "secret1234", "nickname", "封禁"));
        String token = created.path("token").asText();
        String userId = created.path("user").path("id").asText();
        String admin = adminToken();

        mvc.perform(patch("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "secret1234"))))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordWithoutMailIsUnavailableForEveryone() throws Exception {
        boolean enabled = props.getMail().isEnabled();
        props.getMail().setEnabled(false);
        try {
            mvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", unique("nomail")))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("找回密码暂不可用，请联系管理员"));
        } finally {
            props.getMail().setEnabled(enabled);
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
