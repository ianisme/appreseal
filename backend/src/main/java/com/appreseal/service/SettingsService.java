package com.appreseal.service;

import com.appreseal.domain.AppSettings;
import com.appreseal.repo.AppSettingsRepository;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final AppSettingsRepository repo;

    public SettingsService(AppSettingsRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public AppSettings get() {
        AppSettings s = repo.findById(AppSettings.SINGLETON_ID).orElseGet(() -> repo.save(new AppSettings()));
        // 旧库升级时新增列会被填成 0；给保留期兜底，避免误把全部产物当成过期。
        if (s.artifactRetentionDays <= 0 || s.uploadRetentionDays <= 0 || s.logRetentionDays <= 0) {
            s.artifactRetentionDays = s.artifactRetentionDays > 0 ? s.artifactRetentionDays : 30;
            s.uploadRetentionDays = s.uploadRetentionDays > 0 ? s.uploadRetentionDays : 14;
            s.logRetentionDays = s.logRetentionDays > 0 ? s.logRetentionDays : 90;
            s.diskWarnPercent = s.diskWarnPercent > 0 ? s.diskWarnPercent : 80;
            s.autoCleanup = true;
        }
        if (s.diskWarnPercent < 50 || s.diskWarnPercent > 99) {
            s.diskWarnPercent = 80;
        }
        return s;
    }

    @Transactional
    public AppSettings update(Dtos.UpdateSettingsRequest req) {
        AppSettings s = get();
        if (req.decryptPriceCents() != null) s.decryptPriceCents = req.decryptPriceCents();
        if (req.downloadPriceCents() != null) s.downloadPriceCents = req.downloadPriceCents();
        if (req.concurrency() != null) s.concurrency = Math.max(1, Math.min(8, req.concurrency()));
        if (req.maintenance() != null) s.maintenance = req.maintenance();
        if (req.announcement() != null) s.announcement = req.announcement();
        if (req.freeDownloadDays() != null) s.freeDownloadDays = Math.max(0, req.freeDownloadDays());
        if (req.firstDownloadFreeHours() != null) {
            s.firstDownloadFreeHours = Math.max(0, req.firstDownloadFreeHours());
        }
        if (req.artifactRetentionDays() != null) {
            s.artifactRetentionDays = Math.max(1, Math.min(3650, req.artifactRetentionDays()));
        }
        if (req.uploadRetentionDays() != null) {
            s.uploadRetentionDays = Math.max(1, Math.min(365, req.uploadRetentionDays()));
        }
        if (req.logRetentionDays() != null) {
            s.logRetentionDays = Math.max(1, Math.min(3650, req.logRetentionDays()));
        }
        if (req.autoCleanup() != null) s.autoCleanup = req.autoCleanup();
        if (req.diskWarnPercent() != null) {
            s.diskWarnPercent = Math.max(50, Math.min(99, req.diskWarnPercent()));
        }
        return repo.save(s);
    }
}
