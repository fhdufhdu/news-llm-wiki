package com.newswiki.infrastructure.ai;

import com.newswiki.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class CodexRunner {
    private final AppProperties properties;

    public CodexRunner(AppProperties properties) {
        this.properties = properties;
    }

    public Result run(Path workingDir, Path promptFile, Path outputFile, Duration timeout) {
        return run(workingDir, promptFile, outputFile, timeout, null, 0, Duration.ZERO);
    }

    public Result run(
            Path workingDir,
            Path promptFile,
            Path outputFile,
            Duration timeout,
            Path completionFile,
            int expectedLines,
            Duration completionGrace
    ) {
        ArrayList<String> command = new ArrayList<>();
        command.add("codex");
        command.add("exec");
        command.add("--skip-git-repo-check");
        command.add("--sandbox");
        command.add(properties.codexSandbox());
        command.add("-C");
        command.add(workingDir.toString());
        command.add("-m");
        command.add(properties.codexModel());
        command.add("-o");
        command.add(outputFile.toString());
        command.add("-");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        Map<String, String> env = builder.environment();
        env.put("CODEX_HOME", properties.codexHome());
        Path codexHome = Path.of(properties.codexHome());
        Path tmpDir = codexHome.resolve("tmp");
        env.put("HOME", codexHome.getParent() == null ? properties.codexHome() : codexHome.getParent().toString());
        env.put("XDG_CACHE_HOME", codexHome.resolve("cache").toString());
        env.put("XDG_CONFIG_HOME", codexHome.resolve("config").toString());
        env.put("XDG_DATA_HOME", codexHome.resolve("data").toString());
        env.put("XDG_STATE_HOME", codexHome.resolve("state").toString());
        env.put("TMPDIR", tmpDir.toString());
        builder.redirectInput(promptFile.toFile());
        Path stdoutFile = outputFile.resolveSibling(outputFile.getFileName() + ".stdout.log");
        Path stderrFile = outputFile.resolveSibling(outputFile.getFileName() + ".stderr.log");
        builder.redirectOutput(stdoutFile.toFile());
        builder.redirectError(stderrFile.toFile());

        try {
            Files.createDirectories(codexHome);
            Files.createDirectories(codexHome.resolve("cache"));
            Files.createDirectories(codexHome.resolve("config"));
            Files.createDirectories(codexHome.resolve("data"));
            Files.createDirectories(codexHome.resolve("state"));
            Files.createDirectories(tmpDir);
            Process process = builder.start();
            Instant deadline = Instant.now().plus(timeout);
            Instant completedOutputAt = null;
            while (true) {
                if (process.waitFor(1, TimeUnit.SECONDS)) {
                    String stdout = readFileIfExists(stdoutFile);
                    String stderr = readFileIfExists(stderrFile);
                    return new Result(process.exitValue(), stdout, stderr);
                }
                if (completionFile != null && expectedLines > 0 && countLines(completionFile) >= expectedLines) {
                    if (completedOutputAt == null) {
                        completedOutputAt = Instant.now();
                    } else if (!Instant.now().isBefore(completedOutputAt.plus(completionGrace))) {
                        process.destroy();
                        if (!process.waitFor(3, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                            process.waitFor(3, TimeUnit.SECONDS);
                        }
                        String stdout = readFileIfExists(stdoutFile);
                        String stderr = readFileIfExists(stderrFile);
                        String note = "Codex process stopped after output completion: "
                                + expectedLines + " lines in " + completionFile;
                        return new Result(0, stdout, stderr.isBlank() ? note : stderr + "\n" + note);
                    }
                }
                if (Instant.now().isAfter(deadline)) {
                    process.destroyForcibly();
                    String stdout = readFileIfExists(stdoutFile);
                    String stderr = readFileIfExists(stderrFile);
                    return new Result(-1, stdout, stderr.isBlank() ? "Codex process timed out" : stderr + "\nCodex process timed out");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start codex process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for codex process", e);
        }
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

    private String readFileIfExists(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file) : "";
    }

    public record Result(int exitCode, String stdout, String stderr) {
    }
}
