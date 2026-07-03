package com.newswiki.dto;

public record RssSource(
        String sourceKey,
        String providerSlug,
        String name,
        String url,
        String type,
        String category,
        boolean enabled
) {
}
