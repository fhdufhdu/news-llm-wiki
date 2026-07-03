package com.newswiki.dto;

import java.time.Instant;

public record JobRun(
        long id,
        String jobType,
        String status,
        Instant startedAt,
        Instant finishedAt,
        int inputCount,
        int outputCount,
        Integer exitCode,
        String errorMessage
) {
}
