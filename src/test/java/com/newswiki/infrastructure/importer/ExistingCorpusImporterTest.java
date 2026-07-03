package com.newswiki.infrastructure.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
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
    WikiRepository wikiRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from article_raw");
        jdbcTemplate.update("delete from articles");
        jdbcTemplate.update("delete from providers where name = 'GeekNews'");
    }

    @Test
    void importsSourceIndexRows() throws Exception {
        var importer = new ExistingCorpusImporter(
                new ObjectMapper(),
                articleRepository,
                wikiRepository
        );
        Path file = tempDir.resolve("source-index.jsonl");
        Files.writeString(file, """
                {"source_id":"s1","canonical_url":"https://example.com/1","title":"기사","publisher":"GeekNews","feed_url":"https://news.hada.io/rss/news","published":"2026-07-02T00:00:00Z","raw_path":"raw/1.html"}
                """);

        int imported = importer.importSourceIndex(file);

        Integer articleCount = jdbcTemplate.queryForObject("select count(*) from articles", Integer.class);
        Integer providerCount = jdbcTemplate.queryForObject("select count(*) from providers where name = 'GeekNews'", Integer.class);
        assertThat(imported).isEqualTo(1);
        assertThat(articleCount).isEqualTo(1);
        assertThat(providerCount).isEqualTo(1);
    }
}
