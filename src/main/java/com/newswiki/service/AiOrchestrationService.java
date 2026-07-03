package com.newswiki.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newswiki.config.AppProperties;
import com.newswiki.dto.AiArticleResult;
import com.newswiki.infrastructure.ai.AiBatchBuilder;
import com.newswiki.infrastructure.ai.AiOutputParser;
import com.newswiki.infrastructure.ai.CodexRunner;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiOrchestrationService {
    private final AppProperties properties;
    private final AiBatchBuilder batchBuilder;
    private final CodexRunner codexRunner;
    private final AiOutputParser outputParser;
    private final ArticleRepository articleRepository;
    private final WikiRepository wikiRepository;
    private final ObjectMapper objectMapper;

    public AiOrchestrationService(
            AppProperties properties,
            AiBatchBuilder batchBuilder,
            CodexRunner codexRunner,
            AiOutputParser outputParser,
            ArticleRepository articleRepository,
            WikiRepository wikiRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.batchBuilder = batchBuilder;
        this.codexRunner = codexRunner;
        this.outputParser = outputParser;
        this.articleRepository = articleRepository;
        this.wikiRepository = wikiRepository;
        this.objectMapper = objectMapper;
    }

    public CodexRunner.Result runArticleBatch(List<String> articleJsonLines) {
        Path jobDir = Path.of(properties.dataDir(), "ai-jobs", "article-" + Instant.now().toEpochMilli());
        Path prompt = batchBuilder.writePrompt(jobDir, articleJsonLines);
        Path output = jobDir.resolve("last-message.txt");
        return codexRunner.run(Path.of(properties.dataDir()), prompt, output, Duration.ofSeconds(properties.aiTimeoutSeconds()));
    }

    public BatchResult runPendingArticleBatch(long jobRunId) {
        return runPendingArticleBatch(jobRunId, (level, message) -> {
        });
    }

    public BatchResult runPendingArticleBatch(long jobRunId, RssIngestService.IngestLogger logger) {
        List<ArticleRepository.PendingAiArticle> articles = articleRepository.findPendingAiArticles(properties.aiBatchSize());
        if (articles.isEmpty()) {
            return new BatchResult(0, 0, 0, "no pending articles");
        }

        logger.log("INFO", "AI batch 준비: pending " + articles.size() + "개");
        articleRepository.markAiRunning(articles.stream().map(ArticleRepository.PendingAiArticle::id).toList());
        Path jobDir = Path.of(properties.dataDir(), "ai-jobs", "article-" + Instant.now().toEpochMilli());
        List<String> inputs;
        try {
            logger.log("INFO", "AI batch raw materialize 시작: " + jobDir);
            inputs = articles.stream().map(article -> toInputJson(jobDir, article)).toList();
            logger.log("INFO", "AI batch raw materialize 완료: " + inputs.size() + "개");
        } catch (Exception e) {
            logger.log("ERROR", "AI batch raw materialize 실패: " + e.getMessage());
            articles.forEach(article -> articleRepository.markAiFailed(article.id(), e.getMessage()));
            return new BatchResult(articles.size(), 0, articles.size(), e.getMessage());
        }
        Path prompt = batchBuilder.writePrompt(jobDir, inputs);
        Path lastMessage = jobDir.resolve("last-message.txt");
        Path outputJsonl = jobDir.resolve("output.jsonl");
        Path progressJsonl = jobDir.resolve("progress.jsonl");
        logger.log("INFO", "AI batch Codex 시작: input=" + inputs.size()
                + "개, sandbox=" + properties.codexSandbox()
                + ", jobDir=" + jobDir);
        ProgressMonitor progressMonitor = startProgressMonitor(progressJsonl, outputJsonl, inputs.size(), logger);
        CodexRunner.Result runnerResult;
        try {
            runnerResult = codexRunner.run(
                    Path.of(properties.dataDir()),
                    prompt,
                    lastMessage,
                    Duration.ofSeconds(properties.aiTimeoutSeconds()),
                    outputJsonl,
                    inputs.size(),
                    Duration.ofSeconds(15)
            );
        } finally {
            progressMonitor.stop();
        }
        logger.log("INFO", "AI batch Codex 종료: exitCode=" + runnerResult.exitCode()
                + ", stdout=" + runnerResult.stdout().length()
                + " chars, stderr=" + runnerResult.stderr().length() + " chars");

        if (runnerResult.exitCode() != 0) {
            String error = runnerResult.stderr().isBlank() ? "Codex exited with " + runnerResult.exitCode() : runnerResult.stderr();
            logger.log("ERROR", "AI batch Codex 실패: " + summarize(error));
            articles.forEach(article -> articleRepository.markAiFailed(article.id(), error));
            return new BatchResult(articles.size(), 0, articles.size(), error);
        }

        String jsonl = readOutput(outputJsonl, lastMessage, runnerResult.stdout());
        logger.log("INFO", "AI batch output 읽기 완료: " + jsonl.length() + " chars");
        List<AiArticleResult> results;
        try {
            results = outputParser.parseArticles(jsonl);
            logger.log("INFO", "AI batch output 파싱 완료: " + results.size() + "개");
        } catch (Exception e) {
            logger.log("ERROR", "AI batch output 파싱 실패: " + e.getMessage());
            articles.forEach(article -> articleRepository.markAiFailed(article.id(), e.getMessage()));
            return new BatchResult(articles.size(), 0, articles.size(), e.getMessage());
        }
        Set<Long> succeededArticleIds = new HashSet<>();
        for (AiArticleResult result : results) {
            try {
                articleRepository.saveArticleNote(
                        result.articleId(),
                        result.shortSummary(),
                        objectMapper.writeValueAsString(result.durableKnowledge()),
                        result.durability(),
                        jobRunId
                );
                wikiRepository.linkAiResult(result);
                succeededArticleIds.add(result.articleId());
            } catch (Exception e) {
                logger.log("WARN", "AI batch 결과 저장 실패: articleId=" + result.articleId() + ", " + e.getMessage());
                articleRepository.markAiFailed(result.articleId(), e.getMessage());
            }
        }
        articles.stream()
                .filter(article -> !succeededArticleIds.contains(article.id()))
                .forEach(article -> articleRepository.markAiFailed(article.id(), "AI output did not include article result"));
        int succeeded = succeededArticleIds.size();
        int failed = Math.max(0, articles.size() - succeeded);
        logger.log("INFO", "AI batch DB 저장 완료: 성공 " + succeeded + "개, 실패 " + failed + "개");
        return new BatchResult(articles.size(), succeeded, failed, jobDir.toString());
    }

    private String toInputJson(Path jobDir, ArticleRepository.PendingAiArticle article) {
        try {
            Path rawPath = materializeRawForJob(jobDir, article);
            return objectMapper.writeValueAsString(Map.of(
                    "article_id", article.id(),
                    "source_id", article.sourceId(),
                    "original_url", article.canonicalUrl(),
                    "title", article.title(),
                    "provider", article.providerName(),
                    "published_at", article.publishedAt() == null ? "" : article.publishedAt(),
                    "raw_path", rawPath.toAbsolutePath().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI input for article " + article.id(), e);
        }
    }

    private Path materializeRawForJob(Path jobDir, ArticleRepository.PendingAiArticle article) {
        try {
            Path rawDir = jobDir.resolve("raw");
            Files.createDirectories(rawDir);
            Path rawPath = rawDir.resolve(article.sourceId() + ".html.gz");
            Files.write(rawPath, articleRepository.findRawGzipByRawId(article.rawId()));
            return rawPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize raw HTML for AI job article " + article.id(), e);
        }
    }

    private String readOutput(Path outputJsonl, Path lastMessage, String stdout) {
        try {
            if (Files.exists(outputJsonl)) {
                return Files.readString(outputJsonl);
            }
            if (Files.exists(lastMessage)) {
                return Files.readString(lastMessage);
            }
            return stdout;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read AI output", e);
        }
    }

    private ProgressMonitor startProgressMonitor(
            Path progressJsonl,
            Path outputJsonl,
            int expectedLines,
            RssIngestService.IngestLogger logger
    ) {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread thread = new Thread(() -> {
            int progressLines = 0;
            long lastOutputLines = -1;
            long lastHeartbeatAt = 0;
            while (running.get()) {
                try {
                    if (Files.exists(progressJsonl)) {
                        List<String> lines = Files.readAllLines(progressJsonl);
                        while (progressLines < lines.size()) {
                            String line = lines.get(progressLines++);
                            if (!line.isBlank()) {
                                logger.log("INFO", "AI progress: " + line);
                            }
                        }
                    }
                    long outputLines = countLines(outputJsonl);
                    long now = System.currentTimeMillis();
                    if (outputLines != lastOutputLines && outputLines > 0) {
                        logger.log("INFO", "AI batch output 진행: " + outputLines + "/" + expectedLines + " rows");
                        lastOutputLines = outputLines;
                        lastHeartbeatAt = now;
                    } else if (now - lastHeartbeatAt >= 30000) {
                        logger.log("INFO", "AI batch 진행 중: output " + Math.max(outputLines, 0) + "/" + expectedLines + " rows");
                        lastHeartbeatAt = now;
                    }
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    logger.log("WARN", "AI progress monitor 오류: " + e.getMessage());
                }
            }
        }, "newswiki-ai-progress");
        thread.setDaemon(true);
        thread.start();
        return new ProgressMonitor(running, thread);
    }

    private long countLines(Path file) {
        try {
            if (!Files.exists(file)) {
                return 0;
            }
            try (var lines = Files.lines(file)) {
                return lines.count();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    private String summarize(String value) {
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
    }

    private record ProgressMonitor(AtomicBoolean running, Thread thread) {
        void stop() {
            running.set(false);
            thread.interrupt();
        }
    }

    public record BatchResult(int inputCount, int succeeded, int failed, String detail) {
    }
}
