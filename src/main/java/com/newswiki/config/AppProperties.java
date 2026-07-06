package com.newswiki.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "newswiki")
public record AppProperties(
        @NotBlank String dataDir,
        @NotBlank String codexHome,
        @NotBlank String codexModel,
        @NotBlank String codexSandbox,
        @NotBlank String dailyRebuildCron,
        @Min(1) int dailyRebuildMaxBatches,
        @Min(1) int aiMaxRetries,
        @Min(1) int aiBatchSize,
        @Min(30) int aiTimeoutSeconds,
        @Min(1) int articleFetchTimeoutSeconds,
        @Min(1) int articleFetchMaxRetries,
        boolean articleFetchInsecureSsl,
        @NotBlank String rawStorageMode
) {
}
