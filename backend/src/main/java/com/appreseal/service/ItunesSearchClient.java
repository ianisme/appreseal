package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 苹果官方 iTunes Search / Lookup API。测试里可关闭，避免打外网。 */
@Component
public class ItunesSearchClient {
    private static final Logger log = LoggerFactory.getLogger(ItunesSearchClient.class);
    private static final String BASE = "https://itunes.apple.com";

    private final boolean enabled;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Duration timeout;

    public ItunesSearchClient(AppProperties props, ObjectMapper json) {
        AppProperties.Itunes itunes = props.getItunes();
        this.enabled = itunes.isEnabled();
        this.json = json;
        int seconds = Math.max(3, itunes.getTimeoutSeconds());
        this.timeout = Duration.ofSeconds(seconds);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        String proxy = itunes.getProxy();
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy.trim());
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), port)));
        }
        this.http = builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Hit> search(String term, String country) {
        if (!enabled || term == null || term.isBlank()) {
            return List.of();
        }
        String q = term.trim();
        LinkedHashMap<String, Hit> merged = new LinkedHashMap<>();
        try {
            if (q.matches("\\d{5,12}")) {
                add(merged, fetch("/lookup?id=" + q + "&country=" + country));
            } else if (q.contains(".") && !q.contains(" ")) {
                add(merged, fetch("/lookup?bundleId=" + enc(q) + "&country=" + country));
            }
            add(merged, fetch("/search?term=" + enc(q) + "&entity=software&limit=12&country=" + country));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("App Store 搜索失败 country={}: {}", country, e.getMessage());
            throw ApiException.badRequest("App Store 搜索暂时不可用");
        }
        return merged.values().stream().limit(12).toList();
    }

    public record Hit(String bundleId, String name, String developer, String version, String iconUrl, Long trackId) {}

    public static List<Hit> parse(String body, ObjectMapper json) throws Exception {
        JsonNode root = json.readTree(body);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (JsonNode node : results) {
            String bundleId = text(node, "bundleId");
            String name = text(node, "trackName");
            if (bundleId.isBlank() || name.isBlank()) {
                continue;
            }
            String developer = text(node, "artistName");
            if (developer.isBlank()) {
                developer = text(node, "sellerName");
            }
            String version = text(node, "version");
            String icon = text(node, "artworkUrl100");
            if (icon.isBlank()) {
                icon = text(node, "artworkUrl60");
            }
            Long trackId = node.path("trackId").isNumber() ? node.path("trackId").asLong() : null;
            hits.add(new Hit(bundleId, name, developer, version.isBlank() ? "latest" : version, icon, trackId));
        }
        return hits;
    }

    private void add(LinkedHashMap<String, Hit> merged, List<Hit> hits) {
        for (Hit hit : hits) {
            merged.putIfAbsent(hit.bundleId(), hit);
        }
    }

    private List<Hit> fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "AppReseal/1.0")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw ApiException.badRequest("App Store 搜索暂时不可用");
        }
        return parse(response.body(), json);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }
}
