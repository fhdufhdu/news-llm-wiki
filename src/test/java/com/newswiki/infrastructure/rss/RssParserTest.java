package com.newswiki.infrastructure.rss;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RssParserTest {
    @Test
    void parsesRssItems() throws Exception {
        String xml = new ClassPathResource("fixtures/rss/sample-feed.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        var entries = new RssParser().parse(xml);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().title()).isEqualTo("AI 반도체 투자 확대");
        assertThat(entries.getFirst().url()).isEqualTo("https://example.com/article/1?utm_source=rss");
        assertThat(entries.getFirst().summary()).contains("AI 인프라");
    }
}
