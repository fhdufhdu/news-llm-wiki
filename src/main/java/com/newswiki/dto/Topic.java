package com.newswiki.dto;

public record Topic(
        long id,
        String slug,
        String title,
        String summary,
        String updatedAt
) {
}
