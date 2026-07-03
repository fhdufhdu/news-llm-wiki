package com.newswiki.infrastructure.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class AiBatchBuilder {
    public Path writePrompt(Path jobDir, List<String> articleJsonLines) {
        try {
            Files.createDirectories(jobDir);
            Path input = jobDir.resolve("input.jsonl");
            Path prompt = jobDir.resolve("prompt.md");
            Path output = jobDir.resolve("output.jsonl");
            Path progress = jobDir.resolve("progress.jsonl");
            Files.write(input, articleJsonLines);
            Files.writeString(prompt, """
                    You are generating News Wiki article notes using the LLM Wiki pattern.
                    Read this input JSONL file:
                    %s
                    Emit one JSONL row per input article to this output file:
                    %s
                    Also append structured progress JSONL rows to this progress file while you work:
                    %s

                    Progress log contract:
                    - Append one JSON object per line.
                    - Log at least: started, input_read, raw_review_progress every 10 articles, output_write_started, output_write_completed.
                    - Progress row schema:
                      {"event":"started|input_read|raw_review_progress|output_write_started|output_write_completed","processed":0,"total":80,"message":"short Korean status"}

                    Output row schema is strict. Every output line must be one JSON object with exactly these fields:
                    {
                      "article_id": 123,
                      "source_id": "source-id-from-input",
                      "short_summary": "한국어 한 문장 요약",
                      "durability": "transient|durable",
                      "durable_knowledge": ["원문에서 확인한 지속 지식 또는 맥락"],
                      "topics": [{"slug":"topic-slug","title":"표시 이름","name":"표시 이름"}],
                      "entities": [{"slug":"entity-slug","title":"표시 이름","name":"표시 이름"}],
                      "events": [{"slug":"event-slug","title":"표시 이름","name":"표시 이름"}],
                      "claims": [{"slug":"claim-slug","title":"표시 이름","name":"표시 이름"}]
                    }

                    Each input row contains article_id, source_id, original_url, title, provider, published_at, and raw_path.
                    Open and review the saved raw_path HTML files. Do not summarize only the metadata.
                    Keep Korean summaries concise.
                    Preserve source_id and article_id.
                    Do not fetch the internet.
                    Use only saved raw source HTML and metadata provided in the input packet.
                    Create a source-level note for every article, even when it is transient.
                    Link durable knowledge to existing or proposed topics, entities, events, and claims.
                    Distinguish verified facts, reported claims, allegations, forecasts, and measured values.
                    Do not emit markdown, prose, arrays, or wrapper objects to output.jsonl. Only JSONL rows matching the strict schema.
                    """.formatted(input.toAbsolutePath(), output.toAbsolutePath(), progress.toAbsolutePath()));
            return prompt;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write AI batch", e);
        }
    }
}
