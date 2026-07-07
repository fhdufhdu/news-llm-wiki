package com.newswiki.repository;

import com.newswiki.dto.WikiPageListItem;
import com.newswiki.dto.WikiCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:wiki_page_repository_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class WikiPageRepositoryTest {
    @Autowired
    WikiPageRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from wiki_revisions");
        jdbc.update("delete from wiki_page_sources");
        jdbc.update("delete from wiki_pages");
        jdbc.update("delete from wiki_categories");
        jdbc.update("delete from article_raw_sources");
        jdbc.update("delete from articles");
    }

    @Test
    void readsActiveCategoriesAndPagesFromWikiTables() {
        long majorId = insertCategory("technology", "기술", "기술 흐름", 10, 1);
        long subcategoryId = insertCategory("ai", "AI", "AI 흐름", 100, 0);
        insertPage(majorId, subcategoryId, "gpu-power", "GPU 전력", "전력 병목", "본문", 90);

        assertThat(repository.findCategories()).extracting(WikiCategory::slug).containsExactly("technology", "ai");
        assertThat(repository.findPagesByCategory("technology")).extracting(WikiPageListItem::title).containsExactly("GPU 전력");
        assertThat(repository.findPagesByCategory("ai")).extracting(WikiPageListItem::title).containsExactly("GPU 전력");
    }

    @Test
    void readsPageDetailWithSources() {
        long articleId = insertArticle();
        long majorId = insertCategory("technology", "기술", "기술 흐름", 10, 1);
        long subcategoryId = insertCategory("ai", "AI", "AI 흐름", 100, 0);
        long pageId = insertPage(majorId, subcategoryId, "gpu-power", "GPU 전력", "전력 병목", "본문", 90);
        jdbc.update("""
                insert into wiki_page_sources(wiki_page_id, article_id, contribution_summary, evidence_type, created_at)
                values(?, ?, '전력 인프라 근거', 'reported', datetime('now'))
                """, pageId, articleId);

        var detail = repository.findPageBySlug("gpu-power");

        assertThat(detail.title()).isEqualTo("GPU 전력");
        assertThat(detail.sources()).hasSize(1);
        assertThat(detail.sources().getFirst().contributionSummary()).isEqualTo("전력 인프라 근거");
    }

    private long insertArticle() {
        jdbc.update("""
                insert into articles(source_id, canonical_url, title, feed_url, ingested_at, content_hash, raw_status, wiki_status)
                values('wiki-source-1', 'https://example.com/wiki-article', '근거 기사', 'https://example.com/rss', datetime('now'), 'hash', 'FETCHED', 'DONE')
                """);
        return jdbc.queryForObject("select id from articles where source_id='wiki-source-1'", Long.class);
    }

    private long insertCategory(String slug, String title, String summary, int displayOrder, int fixed) {
        jdbc.update("""
                insert into wiki_categories(slug, title, summary, display_order, status, fixed, created_at, updated_at)
                values(?, ?, ?, ?, 'ACTIVE', ?, datetime('now'), datetime('now'))
                """, slug, title, summary, displayOrder, fixed);
        return jdbc.queryForObject("select id from wiki_categories where slug=?", Long.class, slug);
    }

    private long insertPage(long majorId, long subcategoryId, String slug, String title, String summary, String body, int importance) {
        jdbc.update("""
                insert into wiki_pages(major_category_id, subcategory_id, slug, title, summary, body, importance, status, created_at, updated_at)
                values(?, ?, ?, ?, ?, ?, ?, 'ACTIVE', datetime('now'), datetime('now'))
                """, majorId, subcategoryId, slug, title, summary, body, importance);
        return jdbc.queryForObject("select id from wiki_pages where slug=?", Long.class, slug);
    }
}
