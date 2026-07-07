package com.newswiki.repository;

import com.newswiki.dto.ArticleImportItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:article_import_repository_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ArticleImportRepositoryTest {
    @Autowired
    ArticleImportRepository repository;

    @Autowired
    ArticleRepository articleRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from article_import_items");
        jdbcTemplate.update("delete from article_import_jobs");
        jdbcTemplate.update("delete from article_raw_sources");
        jdbcTemplate.update("delete from articles");
    }

    @Test
    void createsJobAndDeduplicatesInputUrls() {
        long jobId = repository.createJob(List.of(
                "https://example.com/a",
                "https://example.com/a",
                " https://example.com/b "
        ));

        var job = repository.findJob(jobId).orElseThrow();
        var items = repository.findItems(jobId);

        assertThat(job.totalCount()).isEqualTo(2);
        assertThat(items).extracting(ArticleImportItem::inputUrl)
                .containsExactly("https://example.com/a", "https://example.com/b");
        assertThat(items).extracting(ArticleImportItem::status)
                .containsExactly("PENDING", "PENDING");
    }

    @Test
    void updatesItemStatusAndProgressCounts() {
        long jobId = repository.createJob(List.of("https://example.com/a"));
        long itemId = repository.findItems(jobId).getFirst().id();
        long articleId = articleRepository.insertArticleIfAbsent(
                "manual-test",
                "https://example.com/a",
                "테스트",
                "manual",
                null,
                "hash"
        );

        repository.markFetching(itemId);
        repository.markFetched(itemId, "https://example.com/a", articleId);
        repository.markWikiPending(itemId);
        repository.markDone(itemId);

        var job = repository.findJob(jobId).orElseThrow();
        var item = repository.findItems(jobId).getFirst();

        assertThat(job.fetchedCount()).isEqualTo(1);
        assertThat(job.wikiDoneCount()).isEqualTo(1);
        assertThat(job.failedCount()).isZero();
        assertThat(item.status()).isEqualTo("DONE");
        assertThat(item.articleId()).isEqualTo(articleId);
    }
}
