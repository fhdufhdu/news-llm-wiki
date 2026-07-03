package com.newswiki.repository.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "article_raw")
public class ArticleRawEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, unique = true)
    private Long articleId;

    @Column(name = "storage_mode", nullable = false)
    private String storageMode;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String html;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "html_gzip")
    private byte[] htmlGzip;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "fetched_at", nullable = false)
    private String fetchedAt;
}
