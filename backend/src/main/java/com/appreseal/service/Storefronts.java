package com.appreseal.service;

import com.appreseal.common.ApiException;

import java.util.List;
import java.util.Locale;

/** App Store 市场。代码与 iTunes Search API 的 country 参数一致。 */
public final class Storefronts {
    public record Item(String code, String name) {}

    public static final List<Item> ALL = List.of(
            new Item("cn", "中国大陆"),
            new Item("hk", "中国香港"),
            new Item("tw", "中国台湾"),
            new Item("us", "美国"),
            new Item("jp", "日本"),
            new Item("kr", "韩国"),
            new Item("sg", "新加坡"),
            new Item("gb", "英国"),
            new Item("de", "德国"),
            new Item("fr", "法国"),
            new Item("au", "澳大利亚"),
            new Item("ca", "加拿大")
    );

    private Storefronts() {}

    public static String normalize(String raw) {
        String code = raw == null || raw.isBlank() ? "cn" : raw.trim().toLowerCase(Locale.ROOT);
        if (ALL.stream().noneMatch(item -> item.code().equals(code))) {
            throw ApiException.badRequest("不支持的 App Store 市场");
        }
        return code;
    }

    public static String name(String code) {
        return ALL.stream().filter(item -> item.code().equals(code)).map(Item::name).findFirst().orElse(code);
    }
}
