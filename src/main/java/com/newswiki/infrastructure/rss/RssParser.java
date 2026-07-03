package com.newswiki.infrastructure.rss;

import com.newswiki.dto.RssEntry;
import com.rometools.rome.io.SyndFeedInput;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;

@Component
public class RssParser {
    public List<RssEntry> parse(String xml) {
        try {
            var feed = new SyndFeedInput().build(new InputSource(new StringReader(xml)));
            return feed.getEntries().stream()
                    .map(entry -> new RssEntry(
                            entry.getTitle(),
                            entry.getLink(),
                            entry.getUri(),
                            entry.getPublishedDate() == null ? "" : entry.getPublishedDate().toInstant().toString(),
                            entry.getDescription() == null ? "" : entry.getDescription().getValue()
                    ))
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RSS feed", e);
        }
    }
}
