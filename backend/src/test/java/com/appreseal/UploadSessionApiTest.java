package com.appreseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 分片上传：中断后按偏移续传，收齐再校验建单。 */
class UploadSessionApiTest extends ApiTestSupport {

    @Test
    void chunkedUploadResumesAndCreatesTask() throws Exception {
        String token = userToken();
        byte[] ipa = IpaFixtures.ipa(1);
        int split = ipa.length / 2;

        var created = postJson("/api/v1/uploads", token, Map.of("filename", "Resume.ipa", "size", ipa.length));
        String id = created.path("id").asText();
        assertEquals(0, created.path("received").asLong());
        assertTrue(created.path("chunkSize").asInt() > 0);

        var first = mvc.perform(put("/api/v1/uploads/" + id)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Upload-Offset", 0)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(java.util.Arrays.copyOf(ipa, split)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(split, body(first).path("received").asLong());

        mvc.perform(put("/api/v1/uploads/" + id)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Upload-Offset", 0)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {1, 2, 3}))
                .andExpect(status().isConflict());

        var resumed = mvc.perform(put("/api/v1/uploads/" + id)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Upload-Offset", split)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(java.util.Arrays.copyOfRange(ipa, split, ipa.length)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(ipa.length, body(resumed).path("received").asLong());

        var task = postJson("/api/v1/uploads/" + id + "/complete", token, null);
        assertEquals("UPLOAD", task.path("source").asText());
        assertEquals("com.example.fixture", task.path("bundleId").asText());
        assertEquals(0, task.path("priceCents").asLong());

        mvc.perform(get("/api/v1/uploads/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUserCannotSeeUpload() throws Exception {
        String owner = userToken();
        byte[] ipa = IpaFixtures.ipa(1);
        String id = postJson("/api/v1/uploads", owner, Map.of("filename", "Mine.ipa", "size", ipa.length))
                .path("id").asText();

        String other = postJson("/api/v1/auth/register", null, Map.of(
                "email", "upload-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "password", "secret1234", "nickname", "别人")).path("token").asText();
        assertNotEquals(owner, other);
        mvc.perform(get("/api/v1/uploads/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void incompleteUploadCannotCreateTask() throws Exception {
        String token = userToken();
        byte[] ipa = IpaFixtures.ipa(1);
        String id = postJson("/api/v1/uploads", token, Map.of("filename", "Half.ipa", "size", ipa.length))
                .path("id").asText();
        mvc.perform(post("/api/v1/uploads/" + id + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
