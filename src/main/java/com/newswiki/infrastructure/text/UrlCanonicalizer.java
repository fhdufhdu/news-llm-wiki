package com.newswiki.infrastructure.text;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class UrlCanonicalizer {
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "fbclid", "gclid", "igshid", "mc_cid", "mc_eid"
    );

    public String canonicalize(String url) {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String query = normalizeQuery(uri.getRawQuery());
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();

        return URI.create(scheme + "://" + host + port
                        + nullToEmpty(uri.getRawPath())
                        + (query.isBlank() ? "" : "?" + query))
                .toString();
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length > 0 && !isTrackingParam(decode(pair[0])))
                .map(pair -> encode(decode(pair[0])) + (pair.length == 2 ? "=" + encode(decode(pair[1])) : ""))
                .collect(Collectors.joining("&"));
    }

    private boolean isTrackingParam(String key) {
        String lower = key.toLowerCase();
        return lower.startsWith("utm_") || TRACKING_PARAMS.contains(lower);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
