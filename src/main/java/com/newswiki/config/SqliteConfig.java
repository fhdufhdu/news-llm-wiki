package com.newswiki.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SqliteConfig {
    @Bean
    DatabasePragmas databasePragmas(JdbcTemplate jdbcTemplate) {
        return new DatabasePragmas(jdbcTemplate);
    }

    public static final class DatabasePragmas {
        public DatabasePragmas(JdbcTemplate jdbcTemplate) {
            jdbcTemplate.execute("PRAGMA journal_mode=WAL");
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
            jdbcTemplate.execute("PRAGMA busy_timeout=5000");
        }
    }
}
