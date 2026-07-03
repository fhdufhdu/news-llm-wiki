package com.newswiki.dto;

import java.time.Instant;

public record Article(
        long id,
        String sourceId,
        String canonicalUrl,
        long providerId,
        String providerName,
        String title,
        String feedUrl,
        Instant publishedAt,
        Instant ingestedAt,
        String contentHash,
        String aiStatus,
        int aiRetryCount,
        String lastError
) {
}
