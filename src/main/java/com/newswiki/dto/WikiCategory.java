package com.newswiki.dto;

public record WikiCategory(
        long id,
        String slug,
        String title,
        String summary,
        int displayOrder
) {
}
