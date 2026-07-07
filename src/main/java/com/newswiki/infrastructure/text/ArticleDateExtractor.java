package com.newswiki.infrastructure.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class ArticleDateExtractor {
    private static final List<String> META_SELECTORS = List.of(
            "meta[property=article:published_time]",
            "meta[name=article:published_time]",
            "meta[property=og:article:published_time]",
            "meta[name=pubdate]",
            "meta[name=date]",
            "meta[name=Date]",
            "meta[name=datePublished]",
            "meta[itemprop=datePublished]",
            "meta[name=parsely-pub-date]",
            "meta[name=sailthru.date]"
    );

    public Optional<Instant> extractPublishedAt(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(html);
        for (String selector : META_SELECTORS) {
            Optional<Instant> parsed = parse(document.select(selector).stream()
                    .map(element -> element.attr("content"))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        for (Element time : document.select("time[datetime]")) {
            Optional<Instant> parsed = parse(time.attr("datetime"));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        try {
            return Optional.of(Instant.parse(normalized));
        } catch (Exception ignored) {
        }
        try {
            return Optional.of(OffsetDateTime.parse(normalized).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Optional.of(LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneId.of("Asia/Seoul"))
                    .toInstant());
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
