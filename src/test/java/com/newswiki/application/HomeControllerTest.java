package com.newswiki.application;

import com.newswiki.dto.CategoryNavItem;
import com.newswiki.dto.HomeView;
import com.newswiki.dto.ArticleListItem;
import com.newswiki.dto.WikiPageDetail;
import com.newswiki.dto.WikiPageListItem;
import com.newswiki.dto.WikiCategory;
import com.newswiki.service.NewsViewService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {
    private final NewsViewService newsViewService = new NewsViewService(null, null) {
        @Override
        public List<CategoryNavItem> categoryNav(String activeSlug) {
            return List.of(
                    new CategoryNavItem("industry-ai", "산업·AI", "industry-ai".equals(activeSlug)),
                    new CategoryNavItem("world", "국제", "world".equals(activeSlug))
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
        public List<WikiCategory> wikiCategories() {
            return List.of(new WikiCategory(1, "industry-ai", "산업·AI", "AI 흐름", 10));
        }

        @Override
        public List<WikiPageListItem> recentWikiPages(int limit) {
            return List.of(new WikiPageListItem(1, "gpu-power", "GPU 전력", "요약", 90, "now"));
        }

        @Override
        public List<WikiPageListItem> wikiPagesByCategory(String categorySlug) {
            return List.of(new WikiPageListItem(1, "gpu-power", "GPU 전력", "요약", 90, "now"));
        }

        @Override
        public WikiPageDetail wikiPage(String slug) {
            return new WikiPageDetail(1, slug, "GPU 전력", "요약", "본문", 90, "now", List.of());
        }
    };

    @Test
    void homeUsesDatabaseCategoryNav() {
        var model = new ExtendedModelMap();
        var viewName = new HomeController(newsViewService).home(model);

        assertThat(viewName).isEqualTo("pages/home");
        assertThat(model.get("categoryNav").toString()).contains("산업·AI", "국제");
        assertThat(model.get("pages").toString()).contains("GPU 전력");
    }

    @Test
    void categoryUsesDatabaseCategoryNav() {
        var model = new ExtendedModelMap();
        var viewName = new CategoryController(newsViewService).category("industry-ai", model);

        assertThat(viewName).isEqualTo("pages/categories");
        assertThat(model.get("categoryTitle")).isEqualTo("산업·AI");
        assertThat(model.get("categoryNav").toString()).contains("active=true");
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
