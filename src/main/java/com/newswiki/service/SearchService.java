package com.newswiki.service;

import com.newswiki.dto.SearchResults;
import com.newswiki.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final SearchRepository searchRepository;
    private final TimeDisplayService timeDisplayService;

    public SearchResults search(String query) {
        SearchResults results = searchRepository.search(query, 20);
        return new SearchResults(
                results.query(),
                results.wikiPages().stream().map(this::format).toList(),
                results.articles().stream().map(this::format).toList()
        );
    }

    private com.newswiki.dto.SearchResult format(com.newswiki.dto.SearchResult result) {
        return new com.newswiki.dto.SearchResult(
                result.type(),
                result.id(),
                result.title(),
                result.url(),
                result.summary(),
                timeDisplayService.format(result.updatedAt())
        );
    }
}
