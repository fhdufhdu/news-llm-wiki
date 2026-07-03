package com.newswiki.dto;

import java.util.List;

public record AiArticleResult(
        long articleId,
        String sourceId,
        String shortSummary,
        String durability,
        List<String> durableKnowledge,
        List<AiLink> topics,
        List<AiLink> entities,
        List<AiLink> events,
        List<AiLink> claims
) {
    public AiArticleResult {
        durableKnowledge = durableKnowledge == null ? List.of() : List.copyOf(durableKnowledge);
        topics = topics == null ? List.of() : List.copyOf(topics);
        entities = entities == null ? List.of() : List.copyOf(entities);
        events = events == null ? List.of() : List.copyOf(events);
        claims = claims == null ? List.of() : List.copyOf(claims);
    }

    public record AiLink(String slug, String title, String name) {
    }
}
