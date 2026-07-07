package com.newswiki.dto;

import java.util.List;

public record TodaySummary(
        String date,
        int articleCount,
        int wikiPageCount,
        String summary,
        List<WikiPageListItem> pages
) {
}
