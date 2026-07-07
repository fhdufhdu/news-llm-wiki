package com.newswiki.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodexWikiJobBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void writesPromptAndPythonHelper() throws Exception {
        var builder = new CodexWikiJobBuilder();

        Path jobDir = builder.build(tempDir, "/app/data/newswiki.sqlite", 123L, 80);

        String prompt = Files.readString(jobDir.resolve("prompt.md"));
        String helper = Files.readString(jobDir.resolve("wiki_helper.py"));

        assertThat(prompt).contains("claim_articles(80)");
        assertThat(prompt).contains("기사 1건마다 SQLite에 즉시 반영");
        assertThat(prompt).contains("Major categories are wiki_sections.fixed=1");
        assertThat(prompt).contains("Subcategories are wiki_sections.fixed=0");
        assertThat(helper).contains("DB = \"/app/data/newswiki.sqlite\"");
        assertThat(helper).contains("JOB_RUN_ID = 123");
        assertThat(helper).contains("def claim_articles");
        assertThat(helper).contains("def list_major_categories");
        assertThat(helper).contains("def list_subcategories");
        assertThat(helper).contains("def create_subcategory");
        assertThat(helper).contains("def create_section");
        assertThat(helper).contains("def update_subcategory");
        assertThat(helper).contains("def update_section");
        assertThat(helper).contains("def delete_subcategory");
        assertThat(helper).contains("def delete_section");
        assertThat(helper).contains("def upsert_page");
        assertThat(helper).contains("def link_source");
        assertThat(helper).contains("def add_revision");
        assertThat(helper).contains("def progress");
        assertThat(helper).contains("def start_wiki_run");
        assertThat(helper).contains("def finish_wiki_run");
    }
}
