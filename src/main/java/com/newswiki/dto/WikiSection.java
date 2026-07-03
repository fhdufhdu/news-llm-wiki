package com.newswiki.dto;

public record WikiSection(
        long id,
        String slug,
        String title,
        String summary,
        int displayOrder
) {
}
