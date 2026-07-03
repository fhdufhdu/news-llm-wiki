package com.newswiki.dto;

public record Provider(
        long id,
        String slug,
        String name,
        String homepageUrl,
        String description,
        int displayOrder,
        boolean enabled
) {
}
