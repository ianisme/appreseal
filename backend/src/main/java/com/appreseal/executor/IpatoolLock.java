package com.appreseal.executor;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * ipatool 只有一个当前账号（~/.ipatool/account）。网页登录和任务下载都会改这份状态，
 * 因此必须共用同一把全局公平锁，不能各自加锁。
 */
@Component
public class IpatoolLock {
    private final ReentrantLock lock = new ReentrantLock(true);

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
