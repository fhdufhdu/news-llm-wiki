package com.newswiki.dto;

public record SummaryBlock(
        String title,
        String summary,
        int sourceCount,
        String generatedAt
) {
}
