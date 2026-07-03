package com.newswiki.dto;

public record WikiPageListItem(
        long id,
        String slug,
        String title,
        String summary,
        int importance,
        String updatedAt
) {
}
