package com.newswiki.dto;

public record ArticleImportItem(
        long id,
        long jobId,
        String inputUrl,
        String canonicalUrl,
        Long articleId,
        String status,
        String errorMessage,
        int attemptCount,
        String createdAt,
        String updatedAt
) {
}
