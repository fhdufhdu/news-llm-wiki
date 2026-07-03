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
@Table(name = "job_runs")
public class JobRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private String startedAt;

    @Column(name = "finished_at")
    private String finishedAt;

    @Column(name = "input_count", nullable = false)
    private Integer inputCount;

    @Column(name = "output_count", nullable = false)
    private Integer outputCount;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message")
    private String errorMessage;
}
