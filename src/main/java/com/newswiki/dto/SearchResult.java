package com.newswiki.dto;

public record SearchResult(
        String type,
        long id,
        String title,
        String url,
        String summary,
        String updatedAt
) {
}
