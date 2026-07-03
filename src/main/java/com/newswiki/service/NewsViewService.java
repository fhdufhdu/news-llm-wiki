package com.newswiki.service;

import com.newswiki.dto.ArticleListItem;
import com.newswiki.dto.HomeView;
import com.newswiki.dto.Provider;
import com.newswiki.dto.SectionNavItem;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NewsViewService {
    private final ArticleRepository articleRepository;
    private final WikiRepository wikiRepository;

    public NewsViewService(ArticleRepository articleRepository, WikiRepository wikiRepository) {
        this.articleRepository = articleRepository;
        this.wikiRepository = wikiRepository;
    }

    public List<SectionNavItem> sectionNav(String activeSlug) {
        return wikiRepository.findEnabledSectionsForNav(activeSlug == null ? "" : activeSlug);
    }

    public HomeView home() {
        List<ArticleListItem> articles = toListItems(articleRepository.findLatestArticles(30));
        String summary = articles.isEmpty()
                ? "아직 수집된 기사가 없습니다. 작업 페이지에서 RSS 수집을 실행하면 주요 기사가 표시됩니다."
                : "최근 수집 기사와 AI 위키 노트를 바탕으로 주요 흐름을 표시합니다. AI 노트가 아직 없는 기사는 처리 상태를 함께 보여줍니다.";
        return new HomeView(
                "오늘의 뉴스",
                Instant.now().toString(),
                articles.size(),
                summary,
                articles
        );
    }

    public List<Provider> providers() {
        return wikiRepository.findEnabledProviders();
    }

    public Provider provider(String slug) {
        return wikiRepository.findEnabledProviderBySlug(slug);
    }

    public List<ArticleListItem> providerArticles(String slug) {
        return toListItems(articleRepository.findLatestArticlesByProvider(slug, 80));
    }

    public List<ArticleListItem> latestArticles() {
        return toListItems(articleRepository.findLatestArticles(80));
    }

    public ArticleRepository.ArticleDetailView article(long id) {
        return articleRepository.findArticleDetail(id);
    }

    public List<String> articleTopics(long id) {
        return wikiRepository.findArticleTopicTitles(id, 20);
    }

    public List<WikiRepository.WikiItem> topics() {
        return wikiRepository.findTopics(100);
    }

    public WikiRepository.WikiItem topic(String slug) {
        return wikiRepository.findTopic(slug);
    }

    private List<ArticleListItem> toListItems(List<ArticleRepository.ArticleListView> rows) {
        List<ArticleListItem> items = new ArrayList<>();
        for (ArticleRepository.ArticleListView row : rows) {
            List<String> chips = wikiRepository.findArticleTopicTitles(row.id(), 5);
            items.add(new ArticleListItem(
                    row.id(),
                    row.title(),
                    row.providerName(),
                    row.publishedAt() == null ? "" : row.publishedAt(),
                    row.summary(),
                    row.durability(),
                    chips
            ));
        }
        return items;
    }
}
