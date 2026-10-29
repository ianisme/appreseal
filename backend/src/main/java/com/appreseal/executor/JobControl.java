package com.appreseal.executor;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录每个任务当前拉起的进程。取消在事务提交后调用 {@link #stop}，
 * 已经启动的进程会被停掉；取消之后才启动的进程也会马上被停掉。
 */
@Component
public class JobControl {
    private final ConcurrentHashMap<String, Set<Process>> live = new ConcurrentHashMap<>();
    private final Set<String> stopping = ConcurrentHashMap.newKeySet();

    /** 调度器开始执行时调用，清掉上一次取消留下的标记。 */
    public void begin(String taskId) {
        stopping.remove(taskId);
        live.putIfAbsent(taskId, ConcurrentHashMap.newKeySet());
    }

    public void watch(String taskId, Process process) {
        if (process == null) {
            return;
        }
        if (stopping.contains(taskId)) {
            ProcessTrees.destroy(process);
            return;
        }
        live.computeIfAbsent(taskId, ignored -> ConcurrentHashMap.newKeySet()).add(process);
        if (stopping.contains(taskId)) {
            ProcessTrees.destroy(process);
        }
    }

    public void unwatch(String taskId, Process process) {
        Set<Process> set = live.get(taskId);
        if (set != null && process != null) {
            set.remove(process);
        }
    }

    public void stop(String taskId) {
        stopping.add(taskId);
        Set<Process> set = live.get(taskId);
        if (set == null) {
            return;
        }
        for (Process process : set) {
            ProcessTrees.destroy(process);
        }
    }

    /** 这次执行结束。重试会再走 {@link #begin}，不会被上一次取消误伤。 */
    public void end(String taskId) {
        stopping.remove(taskId);
        Set<Process> set = live.remove(taskId);
        if (set == null) {
            return;
        }
        for (Process process : set) {
            if (process.isAlive()) {
                ProcessTrees.destroy(process);
            }
        }
    }
}
