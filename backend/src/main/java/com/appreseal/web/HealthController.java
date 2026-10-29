package com.appreseal.web;

import com.appreseal.service.SettingsService;
import com.appreseal.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final SettingsService settings;

    public HealthController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "appreseal-server", "time", Instant.now().toString());
    }

    /** 前台公告与维护开关。不含价格以外的内部配置。 */
    @GetMapping("/site")
    public Dtos.SiteDto site() {
        return Dtos.SiteDto.from(settings.get());
    }
}
