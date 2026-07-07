package com.newswiki.application;

import com.newswiki.repository.ArticleRepository;
import com.newswiki.service.NewsViewService;
import com.newswiki.dto.CategoryNavItem;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleControllerTest {
    @Test
    void returnsArticleDetailViewForExistingArticle() {
        var controller = new ArticleController(new NewsViewService(null, null) {
            @Override
            public List<CategoryNavItem> categoryNav(String activeSlug) {
                return List.of();
            }

            @Override
            public ArticleRepository.ArticleDetailView article(long id) {
                return new ArticleRepository.ArticleDetailView(
                        id,
                        "제목",
                        "https://example.com/article",
                        "https://example.com/rss",
                        "2026-07-03T00:00:00Z",
                        "2026-07-03T00:01:00Z",
                        "FETCHED",
                        "DONE"
                );
            }
        });
        var model = new ExtendedModelMap();

        String view = controller.article(1, model);

        assertThat(view).isEqualTo("pages/article-detail");
        assertThat(model.get("article")).isNotNull();
    }

    @Test
    void returns404ForMissingArticle() {
        var controller = new ArticleController(new NewsViewService(null, null) {
            @Override
            public ArticleRepository.ArticleDetailView article(long id) {
                return null;
            }
        });

        assertThatThrownBy(() -> controller.article(404, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }
}
