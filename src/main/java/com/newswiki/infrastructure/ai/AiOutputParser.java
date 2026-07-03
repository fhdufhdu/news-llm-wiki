package com.newswiki.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.newswiki.dto.AiArticleResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AiOutputParser {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public List<AiArticleResult> parseArticles(String jsonl) {
        return Arrays.stream(jsonl.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(this::parseArticle)
                .toList();
    }

    private AiArticleResult parseArticle(String line) {
        try {
            AiArticleResult result = objectMapper.readValue(line, AiArticleResult.class);
            if (result.sourceId() == null || result.sourceId().isBlank()) {
                throw new IllegalArgumentException("source_id is required");
            }
            if (result.shortSummary() == null || result.shortSummary().isBlank()) {
                throw new IllegalArgumentException("short_summary is required for " + result.sourceId());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid AI article output: " + line, e);
        }
    }
}
