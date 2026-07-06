package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.infrastructure.ai.CodexRunner;
import com.newswiki.infrastructure.ai.CodexWikiJobBuilder;
import com.newswiki.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodexWikiServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void runsCodexWikiJobWhenPendingArticlesExist() throws Exception {
        var runner = new RecordingCodexRunner(properties());
        var service = new CodexWikiService(
                new RecordingArticleRepository(120),
                new CodexWikiJobBuilder(),
                runner,
                properties()
        );
        List<String> logs = new ArrayList<>();

        var result = service.runPendingWikiBatch(10L, (level, message) -> logs.add(level + ":" + message));

        assertThat(result.inputCount()).isEqualTo(80);
        assertThat(result.detail()).contains("codex finished");
        assertThat(logs).anySatisfy(line -> assertThat(line).contains("Codex wiki job 시작"));
        assertThat(runner.promptFile).exists();
        assertThat(Files.readString(runner.promptFile)).contains("claim_articles(80)");
        assertThat(runner.workingDir.resolve("wiki_helper.py")).exists();
    }

    @Test
    void skipsWhenNoPendingArticlesExist() {
        var service = new CodexWikiService(
                new RecordingArticleRepository(0),
                new CodexWikiJobBuilder(),
                new RecordingCodexRunner(properties()),
                properties()
        );

        var result = service.runPendingWikiBatch(10L, (level, message) -> {
        });

        assertThat(result.inputCount()).isZero();
        assertThat(result.detail()).isEqualTo("no pending wiki articles");
    }

    private AppProperties properties() {
        return new AppProperties(
                tempDir.toString(),
                tempDir.resolve("codex").toString(),
                "gpt-5.5",
                "workspace-write",
                "0 30 3 * * *",
                5,
                3,
                80,
                1800,
                15,
                2,
                false,
                "SQLITE_TEXT"
        );
    }

    private static class RecordingArticleRepository extends ArticleRepository {
        private final int pending;

        RecordingArticleRepository(int pending) {
            super(null);
            this.pending = pending;
        }

        @Override
        public int countPendingWikiArticles() {
            return pending;
        }
    }

    private static class RecordingCodexRunner extends CodexRunner {
        Path workingDir;
        Path promptFile;

        RecordingCodexRunner(AppProperties properties) {
            super(properties);
        }

        @Override
        public Result run(Path workingDir, Path promptFile, Path outputFile, Duration timeout) {
            this.workingDir = workingDir;
            this.promptFile = promptFile;
            return new Result(0, "ok", "");
        }
    }
}
