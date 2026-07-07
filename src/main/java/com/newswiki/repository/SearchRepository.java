package com.newswiki.repository;

import com.newswiki.dto.SearchResult;
import com.newswiki.dto.SearchResults;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SearchRepository {
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public SearchResults search(String query, int limit) {
        String ftsQuery = toFtsQuery(query);
        if (ftsQuery.isBlank()) {
            return new SearchResults(query == null ? "" : query, List.of(), List.of());
        }
        return new SearchResults(
                query,
                searchWikiPages(ftsQuery, limit),
                searchArticles(ftsQuery, limit)
        );
    }

    private List<SearchResult> searchWikiPages(String ftsQuery, int limit) {
        return entityManager.createNativeQuery("""
                select 'wiki' as type, p.id, p.title, '/wiki/' || p.slug as url, p.summary, p.updated_at
                  from wiki_page_fts f
                  join wiki_pages p on p.id = f.rowid
                 where wiki_page_fts match :query
                   and p.status = 'ACTIVE'
                 order by bm25(wiki_page_fts), p.updated_at desc
                 limit :limit
                """)
                .setParameter("query", ftsQuery)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::result)
                .toList();
    }

    private List<SearchResult> searchArticles(String ftsQuery, int limit) {
        return entityManager.createNativeQuery("""
                select 'article' as type, a.id, a.title, '/articles/' || a.id as url,
                       '원문 URL: ' || a.canonical_url as summary,
                       coalesce(a.published_at, a.ingested_at)
                  from article_fts f
                  join articles a on a.id = f.rowid
                 where article_fts match :query
                 order by bm25(article_fts), coalesce(a.published_at, a.ingested_at) desc
                 limit :limit
                """)
                .setParameter("query", ftsQuery)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::result)
                .toList();
    }

    private String toFtsQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return Arrays.stream(query.trim().split("\\s+"))
                .map(token -> token.replace("\"", "\"\""))
                .filter(token -> !token.isBlank())
                .map(token -> "\"" + token + "\"*")
                .reduce((left, right) -> left + " AND " + right)
                .orElse("");
    }

    private SearchResult result(Object row) {
        Object[] values = (Object[]) row;
        return new SearchResult(
                stringValue(values[0]),
                longValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                stringValue(values[4]),
                stringValue(values[5])
        );
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
