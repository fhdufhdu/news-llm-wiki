package com.newswiki.application;

import com.newswiki.dto.ArticleImportItem;
import com.newswiki.dto.ArticleImportJob;
import com.newswiki.dto.ArticleImportProgress;
import com.newswiki.dto.SectionNavItem;
import com.newswiki.service.ArticleImportService;
import com.newswiki.service.NewsViewService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportControllerTest {
    @Test
    void submitStartsImportAndRedirectsToDetail() {
        var importService = new RecordingArticleImportService();
        var controller = new ImportController(importService, newsViewService());

        String view = controller.submit("https://example.com/a");

        assertThat(view).isEqualTo("redirect:/imports/7");
        assertThat(importService.submittedUrls).isEqualTo("https://example.com/a");
        assertThat(importService.asyncJobId).isEqualTo(7L);
    }

    @Test
    void detailAddsProgressToModel() {
        var controller = new ImportController(new RecordingArticleImportService(), newsViewService());
        var model = new ExtendedModelMap();

        String view = controller.detail(7L, model);

        assertThat(view).isEqualTo("pages/import-detail");
        assertThat(model.get("progress")).isInstanceOf(ArticleImportProgress.class);
    }

    private NewsViewService newsViewService() {
        return new NewsViewService(null, null) {
            @Override
            public List<SectionNavItem> sectionNav(String activeSlug) {
                return List.of(new SectionNavItem("ai", "AI", false));
            }
        };
    }

    private static class RecordingArticleImportService extends ArticleImportService {
        String submittedUrls;
        long asyncJobId;

        RecordingArticleImportService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public long submitUrls(String rawUrls) {
            this.submittedUrls = rawUrls;
            return 7L;
        }

        @Override
        public void runImportJobAsync(long jobId) {
            this.asyncJobId = jobId;
        }

        @Override
        public ArticleImportProgress findProgress(long jobId) {
            return new ArticleImportProgress(
                    new ArticleImportJob(jobId, "RUNNING", 1, 1, 0, 0, "created", "started", null, "running"),
                    List.of(new ArticleImportItem(1, jobId, "https://example.com/a", "https://example.com/a", 10L,
                            "WIKI_PENDING", null, 0, "created", "updated"))
            );
        }

        @Override
        public List<ArticleImportJob> findRecentJobs() {
            return List.of();
        }
    }
}
