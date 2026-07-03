package com.newswiki.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ArticleFetchFailureRepository {
    private final EntityManager entityManager;

    @Transactional
    public void recordFailure(FailureInput input, String errorMessage, int maxRetries) {
        String now = Instant.now().toString();
        entityManager.createNativeQuery("""
                insert into article_fetch_failures(
                    canonical_url, source_key, source_name, feed_url, title, published_at,
                    failure_count, status, last_error, last_attempt_at, updated_at
                )
                values (
                    :canonicalUrl, :sourceKey, :sourceName, :feedUrl, :title, :publishedAt,
                    1, case when :maxRetries <= 1 then 'IGNORED' else 'RETRYABLE' end,
                    :lastError, :now, :now
                )
                on conflict(canonical_url) do update set
                    source_key = excluded.source_key,
                    source_name = excluded.source_name,
                    feed_url = excluded.feed_url,
                    title = excluded.title,
                    published_at = excluded.published_at,
                    failure_count = article_fetch_failures.failure_count + 1,
                    status = case
                        when article_fetch_failures.failure_count + 1 >= :maxRetries then 'IGNORED'
                        else 'RETRYABLE'
                    end,
                    last_error = excluded.last_error,
                    last_attempt_at = excluded.last_attempt_at,
                    updated_at = excluded.updated_at
                """)
                .setParameter("canonicalUrl", input.canonicalUrl())
                .setParameter("sourceKey", input.sourceKey())
                .setParameter("sourceName", input.sourceName())
                .setParameter("feedUrl", input.feedUrl())
                .setParameter("title", input.title())
                .setParameter("publishedAt", input.publishedAt())
                .setParameter("maxRetries", maxRetries)
                .setParameter("lastError", errorMessage)
                .setParameter("now", now)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public boolean isIgnored(String canonicalUrl) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from article_fetch_failures
                 where canonical_url = :canonicalUrl
                   and status = 'IGNORED'
                """)
                .setParameter("canonicalUrl", canonicalUrl)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Transactional(readOnly = true)
    public List<FailureItem> findRetryable(int limit) {
        return entityManager.createNativeQuery("""
                select canonical_url, source_key, source_name, feed_url, title, published_at, failure_count
                  from article_fetch_failures
                 where status = 'RETRYABLE'
                 order by updated_at asc, id asc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new FailureItem(
                            stringValue(values[0]),
                            stringValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[4]),
                            stringValue(values[5]),
                            ((Number) values[6]).intValue()
                    );
                })
                .toList();
    }

    @Transactional
    public void deleteByCanonicalUrl(String canonicalUrl) {
        entityManager.createNativeQuery("delete from article_fetch_failures where canonical_url = :canonicalUrl")
                .setParameter("canonicalUrl", canonicalUrl)
                .executeUpdate();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record FailureInput(
            String canonicalUrl,
            String sourceKey,
            String sourceName,
            String feedUrl,
            String title,
            String publishedAt
    ) {
    }

    public record FailureItem(
            String canonicalUrl,
            String sourceKey,
            String sourceName,
            String feedUrl,
            String title,
            String publishedAt,
            int failureCount
    ) {
    }
}
