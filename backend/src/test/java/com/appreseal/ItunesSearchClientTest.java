package com.appreseal;

import com.appreseal.service.ItunesSearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItunesSearchClientTest {

    @Test
    void parsesOfficialSoftwareResult() throws Exception {
        String body = """
                {"resultCount":1,"results":[
                  {"wrapperType":"software","kind":"software","trackId":1001,
                   "trackName":"青笺","bundleId":"com.example.qingjian","artistName":"QingYu",
                   "version":"1.4.2","artworkUrl100":"https://example/icon.jpg"},
                  {"wrapperType":"artist","artistName":"QingYu"}
                ]}
                """;
        var hits = ItunesSearchClient.parse(body, new ObjectMapper());
        assertEquals(1, hits.size());
        assertEquals("com.example.qingjian", hits.get(0).bundleId());
        assertEquals("青笺", hits.get(0).name());
        assertEquals(1001L, hits.get(0).trackId());
        assertTrue(hits.get(0).iconUrl().contains("icon.jpg"));
    }
}
