package com.newswiki.dto;

public record CategoryNavItem(
        String slug,
        String title,
        boolean active
) {
}
