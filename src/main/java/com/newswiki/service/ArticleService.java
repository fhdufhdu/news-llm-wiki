package com.newswiki.service;

import com.newswiki.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

@Service
public class ArticleService {
    private final ArticleRepository repository;

    public ArticleService(ArticleRepository repository) {
        this.repository = repository;
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash article HTML", e);
        }
    }

    public Path writeGzipRaw(Path dataDir, String sourceId, Instant publishedAt, String html) {
        try {
            Instant date = publishedAt == null ? Instant.now() : publishedAt;
            String day = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC).format(date);
            Path dir = dataDir.resolve("raw").resolve(day);
            Files.createDirectories(dir);
            Path target = dir.resolve(sourceId + ".html.gz");
            try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(target))) {
                gzip.write(html.getBytes(StandardCharsets.UTF_8));
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write raw article HTML", e);
        }
    }

    public long recordRawFile(long articleId, Path rawPath, String contentType, int httpStatus) {
        try {
            byte[] htmlGzip = Files.readAllBytes(rawPath);
            long rawId = repository.insertRawGzip(articleId, htmlGzip, contentType, httpStatus);
            Files.deleteIfExists(rawPath);
            return rawId;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist raw article HTML to DB", e);
        }
    }
}
