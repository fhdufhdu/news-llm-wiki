package com.newswiki.infrastructure.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
public class ExistingCorpusImporter {
    private final ObjectMapper objectMapper;
    private final ArticleRepository articleRepository;
    private final WikiRepository wikiRepository;

    public ExistingCorpusImporter(ObjectMapper objectMapper, ArticleRepository articleRepository, WikiRepository wikiRepository) {
        this.objectMapper = objectMapper;
        this.articleRepository = articleRepository;
        this.wikiRepository = wikiRepository;
    }

    public int importSourceIndex(Path sourceIndexJsonl) {
        try (var lines = Files.lines(sourceIndexJsonl)) {
            return lines.filter(line -> !line.isBlank())
                    .mapToInt(this::importLine)
                    .sum();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import " + sourceIndexJsonl, e);
        }
    }

    private int importLine(String line) {
        try {
            SourceIndexRow row = objectMapper.readValue(line, SourceIndexRow.class);
            long providerId = wikiRepository.upsertProviderByName(row.publisher());
            articleRepository.insertArticleIfAbsent(
                    row.source_id(),
                    row.canonical_url(),
                    providerId,
                    row.title(),
                    row.feed_url(),
                    row.published() == null || row.published().isBlank() ? null : Instant.parse(row.published()),
                    ""
            );
            return 1;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid source-index row: " + line, e);
        }
    }

    public record SourceIndexRow(
            String source_id,
            String canonical_url,
            String title,
            String publisher,
            String feed_url,
            String published,
            String raw_path
    ) {
    }
}
