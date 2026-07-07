package com.newswiki.application;

import com.newswiki.dto.SectionNavItem;
import com.newswiki.dto.HomeView;
import com.newswiki.dto.ArticleListItem;
import com.newswiki.dto.WikiPageDetail;
import com.newswiki.dto.WikiPageListItem;
import com.newswiki.dto.WikiSection;
import com.newswiki.service.NewsViewService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {
    private final NewsViewService newsViewService = new NewsViewService(null, null) {
        @Override
        public List<SectionNavItem> sectionNav(String activeSlug) {
            return List.of(
                    new SectionNavItem("industry-ai", "산업·AI", "industry-ai".equals(activeSlug)),
                    new SectionNavItem("world", "국제", "world".equals(activeSlug))
            );
        }

        @Override
        public HomeView home() {
            return new HomeView("오늘의 뉴스", "now", 0, "요약", List.of());
        }

        @Override
        public List<ArticleListItem> latestArticles() {
            return List.of();
        }

        @Override
        public List<WikiSection> wikiSections() {
            return List.of(new WikiSection(1, "industry-ai", "산업·AI", "AI 흐름", 10));
        }

        @Override
        public List<WikiPageListItem> recentWikiPages(int limit) {
            return List.of(new WikiPageListItem(1, "gpu-power", "GPU 전력", "요약", 90, "now"));
        }

        @Override
        public List<WikiPageListItem> wikiPagesBySection(String sectionSlug) {
            return List.of(new WikiPageListItem(1, "gpu-power", "GPU 전력", "요약", 90, "now"));
        }

        @Override
        public WikiPageDetail wikiPage(String slug) {
            return new WikiPageDetail(1, slug, "GPU 전력", "요약", "본문", 90, "now", List.of());
        }
    };

    @Test
    void homeUsesDatabaseSectionNav() {
        var model = new ExtendedModelMap();
        var viewName = new HomeController(newsViewService).home(model);

        assertThat(viewName).isEqualTo("pages/home");
        assertThat(model.get("sectionNav").toString()).contains("산업·AI", "국제");
        assertThat(model.get("pages").toString()).contains("GPU 전력");
    }

    @Test
    void sectionUsesDatabaseSectionNav() {
        var model = new ExtendedModelMap();
        var viewName = new SectionController(newsViewService).section("industry-ai", model);

        assertThat(viewName).isEqualTo("pages/sections");
        assertThat(model.get("sectionTitle")).isEqualTo("산업·AI");
        assertThat(model.get("sectionNav").toString()).contains("active=true");
        assertThat(model.get("pages").toString()).contains("GPU 전력");
    }

    @Test
    void wikiPageRendersWikiDocument() {
        var model = new ExtendedModelMap();
        var viewName = new WikiController(newsViewService, new com.newswiki.service.WikiService()).wikiPage("gpu-power", model);

        assertThat(viewName).isEqualTo("pages/wiki-detail");
        assertThat(model.get("page").toString()).contains("GPU 전력");
    }
}
