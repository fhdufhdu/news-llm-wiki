package com.newswiki.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ArticleRepository {
    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public long insertArticleIfAbsent(
            String sourceId,
            String canonicalUrl,
            long providerId,
            String title,
            String feedUrl,
            Instant publishedAt,
            String contentHash
    ) {
        entityManager.createNativeQuery("""
                insert or ignore into articles
                    (source_id, canonical_url, provider_id, title, feed_url, published_at, ingested_at, content_hash)
                values
                    (:sourceId, :canonicalUrl, :providerId, :title, :feedUrl, :publishedAt, :ingestedAt, :contentHash)
                """)
                .setParameter("sourceId", sourceId)
                .setParameter("canonicalUrl", canonicalUrl)
                .setParameter("providerId", providerId)
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
    public List<PendingAiArticle> findPendingAiArticles(int limit) {
        return entityManager.createNativeQuery("""
                select a.id, a.source_id, a.canonical_url, a.title, a.published_at, p.name as provider_name, r.id as raw_id
                  from articles a
                  join providers p on p.id = a.provider_id
                  join article_raw r on r.article_id = a.id
                 where a.ai_status = 'PENDING_AI'
                   and r.html_gzip is not null
                 order by a.ingested_at asc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new PendingAiArticle(
                            longValue(values[0]),
                            stringValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[4]),
                            stringValue(values[5]),
                            longValue(values[6])
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] findRawGzipByRawId(long rawId) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement("select html_gzip from article_raw where id = ?")) {
                statement.setLong(1, rawId);
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Raw article HTML not found: " + rawId);
                    }
                    return resultSet.getBytes(1);
                }
            }
        });
    }

    @Transactional
    public void markAiRunning(List<Long> articleIds) {
        for (Long articleId : articleIds) {
            entityManager.createNativeQuery("""
                    update articles
                       set ai_status = 'AI_RUNNING'
                     where id = :articleId
                       and ai_status = 'PENDING_AI'
                    """)
                    .setParameter("articleId", articleId)
                    .executeUpdate();
        }
    }

    @Transactional
    public void saveArticleNote(long articleId, String shortSummary, String durableKnowledgeJson, String durability, long jobRunId) {
        entityManager.createNativeQuery("""
                insert into article_notes(article_id, short_summary, durable_knowledge, durability, generated_by_job_id, generated_at)
                values (:articleId, :shortSummary, :durableKnowledge, :durability, :jobRunId, :generatedAt)
                on conflict(article_id) do update set
                    short_summary = excluded.short_summary,
                    durable_knowledge = excluded.durable_knowledge,
                    durability = excluded.durability,
                    generated_by_job_id = excluded.generated_by_job_id,
                    generated_at = excluded.generated_at
                """)
                .setParameter("articleId", articleId)
                .setParameter("shortSummary", shortSummary)
                .setParameter("durableKnowledge", durableKnowledgeJson)
                .setParameter("durability", durability == null || durability.isBlank() ? "transient" : durability)
                .setParameter("jobRunId", jobRunId)
                .setParameter("generatedAt", Instant.now().toString())
                .executeUpdate();
        entityManager.createNativeQuery("update articles set ai_status = 'AI_DONE', last_error = null where id = :articleId")
                .setParameter("articleId", articleId)
                .executeUpdate();
    }

    @Transactional
    public void markAiFailed(long articleId, String errorMessage) {
        entityManager.createNativeQuery("""
                update articles
                   set ai_status = 'AI_FAILED',
                       ai_retry_count = ai_retry_count + 1,
                       last_error = :errorMessage
                 where id = :articleId
                """)
                .setParameter("articleId", articleId)
                .setParameter("errorMessage", errorMessage)
                .executeUpdate();
    }

    @Transactional
    public int resetRetryableAiFailures(int maxRetries) {
        return entityManager.createNativeQuery("""
                update articles
                   set ai_status = 'PENDING_AI',
                       last_error = null
                 where ai_status = 'AI_FAILED'
                   and ai_retry_count < :maxRetries
                """)
                .setParameter("maxRetries", maxRetries)
                .executeUpdate();
    }

    @Transactional
    public long insertRawGzip(long articleId, byte[] htmlGzip, String contentType, int httpStatus) {
        entityManager.createNativeQuery("""
                insert into article_raw
                    (article_id, storage_mode, html_gzip, file_path, content_type, http_status, fetched_at)
                values
                    (:articleId, 'DB_GZIP', :htmlGzip, null, :contentType, :httpStatus, :fetchedAt)
                on conflict(article_id) do update set
                    storage_mode = 'DB_GZIP',
                    html_gzip = excluded.html_gzip,
                    file_path = null,
                    content_type = excluded.content_type,
                    http_status = excluded.http_status,
                    fetched_at = excluded.fetched_at
                """)
                .setParameter("articleId", articleId)
                .setParameter("htmlGzip", htmlGzip)
                .setParameter("contentType", contentType)
                .setParameter("httpStatus", httpStatus)
                .setParameter("fetchedAt", Instant.now().toString())
                .executeUpdate();

        long rawId = longValue(entityManager.createNativeQuery("select id from article_raw where article_id = :articleId")
                .setParameter("articleId", articleId)
                .getSingleResult());

        entityManager.createNativeQuery("update articles set raw_id = :rawId where id = :articleId")
                .setParameter("rawId", rawId)
                .setParameter("articleId", articleId)
                .executeUpdate();

        return rawId;
    }

    @Transactional(readOnly = true)
    public List<ArticleListView> findLatestArticles(int limit) {
        return articleList("""
                select a.id, a.title, a.canonical_url, a.published_at, a.ai_status,
                       p.name as provider_name,
                       coalesce(n.short_summary, 'AI 위키 데이터 생성 대기 중입니다.') as short_summary,
                       coalesce(n.durability, lower(a.ai_status)) as durability
                  from articles a
                  join providers p on p.id = a.provider_id
                  left join article_notes n on n.article_id = a.id
                 order by coalesce(a.published_at, a.ingested_at) desc, a.id desc
                 limit :limit
                """, limit, null);
    }

    @Transactional(readOnly = true)
    public List<ArticleListView> findLatestArticlesByProvider(String providerSlug, int limit) {
        return articleList("""
                select a.id, a.title, a.canonical_url, a.published_at, a.ai_status,
                       p.name as provider_name,
                       coalesce(n.short_summary, 'AI 위키 데이터 생성 대기 중입니다.') as short_summary,
                       coalesce(n.durability, lower(a.ai_status)) as durability
                  from articles a
                  join providers p on p.id = a.provider_id
                  left join article_notes n on n.article_id = a.id
                 where p.slug = :providerSlug
                 order by coalesce(a.published_at, a.ingested_at) desc, a.id desc
                 limit :limit
                """, limit, providerSlug);
    }

    @Transactional(readOnly = true)
    public ArticleDetailView findArticleDetail(long id) {
        Object[] values = (Object[]) entityManager.createNativeQuery("""
                select a.id, a.title, a.canonical_url, a.feed_url, a.published_at, a.ingested_at, a.ai_status,
                       p.name as provider_name,
                       n.short_summary, n.durable_knowledge, n.durability, n.generated_at
                  from articles a
                  join providers p on p.id = a.provider_id
                  left join article_notes n on n.article_id = a.id
                 where a.id = :id
                """)
                .setParameter("id", id)
                .getSingleResult();
        return new ArticleDetailView(
                longValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                stringValue(values[4]),
                stringValue(values[5]),
                stringValue(values[7]),
                stringValue(values[6]),
                stringValue(values[8]),
                stringValue(values[9]),
                stringValue(values[10]),
                stringValue(values[11])
        );
    }

    private List<ArticleListView> articleList(String sql, int limit, String providerSlug) {
        var query = entityManager.createNativeQuery(sql).setParameter("limit", limit);
        if (providerSlug != null) {
            query.setParameter("providerSlug", providerSlug);
        }
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
                            stringValue(values[7]),
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

    public record PendingAiArticle(
            long id,
            String sourceId,
            String canonicalUrl,
            String title,
            String publishedAt,
            String providerName,
            long rawId
    ) {
    }

    public record ArticleListView(
            long id,
            String title,
            String canonicalUrl,
            String publishedAt,
            String providerName,
            String summary,
            String durability,
            String aiStatus
    ) {
    }

    public record ArticleDetailView(
            long id,
            String title,
            String canonicalUrl,
            String feedUrl,
            String publishedAt,
            String ingestedAt,
            String providerName,
            String aiStatus,
            String shortSummary,
            String durableKnowledge,
            String durability,
            String generatedAt
    ) {
    }
}
