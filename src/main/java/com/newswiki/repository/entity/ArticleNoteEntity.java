package com.newswiki.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "article_notes")
public class ArticleNoteEntity {
    @Id
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "short_summary", nullable = false)
    private String shortSummary;

    @Lob
    @Column(name = "durable_knowledge", nullable = false)
    private String durableKnowledge;

    @Column(nullable = false)
    private String durability;

    @Column(name = "generated_by_job_id")
    private Long generatedByJobId;

    @Column(name = "generated_at", nullable = false)
    private String generatedAt;
}
