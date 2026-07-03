package com.newswiki.dto;

import java.util.List;

public record HomeView(
        String title,
        String generatedAt,
        int sourceCount,
        String summary,
        List<ArticleListItem> majorArticles
) {
}
