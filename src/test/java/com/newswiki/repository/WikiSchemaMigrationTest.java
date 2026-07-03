package com.newswiki.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
                "wiki_sections",
                "wiki_pages",
                "wiki_page_sources",
                "wiki_revisions",
                "wiki_runs"
        );
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
}
