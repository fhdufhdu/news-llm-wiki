package com.newswiki.dto;

public record Entity(
        long id,
        String slug,
        String name,
        String entityType,
        String summary,
        String updatedAt
) {
}
