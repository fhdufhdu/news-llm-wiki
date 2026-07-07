package com.newswiki.dto;

public record WikiSourceRef(
        long articleId,
        String title,
        String canonicalUrl,
        String contributionSummary
) {
}
