package com.newswiki.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiOutputParserTest {
    @Test
    void parsesArticleResults() throws Exception {
        String jsonl = new ClassPathResource("fixtures/ai/article-output.jsonl")
                .getContentAsString(StandardCharsets.UTF_8);

        var results = new AiOutputParser().parseArticles(jsonl);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().sourceId()).isEqualTo("abc123");
        assertThat(results.getFirst().topics().getFirst().slug()).isEqualTo("semiconductor-ai");
    }
}
