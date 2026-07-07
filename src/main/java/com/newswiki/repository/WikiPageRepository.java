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
    public List<WikiSection> findFixedNavSections() {
        return entityManager.createNativeQuery("""
                select id, slug, title, summary, display_order
                  from wiki_sections
                 where status = 'ACTIVE'
                   and fixed = 1
                 order by display_order asc, title asc
                """)
                .getResultStream()
                .map(this::section)
                .toList();
    }

    @Transactional(readOnly = true)
    public TodaySummaryView findTodaySummary(String date) {
        int articleCount = intValue(entityManager.createNativeQuery("""
                select count(*)
                  from articles
                 where date(datetime(coalesce(published_at, ingested_at, collected_at), '+9 hours')) = :date
                """)
                .setParameter("date", date)
                .getSingleResult());
        List<WikiPageListItem> pages = entityManager.createNativeQuery("""
                select distinct p.id, p.slug, p.title, p.summary, p.importance, p.updated_at
                  from wiki_pages p
                  join wiki_page_sources ps on ps.wiki_page_id = p.id
                  join articles a on a.id = ps.article_id
                 where p.status = 'ACTIVE'
                   and date(datetime(coalesce(a.published_at, a.ingested_at, a.collected_at), '+9 hours')) = :date
                 order by p.importance desc, p.updated_at desc
                 limit 8
                """)
                .setParameter("date", date)
                .getResultStream()
                .map(this::pageListItem)
                .toList();
        String summary = pages.isEmpty()
                ? "오늘 저장된 기사 원문을 바탕으로 위키 문서가 생성되면 이 영역에 주요 흐름을 표시합니다."
                : pages.stream()
                .limit(4)
                .map(page -> page.title() + ": " + page.summary())
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
        return new TodaySummaryView(date, articleCount, pages.size(), summary, pages);
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
    public List<String> findPageTitlesByArticleId(long articleId, int limit) {
        return entityManager.createNativeQuery("""
                select p.title
                  from wiki_pages p
                  join wiki_page_sources s on s.wiki_page_id = p.id
                 where s.article_id = :articleId
                   and p.status = 'ACTIVE'
                 order by p.importance desc, p.title asc
                 limit :limit
                """)
                .setParameter("articleId", articleId)
                .setParameter("limit", limit)
                .getResultStream()
                .map(Object::toString)
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
                select a.id, a.title, a.canonical_url, s.contribution_summary
                  from wiki_page_sources s
                  join articles a on a.id = s.article_id
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
                            stringValue(values[3])
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

    public record TodaySummaryView(
            String date,
            int articleCount,
            int wikiPageCount,
            String summary,
            List<WikiPageListItem> pages
    ) {
    }
}
