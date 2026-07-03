package com.newswiki.infrastructure.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlCanonicalizerTest {
    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    @Test
    void removesTrackingParametersAndFragments() {
        String input = "https://Example.com/news/a?utm_source=rss&b=2&fbclid=x#comments";

        String result = canonicalizer.canonicalize(input);

        assertThat(result).isEqualTo("https://example.com/news/a?b=2");
    }

    @Test
    void preservesMeaningfulQueryOrder() {
        String input = "https://news.example.com/read?id=10&section=it&utm_medium=feed";

        String result = canonicalizer.canonicalize(input);

        assertThat(result).isEqualTo("https://news.example.com/read?id=10&section=it");
    }
}
