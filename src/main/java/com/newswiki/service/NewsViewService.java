package com.newswiki.service;

import com.newswiki.dto.ArticleListItem;
import com.newswiki.dto.HomeView;
import com.newswiki.dto.SectionNavItem;
import com.newswiki.dto.WikiPageDetail;
import com.newswiki.dto.WikiPageListItem;
import com.newswiki.dto.WikiSection;
import com.newswiki.dto.TodaySummary;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiPageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class NewsViewService {
    private final ArticleRepository articleRepository;
    private final WikiPageRepository wikiPageRepository;

    @Autowired
    public NewsViewService(ArticleRepository articleRepository, WikiPageRepository wikiPageRepository) {
        this.articleRepository = articleRepository;
        this.wikiPageRepository = wikiPageRepository;
    }

    public NewsViewService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
        this.wikiPageRepository = null;
    }

    public List<SectionNavItem> sectionNav(String activeSlug) {
        if (wikiPageRepository != null) {
            String active = activeSlug == null ? "" : activeSlug;
            return wikiPageRepository.findFixedNavSections().stream()
                    .map(section -> new SectionNavItem(section.slug(), section.title(), section.slug().equals(active)))
                    .toList();
        }
        return List.of();
    }

    public List<WikiSection> wikiSections() {
        return wikiPageRepository.findSections();
    }

    public List<WikiPageListItem> recentWikiPages(int limit) {
        return wikiPageRepository.findRecentPages(limit);
    }

    public TodaySummary todaySummary() {
        String date = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        if (wikiPageRepository == null) {
            return new TodaySummary(date, 0, 0, "오늘 저장된 기사 원문을 바탕으로 위키 문서가 생성되면 이 영역에 주요 흐름을 표시합니다.", List.of());
        }
        var summary = wikiPageRepository.findTodaySummary(date);
        return new TodaySummary(
                summary.date(),
                summary.articleCount(),
                summary.wikiPageCount(),
                summary.summary(),
                summary.pages()
        );
    }

    public List<WikiPageListItem> wikiPagesBySection(String sectionSlug) {
        return wikiPageRepository.findPagesBySection(sectionSlug);
    }

    public WikiPageDetail wikiPage(String slug) {
        return wikiPageRepository.findPageBySlug(slug);
    }

    public HomeView home() {
        List<ArticleListItem> articles = toListItems(articleRepository.findLatestArticles(30));
        String summary = articles.isEmpty()
                ? "아직 수집된 기사가 없습니다. 기사 가져오기 화면에서 URL을 입력하면 주요 기사가 표시됩니다."
                : "최근 수집 기사와 AI 위키 노트를 바탕으로 주요 흐름을 표시합니다. AI 노트가 아직 없는 기사는 처리 상태를 함께 보여줍니다.";
        return new HomeView(
                "오늘의 뉴스",
                Instant.now().toString(),
                articles.size(),
                summary,
                articles
        );
    }

    public List<ArticleListItem> latestArticles() {
        return toListItems(articleRepository.findLatestArticles(80));
    }

    public ArticleRepository.ArticleDetailView article(long id) {
        return articleRepository.findArticleDetail(id);
    }

    public List<String> articleTopics(long id) {
        return wikiPageRepository == null ? List.of() : wikiPageRepository.findPageTitlesByArticleId(id, 20);
    }

    private List<ArticleListItem> toListItems(List<ArticleRepository.ArticleListView> rows) {
        List<ArticleListItem> items = new ArrayList<>();
        for (ArticleRepository.ArticleListView row : rows) {
            List<String> chips = wikiPageRepository == null ? List.of() : wikiPageRepository.findPageTitlesByArticleId(row.id(), 5);
            items.add(new ArticleListItem(
                    row.id(),
                    row.title(),
                    row.publishedAt() == null ? "" : row.publishedAt(),
                    row.summary(),
                    row.durability(),
                    chips
            ));
        }
        return items;
    }
}
