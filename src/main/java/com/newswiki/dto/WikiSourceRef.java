package com.newswiki.dto;

public record WikiSourceRef(
        long articleId,
        String providerName,
        String title,
        String canonicalUrl,
        String contributionSummary
) {
}
