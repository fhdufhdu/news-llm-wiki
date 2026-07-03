package com.newswiki.infrastructure.rss;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RssIndexExpander {
    public List<String> extractFeedUrls(String indexUrl, String html) {
        return Jsoup.parse(html, indexUrl)
                .select("a[href]")
                .stream()
                .map(element -> element.absUrl("href"))
                .filter(this::looksLikeFeed)
                .distinct()
                .limit(100)
                .toList();
    }

    private boolean looksLikeFeed(String url) {
        String lower = url.toLowerCase();
        return lower.contains("rss")
                || lower.contains("feed")
                || lower.endsWith(".xml");
    }
}
