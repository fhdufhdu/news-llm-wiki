package com.newswiki.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ArticleRepository {
    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public long insertArticleIfAbsent(
            String sourceId,
            String canonicalUrl,
            String title,
            String feedUrl,
            Instant publishedAt,
            String contentHash
    ) {
        entityManager.createNativeQuery("""
                insert or ignore into articles
                    (source_id, canonical_url, title, feed_url, published_at, ingested_at, content_hash)
                values
                    (:sourceId, :canonicalUrl, :title, :feedUrl, :publishedAt, :ingestedAt, :contentHash)
                """)
                .setParameter("sourceId", sourceId)
                .setParameter("canonicalUrl", canonicalUrl)
                .setParameter("title", title)
                .setParameter("feedUrl", feedUrl)
                .setParameter("publishedAt", publishedAt == null ? null : publishedAt.toString())
                .setParameter("ingestedAt", Instant.now().toString())
                .setParameter("contentHash", contentHash)
                .executeUpdate();

        return longValue(entityManager.createNativeQuery("select id from articles where canonical_url = :canonicalUrl")
                .setParameter("canonicalUrl", canonicalUrl)
                .getSingleResult());
    }

    @Transactional(readOnly = true)
    public boolean existsByCanonicalUrl(String canonicalUrl) {
        Number count = (Number) entityManager.createNativeQuery("select count(*) from articles where canonical_url = :canonicalUrl")
                .setParameter("canonicalUrl", canonicalUrl)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findIdByCanonicalUrl(String canonicalUrl) {
        List<?> rows = entityManager.createNativeQuery("select id from articles where canonical_url = :canonicalUrl")
                .setParameter("canonicalUrl", canonicalUrl)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(longValue(rows.getFirst()));
    }

    @Transactional
    public long saveManualRawArticle(
            String canonicalUrl,
            String title,
            String rawHtml,
            int httpStatus,
            String contentHash,
            Instant publishedAt
    ) {
        String sourceId = "manual-" + contentHash.substring(0, Math.min(16, contentHash.length()));
        long articleId = insertArticleIfAbsent(
                sourceId,
                canonicalUrl,
                title == null || title.isBlank() ? canonicalUrl : title,
                "manual",
                publishedAt,
                contentHash
        );
        saveRawHtml(articleId, rawHtml, contentHash, httpStatus);
        entityManager.createNativeQuery("""
                update articles
                   set wiki_status = 'PENDING',
                       wiki_locked_at = null,
                       wiki_last_error = null
                 where id = :articleId
                """)
                .setParameter("articleId", articleId)
                .executeUpdate();
        return articleId;
    }

    @Transactional
    public int resetRetryableWikiFailures(int maxRetries) {
        return entityManager.createNativeQuery("""
                update articles
                   set wiki_status = 'PENDING',
                       wiki_locked_at = null,
                       wiki_last_error = null
                 where wiki_status = 'FAILED'
                   and wiki_attempt_count < :maxRetries
                """)
                .setParameter("maxRetries", maxRetries)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public int countPendingWikiArticles() {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from articles a
                  join article_raw_sources r on r.article_id = a.id
                 where a.wiki_status = 'PENDING'
                """)
                .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    @Transactional
    public int recoverInterruptedWikiRunning() {
        return entityManager.createNativeQuery("""
                update articles
                   set wiki_status = 'PENDING',
                       wiki_locked_at = null,
                       wiki_last_error = 'Server restarted while wiki processing was running'
                 where wiki_status = 'RUNNING'
                """)
                .executeUpdate();
    }

    @Transactional
    public void saveRawHtml(long articleId, String rawHtml, String contentHash, int httpStatus) {
        String now = Instant.now().toString();
        entityManager.createNativeQuery("""
                insert into article_raw_sources
                    (article_id, raw_html, content_hash, http_status, fetched_at, created_at)
                values
                    (:articleId, :rawHtml, :contentHash, :httpStatus, :now, :now)
                on conflict(article_id) do update set
                    raw_html = excluded.raw_html,
                    content_hash = excluded.content_hash,
                    http_status = excluded.http_status,
                    fetched_at = excluded.fetched_at
                """)
                .setParameter("articleId", articleId)
                .setParameter("rawHtml", rawHtml)
                .setParameter("contentHash", contentHash)
                .setParameter("httpStatus", httpStatus)
                .setParameter("now", now)
                .executeUpdate();

        entityManager.createNativeQuery("""
                update articles
                   set raw_status = 'FETCHED',
                       wiki_status = case when wiki_status is null then 'PENDING' else wiki_status end,
                       raw_id = null
                 where id = :articleId
                """)
                .setParameter("articleId", articleId)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public boolean hasRawHtml(long articleId) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from article_raw_sources
                 where article_id = :articleId
                """)
                .setParameter("articleId", articleId)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    @Transactional(readOnly = true)
    public List<ArticleListView> findLatestArticles(int limit) {
        return articleList("""
                select a.id, a.title, a.canonical_url, a.published_at, a.wiki_status,
                       '원문 수집 상태: ' || a.raw_status || ', 위키 처리 상태: ' || a.wiki_status as summary,
                       lower(a.wiki_status) as durability
                  from articles a
                 order by coalesce(a.published_at, a.ingested_at) desc, a.id desc
                 limit :limit
                """, limit);
    }

    @Transactional(readOnly = true)
    public ArticleDetailView findArticleDetail(long id) {
        List<?> rows = entityManager.createNativeQuery("""
                select a.id, a.title, a.canonical_url, a.feed_url, a.published_at, a.ingested_at,
                       a.raw_status, a.wiki_status
                  from articles a
                where a.id = :id
                """)
                .setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] values = (Object[]) rows.getFirst();
        return new ArticleDetailView(
                longValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                stringValue(values[4]),
                stringValue(values[5]),
                stringValue(values[6]),
                stringValue(values[7])
        );
    }

    private List<ArticleListView> articleList(String sql, int limit) {
        var query = entityManager.createNativeQuery(sql).setParameter("limit", limit);
        return query.getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new ArticleListView(
                            longValue(values[0]),
                            stringValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[5]),
                            stringValue(values[6]),
                            stringValue(values[4])
                    );
                })
                .toList();
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record ArticleListView(
            long id,
            String title,
            String canonicalUrl,
            String publishedAt,
            String summary,
            String durability,
            String wikiStatus
    ) {
    }

    public record ArticleDetailView(
            long id,
            String title,
            String canonicalUrl,
            String feedUrl,
            String publishedAt,
            String ingestedAt,
            String rawStatus,
            String wikiStatus
    ) {
    }
}
