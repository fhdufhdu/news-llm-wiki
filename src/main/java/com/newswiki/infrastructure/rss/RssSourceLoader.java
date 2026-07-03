package com.newswiki.infrastructure.rss;

import com.newswiki.dto.RssSource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class RssSourceLoader {
    @SuppressWarnings("unchecked")
    public List<RssSource> load(Path path) {
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Object> root = yaml.load(reader);
            List<Map<String, Object>> sources = (List<Map<String, Object>>) root.getOrDefault("sources", List.of());
            return sources.stream()
                    .map(this::toSource)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load RSS sources from " + path, e);
        }
    }

    private RssSource toSource(Map<String, Object> row) {
        String sourceKey = String.valueOf(row.get("id"));
        return new RssSource(
                sourceKey,
                String.valueOf(row.getOrDefault("provider_slug", sourceKey)),
                String.valueOf(row.get("name")),
                String.valueOf(row.get("url")),
                String.valueOf(row.getOrDefault("type", "rss")),
                String.valueOf(row.getOrDefault("categories", "_all_")),
                Boolean.parseBoolean(String.valueOf(row.getOrDefault("enabled", "true")))
        );
    }
}
