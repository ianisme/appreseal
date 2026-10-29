package com.appreseal.executor;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 停掉一次脱壳拉起的进程，以及它还没退出的子进程。 */
public final class ProcessTrees {
    private ProcessTrees() {}

    public static void destroy(Process process) {
        if (process == null) {
            return;
        }
        try {
            ProcessHandle handle = process.toHandle();
            List<ProcessHandle> children = handle.descendants().toList();
            for (int i = children.size() - 1; i >= 0; i--) {
                children.get(i).destroy();
            }
            handle.destroy();
            for (ProcessHandle child : children) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // 进程已经退出时销毁会失败，忽略即可
        }
    }
}
