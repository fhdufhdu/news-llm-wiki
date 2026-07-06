package com.newswiki.dto;

import java.util.List;

public record ArticleImportProgress(
        ArticleImportJob job,
        List<ArticleImportItem> items
) {
}
