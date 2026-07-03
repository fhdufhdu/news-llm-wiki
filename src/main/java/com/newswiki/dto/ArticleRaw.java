package com.newswiki.dto;

import java.time.Instant;

public record ArticleRaw(
        long id,
        long articleId,
        String storageMode,
        String filePath,
        String contentType,
        int httpStatus,
        Instant fetchedAt
) {
}
