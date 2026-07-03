package com.newswiki.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "articles")
public class ArticleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, unique = true)
    private String sourceId;

    @Column(name = "canonical_url", nullable = false, unique = true)
    private String canonicalUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(nullable = false)
    private String title;

    @Column(name = "feed_url", nullable = false)
    private String feedUrl;

    @Column(name = "published_at")
    private String publishedAt;

    @Column(name = "ingested_at", nullable = false)
    private String ingestedAt;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "raw_id")
    private Long rawId;

    @Column(name = "raw_status", nullable = false)
    private String rawStatus;

    @Column(name = "wiki_status", nullable = false)
    private String wikiStatus;

    @Column(name = "wiki_attempt_count", nullable = false)
    private Integer wikiAttemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "wiki_last_error")
    private String wikiLastError;
}
