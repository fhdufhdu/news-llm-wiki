package com.newswiki.repository;

import com.newswiki.dto.WikiPageDetail;
import com.newswiki.dto.WikiPageListItem;
import com.newswiki.dto.WikiSection;
import com.newswiki.dto.WikiSourceRef;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WikiPageRepository {
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<WikiSection> findSections() {
        return entityManager.createNativeQuery("""
                select id, slug, title, summary, display_order
                  from wiki_sections
                 where status = 'ACTIVE'
                 order by display_order asc, title asc
                """)
                .getResultStream()
                .map(this::section)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WikiPageListItem> findRecentPages(int limit) {
        return entityManager.createNativeQuery("""
                select id, slug, title, summary, importance, updated_at
                  from wiki_pages
                 where status = 'ACTIVE'
                 order by updated_at desc, importance desc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(this::pageListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WikiPageListItem> findPagesBySection(String sectionSlug) {
        return entityManager.createNativeQuery("""
                select p.id, p.slug, p.title, p.summary, p.importance, p.updated_at
                  from wiki_pages p
                  join wiki_sections s on s.id = p.section_id
                 where s.slug = :sectionSlug
                   and s.status = 'ACTIVE'
                   and p.status = 'ACTIVE'
                 order by p.importance desc, p.updated_at desc, p.title asc
                """)
                .setParameter("sectionSlug", sectionSlug)
                .getResultStream()
                .map(this::pageListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public WikiPageDetail findPageBySlug(String slug) {
        List<?> rows = entityManager.createNativeQuery("""
                select id, slug, title, summary, body, importance, updated_at
                  from wiki_pages
                 where slug = :slug
                   and status = 'ACTIVE'
                """)
                .setParameter("slug", slug)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] values = (Object[]) rows.getFirst();
        long pageId = longValue(values[0]);
        return new WikiPageDetail(
                pageId,
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                stringValue(values[4]),
                intValue(values[5]),
                stringValue(values[6]),
                findSources(pageId)
        );
    }

    @Transactional(readOnly = true)
    public List<WikiSourceRef> findSources(long pageId) {
        return entityManager.createNativeQuery("""
                select a.id, p.name, a.title, a.canonical_url, s.contribution_summary
                  from wiki_page_sources s
                  join articles a on a.id = s.article_id
                  join providers p on p.id = a.provider_id
                 where s.wiki_page_id = :pageId
                 order by s.created_at desc, a.id desc
                """)
                .setParameter("pageId", pageId)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new WikiSourceRef(
                            longValue(values[0]),
                            stringValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[4])
                    );
                })
                .toList();
    }

    private WikiSection section(Object row) {
        Object[] values = (Object[]) row;
        return new WikiSection(
                longValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                intValue(values[4])
        );
    }

    private WikiPageListItem pageListItem(Object row) {
        Object[] values = (Object[]) row;
        return new WikiPageListItem(
                longValue(values[0]),
                stringValue(values[1]),
                stringValue(values[2]),
                stringValue(values[3]),
                intValue(values[4]),
                stringValue(values[5])
        );
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
