package com.newswiki.repository;

import com.newswiki.dto.AiArticleResult;
import com.newswiki.dto.Provider;
import com.newswiki.dto.SectionNavItem;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WikiRepository {
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<SectionNavItem> findEnabledSectionsForNav(String activeSlug) {
        return entityManager.createNativeQuery("""
                select slug, title
                  from sections
                 where enabled = 1
                 order by display_order asc, title asc
                """)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    String slug = stringValue(values[0]);
                    return new SectionNavItem(slug, stringValue(values[1]), slug.equals(activeSlug));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Provider> findEnabledProviders() {
        return entityManager.createNativeQuery("""
                select id, slug, name, homepage_url, description, display_order, enabled
                  from providers
                 where enabled = 1
                 order by display_order asc, name asc
                """)
                .getResultStream()
                .map(this::provider)
                .toList();
    }

    @Transactional(readOnly = true)
    public Provider findEnabledProviderBySlug(String slug) {
        return provider(entityManager.createNativeQuery("""
                select id, slug, name, homepage_url, description, display_order, enabled
                  from providers
                 where enabled = 1
                   and slug = :slug
                """)
                .setParameter("slug", slug)
                .getSingleResult());
    }

    @Transactional(readOnly = true)
    public List<WikiItem> findTopics(int limit) {
        return entityManager.createNativeQuery("""
                select t.slug, t.title, t.summary, count(at.article_id) as article_count, t.updated_at
                  from topics t
                  left join article_topic at on at.topic_id = t.id
                 group by t.id
                 order by article_count desc, t.updated_at desc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::wikiItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public WikiItem findTopic(String slug) {
        return wikiItem(entityManager.createNativeQuery("""
                select t.slug, t.title, t.summary, count(at.article_id) as article_count, t.updated_at
                  from topics t
                  left join article_topic at on at.topic_id = t.id
                 where t.slug = :slug
                 group by t.id
                """)
                .setParameter("slug", slug)
                .getSingleResult());
    }

    @Transactional(readOnly = true)
    public List<String> findArticleTopicTitles(long articleId, int limit) {
        return entityManager.createNativeQuery("""
                select t.title
                  from topics t
                  join article_topic at on at.topic_id = t.id
                 where at.article_id = :articleId
                 order by t.title asc
                 limit :limit
                """)
                .setParameter("articleId", articleId)
                .setParameter("limit", limit)
                .getResultStream()
                .map(Object::toString)
                .toList();
    }

    @Transactional(readOnly = true)
    public long findProviderIdBySlug(String slug) {
        return longValue(entityManager.createNativeQuery("select id from providers where slug = :slug and enabled = 1")
                .setParameter("slug", slug)
                .getSingleResult());
    }

    @Transactional
    public long upsertProviderByName(String name) {
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-|-$)", "");
        entityManager.createNativeQuery("""
                insert into providers(slug, name, description, display_order, enabled, created_at, updated_at)
                values (:slug, :name, '', 1000, 1, datetime('now'), datetime('now'))
                on conflict(slug) do update set name = excluded.name, updated_at = datetime('now')
                """)
                .setParameter("slug", slug)
                .setParameter("name", name)
                .executeUpdate();
        return longValue(entityManager.createNativeQuery("select id from providers where slug = :slug")
                .setParameter("slug", slug)
                .getSingleResult());
    }

    @Transactional
    public void linkAiResult(AiArticleResult result) {
        for (AiArticleResult.AiLink topic : result.topics()) {
            long topicId = upsertTopic(topic.slug(), firstNonBlank(topic.title(), topic.name(), topic.slug()));
            insertRelation("article_topic", "topic_id", result.articleId(), topicId, "confidence", 1.0);
        }
        for (AiArticleResult.AiLink entity : result.entities()) {
            long entityId = upsertEntity(entity.slug(), firstNonBlank(entity.name(), entity.title(), entity.slug()));
            insertRelation("article_entity", "entity_id", result.articleId(), entityId, "role", "mentioned");
        }
        for (AiArticleResult.AiLink event : result.events()) {
            long eventId = upsertEvent(event.slug(), firstNonBlank(event.title(), event.name(), event.slug()));
            insertRelation("article_event", "event_id", result.articleId(), eventId, "role", "evidence");
        }
        for (AiArticleResult.AiLink claim : result.claims()) {
            long claimId = upsertClaim(claim.slug(), firstNonBlank(claim.title(), claim.name(), claim.slug()));
            insertRelation("article_claim", "claim_id", result.articleId(), claimId, "stance", "reported");
        }
    }

    private long upsertTopic(String slug, String title) {
        entityManager.createNativeQuery("""
                insert into topics(slug, title, summary, updated_at)
                values (:slug, :title, '', datetime('now'))
                on conflict(slug) do update set title = excluded.title, updated_at = datetime('now')
                """)
                .setParameter("slug", slug)
                .setParameter("title", title)
                .executeUpdate();
        return idBySlug("topics", slug);
    }

    private long upsertEntity(String slug, String name) {
        entityManager.createNativeQuery("""
                insert into entities(slug, name, entity_type, summary, updated_at)
                values (:slug, :name, 'unknown', '', datetime('now'))
                on conflict(slug) do update set name = excluded.name, updated_at = datetime('now')
                """)
                .setParameter("slug", slug)
                .setParameter("name", name)
                .executeUpdate();
        return idBySlug("entities", slug);
    }

    private long upsertEvent(String slug, String title) {
        entityManager.createNativeQuery("""
                insert into events(slug, title, summary, status, updated_at)
                values (:slug, :title, '', 'reported', datetime('now'))
                on conflict(slug) do update set title = excluded.title, updated_at = datetime('now')
                """)
                .setParameter("slug", slug)
                .setParameter("title", title)
                .executeUpdate();
        return idBySlug("events", slug);
    }

    private long upsertClaim(String slug, String title) {
        entityManager.createNativeQuery("""
                insert into claims(slug, title, claim_type, summary, verification_status, updated_at)
                values (:slug, :title, 'reported', '', 'reported', datetime('now'))
                on conflict(slug) do update set title = excluded.title, updated_at = datetime('now')
                """)
                .setParameter("slug", slug)
                .setParameter("title", title)
                .executeUpdate();
        return idBySlug("claims", slug);
    }

    private void insertRelation(String table, String targetColumn, long articleId, long targetId, String valueColumn, Object value) {
        entityManager.createNativeQuery("""
                insert or ignore into %s(article_id, %s, %s)
                values (:articleId, :targetId, :value)
                """.formatted(table, targetColumn, valueColumn))
                .setParameter("articleId", articleId)
                .setParameter("targetId", targetId)
                .setParameter("value", value)
                .executeUpdate();
    }

    private long idBySlug(String table, String slug) {
        return longValue(entityManager.createNativeQuery("select id from " + table + " where slug = :slug")
                .setParameter("slug", slug)
                .getSingleResult());
    }

    private Provider provider(Object row) {
        Object[] values = (Object[]) row;
        return new Provider(
                longValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                stringValue(values[4]),
                intValue(values[5]),
                intValue(values[6]) == 1
        );
    }

    private WikiItem wikiItem(Object row) {
        Object[] values = (Object[]) row;
        return new WikiItem(
                stringValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                intValue(values[3]),
                stringValue(values[4])
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
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

    public record WikiItem(
            String slug,
            String title,
            String summary,
            int articleCount,
            String updatedAt
    ) {
    }
}
