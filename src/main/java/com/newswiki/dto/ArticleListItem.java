package com.newswiki.dto;

import java.util.List;

public record ArticleListItem(
        long id,
        String title,
        String publishedAt,
        String summary,
        String durability,
        List<String> chips
) {
}
