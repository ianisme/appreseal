package com.appreseal.common;

import java.util.List;

/** 列表分页：页码从 1 开始，单页最多 100 条。 */
public final class Pages {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Pages() {}

    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int size(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    public static int pageCount(long total, int size) {
        if (total <= 0 || size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    public static <T> List<T> slice(List<T> all, int page, int size) {
        int from = (page - 1) * size;
        if (from >= all.size()) {
            return List.of();
        }
        return List.copyOf(all.subList(from, Math.min(all.size(), from + size)));
    }
}
