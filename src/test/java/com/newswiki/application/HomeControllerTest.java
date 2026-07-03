package com.newswiki.application;

import com.newswiki.dto.SectionNavItem;
import com.newswiki.dto.HomeView;
import com.newswiki.dto.Provider;
import com.newswiki.dto.ArticleListItem;
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
        public List<Provider> providers() {
            return List.of();
        }

        @Override
        public List<ArticleListItem> latestArticles() {
            return List.of();
        }
    };

    @Test
    void homeUsesDatabaseSectionNav() {
        var model = new ExtendedModelMap();
        var viewName = new HomeController(newsViewService).home(model);

        assertThat(viewName).isEqualTo("pages/home");
        assertThat(model.get("sectionNav").toString()).contains("산업·AI", "국제");
        assertThat(model.get("sectionNav").toString()).doesNotContain("GeekNews");
    }

    @Test
    void sectionUsesDatabaseSectionNav() {
        var model = new ExtendedModelMap();
        var viewName = new SectionController(newsViewService).section("industry-ai", model);

        assertThat(viewName).isEqualTo("pages/sections");
        assertThat(model.get("sectionTitle")).isEqualTo("산업·AI");
        assertThat(model.get("sectionNav").toString()).contains("active=true");
    }
}
