package com.appreseal.service;

import com.appreseal.common.Ids;
import com.appreseal.domain.AuditLog;
import com.appreseal.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void log(String actor, String action, String target, String detail, String ip) {
        AuditLog l = new AuditLog();
        l.id = Ids.of("al");
        l.actor = actor == null || actor.isBlank() ? "系统" : actor;
        l.action = action;
        l.target = target == null ? "-" : target;
        l.detail = detail == null ? "" : detail;
        l.ip = ip == null || ip.isBlank() ? "-" : ip;
        logs.save(l);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> list(String action) {
        return action == null || action.isBlank() || "ALL".equals(action)
                ? logs.findAllByOrderByCreatedAtDesc()
                : logs.findByActionOrderByCreatedAtDesc(action);
    }
}
