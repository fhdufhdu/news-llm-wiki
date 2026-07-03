package com.newswiki.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:job_lock_service_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class JobLockServiceTest {
    @Autowired
    JobLockService locks;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from job_locks");
    }

    @Test
    void preventsOverlappingLocks() {
        boolean first = locks.tryAcquire("ingest", "worker-a", Duration.ofMinutes(10));
        boolean second = locks.tryAcquire("ingest", "worker-b", Duration.ofMinutes(10));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }
}
