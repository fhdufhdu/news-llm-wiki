package com.newswiki.dto;

import java.util.List;

public record ArticleListItem(
        long id,
        String title,
        String provider,
        String publishedAt,
        String summary,
        String durability,
        List<String> chips
) {
}
