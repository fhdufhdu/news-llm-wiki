package com.newswiki.infrastructure.rss;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RssIndexExpanderTest {
    @Test
    void extractsAbsoluteFeedUrlsFromIndexPage() {
        var urls = new RssIndexExpander().extractFeedUrls("https://example.com/help/rss.html", """
                <html>
                  <body>
                    <a href="/rss/all.xml">전체</a>
                    <a href="https://example.com/feed/tech">기술</a>
                    <a href="/news/1">기사</a>
                  </body>
                </html>
                """);

        assertThat(urls).containsExactly(
                "https://example.com/rss/all.xml",
                "https://example.com/feed/tech"
        );
    }
}
