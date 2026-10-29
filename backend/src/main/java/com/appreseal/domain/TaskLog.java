package com.appreseal.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_logs", indexes = @Index(name = "idx_logs_task", columnList = "taskId,seq"))
public class TaskLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 40)
    public String taskId;

    @Column(nullable = false)
    public int seq;

    @Column(nullable = false)
    public Instant at = Instant.now();

    /** INFO / OK / WARN / ERROR */
    @Column(nullable = false, length = 8)
    public String level = "INFO";

    @Column(nullable = false, length = 500)
    public String message;

    /** true = 只给管理员看的详细日志（路径、账号、脚本名等）。 */
    @Column(nullable = false)
    public boolean internal = false;

    public TaskLog() {}

    public TaskLog(String taskId, int seq, String level, String message) {
        this(taskId, seq, level, message, false);
    }

    public TaskLog(String taskId, int seq, String level, String message, boolean internal) {
        this.taskId = taskId;
        this.seq = seq;
        this.level = level;
        this.message = message;
        this.internal = internal;
    }
}
