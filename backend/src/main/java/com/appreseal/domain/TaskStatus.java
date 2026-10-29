package com.appreseal.domain;

public enum TaskStatus {
    QUEUED, DOWNLOADING, DECRYPTING, VERIFYING, SUCCEEDED, FAILED, CANCELED;

    public boolean isRunning() {
        return this == QUEUED || this == DOWNLOADING || this == DECRYPTING || this == VERIFYING;
    }

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }

    public String stage() {
        return switch (this) {
            case QUEUED -> "排队中";
            case DOWNLOADING -> "下载中";
            case DECRYPTING -> "解密中";
            case VERIFYING -> "校验中";
            case SUCCEEDED -> "已完成";
            case FAILED -> "已失败";
            case CANCELED -> "已取消";
        };
    }
}
