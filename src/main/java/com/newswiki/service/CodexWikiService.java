package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.dto.WikiJobResult;
import com.newswiki.infrastructure.ai.CodexRunner;
import com.newswiki.infrastructure.ai.CodexWikiJobBuilder;
import com.newswiki.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CodexWikiService {
    private final ArticleRepository articleRepository;
    private final CodexWikiJobBuilder jobBuilder;
    private final CodexRunner codexRunner;
    private final AppProperties properties;

    public WikiJobResult runPendingWikiBatch(long jobRunId, WikiLogger logger) {
        int pending = articleRepository.countPendingWikiArticles();
        if (pending == 0) {
            return new WikiJobResult(0, 0, 0, "no pending wiki articles");
        }

        int input = Math.min(pending, properties.aiBatchSize());
        Path jobDir = jobBuilder.build(
                Path.of(properties.dataDir(), "wiki-jobs"),
                Path.of(properties.dataDir(), "newswiki.sqlite").toString(),
                jobRunId,
                input
        );
        Path promptFile = jobDir.resolve("prompt.md");
        Path outputFile = jobDir.resolve("last-message.txt");
        logger.log("INFO", "Codex wiki job 시작: input=" + input
                + "개, sandbox=" + properties.codexSandbox()
                + ", jobDir=" + jobDir);

        CodexRunner.Result result = codexRunner.run(
                jobDir,
                promptFile,
                outputFile,
                Duration.ofSeconds(properties.aiTimeoutSeconds())
        );
        if (result.exitCode() == 0) {
            return new WikiJobResult(input, 0, 0, "codex finished; article-level counts are stored in DB");
        }
        String detail = result.stderr() == null || result.stderr().isBlank() ? result.stdout() : result.stderr();
        return new WikiJobResult(input, 0, input, detail);
    }

    public interface WikiLogger {
        void log(String level, String message);
    }
}
