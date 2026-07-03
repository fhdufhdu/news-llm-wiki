package com.newswiki.dto;

public record Section(
        long id,
        String slug,
        String title,
        String description,
        int displayOrder,
        boolean enabled
) {
}
