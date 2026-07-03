package com.newswiki.dto;

public record SectionNavItem(
        String slug,
        String title,
        boolean active
) {
}
