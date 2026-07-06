package com.newswiki.dto;

public record ArticleImportJob(
        long id,
        String status,
        int totalCount,
        int fetchedCount,
        int wikiDoneCount,
        int failedCount,
        String createdAt,
        String startedAt,
        String finishedAt,
        String lastMessage
) {
}
