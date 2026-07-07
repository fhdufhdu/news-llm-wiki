package com.newswiki.infrastructure.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newswiki.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:existing_corpus_importer_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ExistingCorpusImporterTest {
    @TempDir
    Path tempDir;

    @Autowired
    ArticleRepository articleRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from article_raw");
        jdbcTemplate.update("delete from articles");
    }

    @Test
    void importsSourceIndexRows() throws Exception {
        var importer = new ExistingCorpusImporter(
                new ObjectMapper(),
                articleRepository
        );
        Path file = tempDir.resolve("source-index.jsonl");
        Files.writeString(file, """
                {"source_id":"s1","canonical_url":"https://example.com/1","title":"기사","publisher":"SampleSource","feed_url":"https://example.com/rss","published":"2026-07-02T00:00:00Z","raw_path":"raw/1.html"}
                """);

        int imported = importer.importSourceIndex(file);

        Integer articleCount = jdbcTemplate.queryForObject("select count(*) from articles", Integer.class);
        assertThat(imported).isEqualTo(1);
        assertThat(articleCount).isEqualTo(1);
    }
}
