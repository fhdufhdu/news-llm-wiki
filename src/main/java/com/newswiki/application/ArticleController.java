package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ArticleController {
    private final NewsViewService newsViewService;

    public ArticleController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/articles/{id}")
    public String article(@PathVariable long id, Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("article", newsViewService.article(id));
        model.addAttribute("topics", newsViewService.articleTopics(id));
        return "pages/article-detail";
    }
}
