package com.newswiki.dto;

import java.time.Instant;

public record ArticleNote(
        long articleId,
        String shortSummary,
        String durableKnowledgeJson,
        String durability,
        Long generatedByJobId,
        Instant generatedAt
) {
}
