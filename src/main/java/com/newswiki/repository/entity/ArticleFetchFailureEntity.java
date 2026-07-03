package com.newswiki.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "article_fetch_failures")
public class ArticleFetchFailureEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_url", nullable = false, unique = true)
    private String canonicalUrl;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "feed_url", nullable = false)
    private String feedUrl;

    private String title;

    @Column(name = "published_at")
    private String publishedAt;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    @Column(nullable = false)
    private String status;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_attempt_at", nullable = false)
    private String lastAttemptAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;
}
