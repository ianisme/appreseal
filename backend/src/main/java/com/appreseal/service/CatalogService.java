package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.domain.*;
import com.appreseal.repo.AppleAccountRepository;
import com.appreseal.repo.AppRepository;
import com.appreseal.repo.LibraryRepository;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogService {
    private final AppRepository apps;
    private final LibraryRepository library;
    private final SettingsService settings;
    private final ItunesSearchClient itunes;
    private final AppleAccountRepository appleAccounts;

    public CatalogService(AppRepository apps, LibraryRepository library,
                          SettingsService settings, ItunesSearchClient itunes, AppleAccountRepository appleAccounts) {
        this.apps = apps;
        this.library = library;
        this.settings = settings;
        this.itunes = itunes;
        this.appleAccounts = appleAccounts;
    }

    @Transactional(readOnly = true)
    public List<Dtos.AppDto> list(String q, String category, String sort) {
        List<AppItem> items;
        if (q != null && !q.isBlank()) {
            items = apps.searchOnSale(q.trim());
        } else if ("downloads".equals(sort)) {
            items = apps.findByStatusOrderByDownloadsDesc(AppStatus.ON_SALE);
        } else {
            items = apps.findByStatusOrderByUpdatedAtDesc(AppStatus.ON_SALE);
        }
        List<AppItem> filtered = items.stream()
                .filter(a -> category == null || category.isBlank() || "全部".equals(category) || category.equals(a.category))
                .toList();
        if ("price".equals(sort)) {
            filtered = filtered.stream()
                    .sorted(Comparator.comparingLong(a -> a.priceDownloadCents))
                    .toList();
        } else if ("downloads".equals(sort)) {
            filtered = filtered.stream()
                    .sorted(Comparator.comparingLong((AppItem a) -> a.downloads).reversed())
                    .toList();
        }
        return filtered.stream().map(Dtos.AppDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Dtos.AppDto get(String bundleId) {
        return Dtos.AppDto.from(apps.findByBundleId(bundleId)
                .orElseThrow(() -> ApiException.notFound("应用不存在或已下架")));
    }

    @Transactional(readOnly = true)
    public List<Dtos.StoreMarket> openMarkets() {
        Map<String, Long> counts = appleAccounts.findAll().stream()
                .filter(a -> a.status != AppleStatus.DISABLED)
                .map(a -> a.storefront)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.groupingBy(code -> code, Collectors.counting()));
        return Storefronts.ALL.stream()
                .filter(item -> counts.containsKey(item.code()))
                .map(item -> new Dtos.StoreMarket(item.code(), item.name(), counts.get(item.code()).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Dtos.StoreSearchResult search(String q, String country, String userId) {
        String storefront = Storefronts.normalize(country);
        int matched = (int) appleAccounts.findAll().stream()
                .filter(a -> a.status != AppleStatus.DISABLED)
                .filter(a -> storefront.equals(a.storefront))
                .count();
        if (q == null || q.isBlank()) {
            return new Dtos.StoreSearchResult(storefront, Storefronts.name(storefront), matched, List.of());
        }
        List<Dtos.StoreHit> hits = itunes.isEnabled()
                ? itunes.search(q.trim(), storefront).stream().map(hit -> toHit(hit, userId)).toList()
                : apps.searchOnSale(q.trim()).stream().limit(8).map(a -> localHit(a, userId)).toList();
        return new Dtos.StoreSearchResult(storefront, Storefronts.name(storefront), matched, hits);
    }

    private Dtos.StoreHit toHit(ItunesSearchClient.Hit hit, String userId) {
        return new Dtos.StoreHit(hit.bundleId(), hit.name(), hit.developer(), hit.version(),
                iconText(hit.name()), iconHue(hit.bundleId()),
                library.existsByUserIdAndBundleId(userId, hit.bundleId()),
                hit.iconUrl() == null || hit.iconUrl().isBlank() ? null : hit.iconUrl(), hit.trackId());
    }

    private Dtos.StoreHit localHit(AppItem app, String userId) {
        return new Dtos.StoreHit(app.bundleId, app.name, app.developer, app.version, app.iconText, app.iconHue,
                library.existsByUserIdAndBundleId(userId, app.bundleId), null, null);
    }

    private static String iconText(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "App";
        return trimmed.codePointCount(0, trimmed.length()) == 1
                ? trimmed
                : trimmed.substring(0, trimmed.offsetByCodePoints(0, Math.min(2, trimmed.codePointCount(0, trimmed.length()))));
    }

    private static int iconHue(String bundleId) {
        int hash = bundleId == null ? 210 : Math.abs(bundleId.hashCode());
        return hash % 360;
    }

    /** 团队成员下载成品：不扣费。已在库里的直接返回，否则记一条可重复下载的记录。 */
    @Transactional
    public Dtos.DownloadResult download(String bundleId, String userId) {
        Instant now = Instant.now();
        AppItem app = apps.findByBundleId(bundleId)
                .filter(a -> a.status == AppStatus.ON_SALE)
                .orElseThrow(() -> ApiException.badRequest("应用不存在或已下架"));
        var existing = library.findFirstByUserIdAndBundleIdOrderByAcquiredAtDesc(userId, bundleId);
        if (existing.isPresent()) {
            return new Dtos.DownloadResult(Dtos.AppDto.from(app), 0, existing.get().id);
        }

        app.downloads += 1;
        apps.save(app);

        LibraryItem item = new LibraryItem();
        item.id = Ids.of("lib");
        item.userId = userId;
        item.appId = app.id;
        item.bundleId = app.bundleId;
        item.name = app.name;
        item.version = app.version;
        item.source = LibrarySource.PURCHASE;
        item.acquiredAt = now;
        item.freeUntil = now.plus(Duration.ofDays(3650));
        library.save(item);

        return new Dtos.DownloadResult(Dtos.AppDto.from(app), 0, item.id);
    }

    @Transactional(readOnly = true)
    public List<Dtos.LibraryDto> library(String userId) {
        return library.findByUserIdOrderByAcquiredAtDesc(userId).stream().map(Dtos.LibraryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AppItem findApp(String bundleId) {
        return apps.findByBundleId(bundleId).orElse(null);
    }

    @Transactional(readOnly = true)
    public AppItem findAppOrThrow(String bundleId) {
        return apps.findByBundleId(bundleId)
                .orElseThrow(() -> ApiException.badRequest("应用不存在，无法脱壳"));
    }

    @Transactional(readOnly = true)
    public List<AppItem> allForAdmin() {
        return apps.findAll().stream()
                .sorted(Comparator.comparing((AppItem a) -> a.updatedAt).reversed())
                .toList();
    }

    @Transactional
    public AppItem saveApp(Dtos.SaveAppRequest req) {
        AppItem app;
        if (req.id() != null && !req.id().isBlank()) {
            app = apps.findById(req.id()).orElseThrow(() -> ApiException.notFound("应用不存在"));
        } else {
            if (req.bundleId() == null || req.bundleId().isBlank()) {
                throw ApiException.badRequest("缺少 bundle id");
            }
            apps.findByBundleId(req.bundleId().trim()).ifPresent(a -> {
                throw ApiException.conflict("该 bundle id 已存在");
            });
            app = new AppItem();
            app.id = Ids.of("app");
            app.bundleId = req.bundleId().trim();
            app.downloads = 0;
        }
        AppSettings s = settings.get();
        if (req.bundleId() != null && !req.bundleId().isBlank()) app.bundleId = req.bundleId().trim();
        if (req.name() != null) app.name = req.name();
        if (req.developer() != null) app.developer = req.developer();
        if (req.category() != null) app.category = req.category();
        if (req.iconText() != null) app.iconText = req.iconText();
        if (req.iconHue() != null) app.iconHue = req.iconHue();
        if (req.version() != null) app.version = req.version();
        if (req.sizeBytes() != null) app.sizeBytes = req.sizeBytes();
        if (req.minIOS() != null) app.minIOS = req.minIOS();
        if (req.executable() != null) app.executable = req.executable();
        if (req.sha256() != null) app.sha256 = req.sha256();
        if (req.priceDownloadCents() != null) app.priceDownloadCents = req.priceDownloadCents();
        if (req.priceDecryptCents() != null) app.priceDecryptCents = req.priceDecryptCents();
        if (req.status() != null) app.status = req.status();
        if (req.verified() != null) app.verified = req.verified();
        if (req.arch() != null && !req.arch().isEmpty()) app.arch = String.join(",", req.arch());
        if (req.description() != null) app.description = req.description();
        if (app.name == null || app.name.isBlank()) app.name = app.bundleId;
        if (app.iconText == null || app.iconText.isBlank()) {
            app.iconText = app.name.substring(0, Math.min(2, app.name.length()));
        }
        if (app.priceDownloadCents <= 0) app.priceDownloadCents = s.downloadPriceCents;
        if (app.priceDecryptCents <= 0) app.priceDecryptCents = s.decryptPriceCents;
        if (app.sha256 == null || app.sha256.isBlank()) app.sha256 = Ids.of("sha");
        app.updatedAt = Instant.now();
        return apps.save(app);
    }

    @Transactional
    public void setShelf(String id, AppStatus status) {
        AppItem app = apps.findById(id).orElseThrow(() -> ApiException.notFound("应用不存在"));
        app.status = status;
        app.updatedAt = Instant.now();
        apps.save(app);
    }

    @Transactional
    public void setStatusByBundle(String bundleId, AppStatus status) {
        apps.findByBundleId(bundleId).ifPresent(a -> {
            a.status = status;
            a.updatedAt = Instant.now();
            apps.save(a);
        });
    }

    @Transactional(readOnly = true)
    public boolean isKnown(String bundleId) {
        return apps.findByBundleId(bundleId).isPresent();
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
