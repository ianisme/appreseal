package com.appreseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadApiTest extends ApiTestSupport {

    @Test
    void uploadEncryptedIpaCreatesTaskAndCharges() throws Exception {
        String token = userToken();

        MockMultipartFile file = new MockMultipartFile("file", "Test.ipa",
                "application/octet-stream", IpaFixtures.ipa(1));
        MvcResult result = mvc.perform(multipart("/api/v1/tasks/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var created = body(result);

        String id = created.path("id").asText();
        assertEquals("UPLOAD", created.path("source").asText());
        assertEquals("com.example.fixture", created.path("bundleId").asText());
        assertEquals("1.2.3", created.path("version").asText());
        assertEquals("QUEUED", created.path("status").asText());
        assertEquals(0, created.path("priceCents").asLong());

        var canceled = postJson("/api/v1/tasks/" + id + "/cancel", token, null);
        assertEquals("CANCELED", canceled.path("status").asText());
    }

    @Test
    void uploadAlreadyDecryptedIpaIsRejectedWithoutCharge() throws Exception {
        String token = userToken();

        MockMultipartFile file = new MockMultipartFile("file", "Decrypted.ipa",
                "application/octet-stream", IpaFixtures.ipa(0));
        MvcResult result = mvc.perform(multipart("/api/v1/tasks/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        String message = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(message.contains("没有可解密的加密段"), message);
    }

    @Test
    void uploadNonIpaIsRejected() throws Exception {
        String token = userToken();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt",
                MediaType.TEXT_PLAIN_VALUE, "hello".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mvc.perform(multipart("/api/v1/tasks/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(".ipa"));
    }
}
