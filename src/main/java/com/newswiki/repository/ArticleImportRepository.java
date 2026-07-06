package com.newswiki.repository;

import com.newswiki.dto.ArticleImportItem;
import com.newswiki.dto.ArticleImportJob;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ArticleImportRepository {
    private final EntityManager entityManager;

    @Transactional
    public long createJob(List<String> urls) {
        LinkedHashSet<String> uniqueUrls = new LinkedHashSet<>();
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                uniqueUrls.add(url.trim());
            }
        }
        entityManager.createNativeQuery("""
                insert into article_import_jobs(status, total_count, last_message)
                values ('PENDING', :totalCount, :lastMessage)
                """)
                .setParameter("totalCount", uniqueUrls.size())
                .setParameter("lastMessage", "URL " + uniqueUrls.size() + "개 등록")
                .executeUpdate();
        long jobId = longValue(entityManager.createNativeQuery("select last_insert_rowid()").getSingleResult());
        for (String url : uniqueUrls) {
            entityManager.createNativeQuery("""
                    insert into article_import_items(job_id, input_url, status)
                    values (:jobId, :inputUrl, 'PENDING')
                    """)
                    .setParameter("jobId", jobId)
                    .setParameter("inputUrl", url)
                    .executeUpdate();
        }
        return jobId;
    }

    @Transactional
    public void startJob(long jobId, String message) {
        entityManager.createNativeQuery("""
                update article_import_jobs
                   set status = 'RUNNING',
                       started_at = coalesce(started_at, :now),
                       last_message = :message
                 where id = :jobId
                """)
                .setParameter("now", Instant.now().toString())
                .setParameter("message", message)
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    @Transactional
    public void finishJob(long jobId, String message) {
        refreshCounts(jobId);
        ArticleImportJob job = findJob(jobId).orElseThrow();
        String status = job.failedCount() == 0 ? "DONE"
                : job.failedCount() == job.totalCount() ? "FAILED" : "PARTIAL_FAILED";
        entityManager.createNativeQuery("""
                update article_import_jobs
                   set status = :status,
                       finished_at = :now,
                       last_message = :message
                 where id = :jobId
                """)
                .setParameter("status", status)
                .setParameter("now", Instant.now().toString())
                .setParameter("message", message)
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public Optional<ArticleImportJob> findJob(long jobId) {
        List<?> rows = entityManager.createNativeQuery("""
                select id, status, total_count, fetched_count, wiki_done_count, failed_count,
                       created_at, started_at, finished_at, last_message
                  from article_import_jobs
                 where id = :jobId
                """)
                .setParameter("jobId", jobId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(job(rows.getFirst()));
    }

    @Transactional(readOnly = true)
    public List<ArticleImportJob> findRecentJobs(int limit) {
        return entityManager.createNativeQuery("""
                select id, status, total_count, fetched_count, wiki_done_count, failed_count,
                       created_at, started_at, finished_at, last_message
                  from article_import_jobs
                 order by created_at desc, id desc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::job)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleImportItem> findItems(long jobId) {
        return entityManager.createNativeQuery("""
                select id, job_id, input_url, canonical_url, article_id, status,
                       error_message, attempt_count, created_at, updated_at
                  from article_import_items
                 where job_id = :jobId
                 order by id asc
                """)
                .setParameter("jobId", jobId)
                .getResultStream()
                .map(this::item)
                .toList();
    }

    @Transactional
    public List<ArticleImportItem> claimPendingItems(long jobId, int limit) {
        List<ArticleImportItem> items = entityManager.createNativeQuery("""
                select id, job_id, input_url, canonical_url, article_id, status,
                       error_message, attempt_count, created_at, updated_at
                  from article_import_items
                 where job_id = :jobId
                   and status = 'PENDING'
                 order by id asc
                 limit :limit
                """)
                .setParameter("jobId", jobId)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::item)
                .toList();
        for (ArticleImportItem item : items) {
            markFetching(item.id());
        }
        return items;
    }

    @Transactional
    public void markFetching(long itemId) {
        updateStatus(itemId, "FETCHING", null, null, null);
    }

    @Transactional
    public void markFetched(long itemId, String canonicalUrl, long articleId) {
        updateStatus(itemId, "FETCHED", canonicalUrl, articleId, null);
        refreshCountsByItem(itemId);
    }

    @Transactional
    public void markWikiPending(long itemId) {
        updateStatus(itemId, "WIKI_PENDING", null, null, null);
    }

    @Transactional
    public void markDone(long itemId) {
        updateStatus(itemId, "DONE", null, null, null);
        refreshCountsByItem(itemId);
    }

    @Transactional
    public void markFailed(long itemId, String errorMessage) {
        entityManager.createNativeQuery("""
                update article_import_items
                   set status = 'FAILED',
                       error_message = :errorMessage,
                       attempt_count = attempt_count + 1,
                       updated_at = :now
                 where id = :itemId
                """)
                .setParameter("errorMessage", errorMessage)
                .setParameter("now", Instant.now().toString())
                .setParameter("itemId", itemId)
                .executeUpdate();
        refreshCountsByItem(itemId);
    }

    @Transactional
    public int markDoneForCompletedWiki(long jobId) {
        int changed = entityManager.createNativeQuery("""
                update article_import_items
                   set status = 'DONE',
                       updated_at = :now
                 where job_id = :jobId
                   and status in ('WIKI_PENDING', 'WIKI_RUNNING')
                   and article_id in (select id from articles where wiki_status = 'DONE')
                """)
                .setParameter("jobId", jobId)
                .setParameter("now", Instant.now().toString())
                .executeUpdate();
        refreshCounts(jobId);
        return changed;
    }

    @Transactional
    public int recoverInterruptedItems() {
        return entityManager.createNativeQuery("""
                update article_import_items
                   set status = case
                           when status = 'FETCHING' then 'PENDING'
                           when status = 'WIKI_RUNNING' then 'WIKI_PENDING'
                           else status
                       end,
                       updated_at = :now
                 where status in ('FETCHING', 'WIKI_RUNNING')
                """)
                .setParameter("now", Instant.now().toString())
                .executeUpdate();
    }

    private void updateStatus(long itemId, String status, String canonicalUrl, Long articleId, String errorMessage) {
        entityManager.createNativeQuery("""
                update article_import_items
                   set status = :status,
                       canonical_url = coalesce(:canonicalUrl, canonical_url),
                       article_id = coalesce(:articleId, article_id),
                       error_message = :errorMessage,
                       updated_at = :now
                 where id = :itemId
                """)
                .setParameter("status", status)
                .setParameter("canonicalUrl", canonicalUrl)
                .setParameter("articleId", articleId)
                .setParameter("errorMessage", errorMessage)
                .setParameter("now", Instant.now().toString())
                .setParameter("itemId", itemId)
                .executeUpdate();
    }

    private void refreshCountsByItem(long itemId) {
        Long jobId = longValue(entityManager.createNativeQuery("select job_id from article_import_items where id = :itemId")
                .setParameter("itemId", itemId)
                .getSingleResult());
        refreshCounts(jobId);
    }

    private void refreshCounts(long jobId) {
        entityManager.createNativeQuery("""
                update article_import_jobs
                   set fetched_count = (
                           select count(*) from article_import_items
                            where job_id = :jobId
                              and status in ('FETCHED', 'WIKI_PENDING', 'WIKI_RUNNING', 'DONE')
                       ),
                       wiki_done_count = (
                           select count(*) from article_import_items
                            where job_id = :jobId
                              and status = 'DONE'
                       ),
                       failed_count = (
                           select count(*) from article_import_items
                            where job_id = :jobId
                              and status = 'FAILED'
                       )
                 where id = :jobId
                """)
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    private ArticleImportJob job(Object row) {
        Object[] values = (Object[]) row;
        return new ArticleImportJob(
                longValue(values[0]),
                stringValue(values[1]),
                intValue(values[2]),
                intValue(values[3]),
                intValue(values[4]),
                intValue(values[5]),
                stringValue(values[6]),
                stringValue(values[7]),
                stringValue(values[8]),
                stringValue(values[9])
        );
    }

    private ArticleImportItem item(Object row) {
        Object[] values = (Object[]) row;
        return new ArticleImportItem(
                longValue(values[0]),
                longValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                values[4] == null ? null : longValue(values[4]),
                stringValue(values[5]),
                stringValue(values[6]),
                intValue(values[7]),
                stringValue(values[8]),
                stringValue(values[9])
        );
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
