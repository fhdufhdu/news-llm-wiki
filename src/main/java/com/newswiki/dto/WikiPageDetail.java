package com.newswiki.dto;

import java.util.List;

public record WikiPageDetail(
        long id,
        String slug,
        String title,
        String summary,
        String body,
        int importance,
        String updatedAt,
        List<WikiSourceRef> sources
) {
}
