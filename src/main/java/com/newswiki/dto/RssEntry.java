package com.newswiki.dto;

public record RssEntry(
        String title,
        String url,
        String guid,
        String published,
        String summary
) {
}
