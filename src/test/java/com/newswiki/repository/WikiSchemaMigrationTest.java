package com.newswiki.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:wiki_schema_migration_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class WikiSchemaMigrationTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsWikiSchemaTables() {
        Set<String> tableNames = Set.copyOf(jdbc.query(
                "select name from sqlite_master where type = 'table'",
                (rs, rowNum) -> rs.getString("name")
        ));

        assertThat(tableNames).contains(
                "article_raw_sources",
                "wiki_categories",
                "wiki_pages",
                "wiki_page_sources",
                "wiki_revisions",
                "wiki_runs",
                "daily_wiki_summaries"
        );
        assertThat(tableNames).doesNotContain("sections", "section_summaries", "wiki_sections");
    }

    @Test
    void addsWikiProcessingColumnsToArticles() {
        Set<String> columnNames = Set.copyOf(jdbc.query(
                "pragma table_info(articles)",
                (rs, rowNum) -> rs.getString("name")
        ));

        assertThat(columnNames).contains(
                "raw_status",
                "wiki_status",
                "wiki_locked_at",
                "wiki_attempt_count",
                "wiki_last_error"
        );
    }

    @Test
    void requiresWikiPageMajorCategory() {
        Set<String> columnNames = Set.copyOf(jdbc.query(
                "pragma table_info(wiki_pages)",
                (rs, rowNum) -> rs.getString("name")
        ));

        assertThat(columnNames).contains("major_category_id");
        assertThat(columnNames).contains("subcategory_id");
        assertThat(columnNames).doesNotContain("section_id");
        assertThatThrownBy(() -> jdbc.update("""
                insert into wiki_pages(slug, title, summary, body, importance, status, created_at, updated_at)
                values('missing-major', '대분류 없음', '', '', 50, 'ACTIVE', datetime('now'), datetime('now'))
                """))
                .hasMessageContaining("wiki page major_category_id is required");
    }

}
