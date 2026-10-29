package com.appreseal.executor;

/** 一次脱壳执行：由 {@link TaskDispatcher} 在后台线程调用。 */
public interface DecryptJob {
    void run(String taskId);
}
