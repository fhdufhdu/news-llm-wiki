package com.newswiki.repository;

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
