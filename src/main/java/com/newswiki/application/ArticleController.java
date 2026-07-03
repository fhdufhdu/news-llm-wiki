package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ArticleController {
    private final NewsViewService newsViewService;

    public ArticleController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/articles/{id}")
    public String article(@PathVariable long id, Model model) {
        var article = newsViewService.article(id);
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("article", article);
        return "pages/article-detail";
    }
}
