package com.newswiki.dto;

import java.util.List;

public record SearchResults(
        String query,
        List<SearchResult> wikiPages,
        List<SearchResult> articles
) {
}
