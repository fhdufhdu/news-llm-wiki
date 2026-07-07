package com.newswiki.service;

import com.newswiki.dto.SearchResults;
import com.newswiki.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final SearchRepository searchRepository;

    public SearchResults search(String query) {
        return searchRepository.search(query, 20);
    }
}
