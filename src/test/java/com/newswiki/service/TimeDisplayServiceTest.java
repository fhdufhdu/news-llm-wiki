package com.newswiki.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDisplayServiceTest {
    private final TimeDisplayService service = new TimeDisplayService();

    @Test
    void formatsUtcInstantAsKoreanSeoulTime() {
        assertThat(service.format("2026-07-06T23:38:26Z"))
                .isEqualTo("2026년 7월 7일 08:38");
    }

    @Test
    void formatsSqliteUtcDateTimeAsKoreanSeoulTime() {
        assertThat(service.format("2026-07-06 23:38:26"))
                .isEqualTo("2026년 7월 7일 08:38");
    }

    @Test
    void keepsUnparseableText() {
        assertThat(service.format("생성 대기")).isEqualTo("생성 대기");
    }
}
