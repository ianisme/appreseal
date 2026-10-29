package com.appreseal.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 可读的短 ID：t_1a2b3c4d（与前端 mock 的命名风格一致）。 */
public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private Ids() {}

    public static String of(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append('_');
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** seed 数据用固定时间：当前时间往前推 n 分钟。 */
    public static Instant minutesAgo(long minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES);
    }

    public static Instant daysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
