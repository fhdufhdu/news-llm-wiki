package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import com.newswiki.service.WikiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class WikiController {
    private final NewsViewService newsViewService;
    private final WikiService wikiService;

    public WikiController(NewsViewService newsViewService, WikiService wikiService) {
        this.newsViewService = newsViewService;
        this.wikiService = wikiService;
    }

    @GetMapping("/topics")
    public String topics(Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("summary", wikiService.pendingSummary());
        model.addAttribute("pages", newsViewService.recentWikiPages(100));
        return "pages/wiki-detail";
    }

    @GetMapping("/topics/{slug}")
    public String topic(@PathVariable String slug, Model model) {
        return wikiPage(slug, model);
    }

    @GetMapping("/wiki/{slug}")
    public String wikiPage(@PathVariable String slug, Model model) {
        var page = newsViewService.wikiPage(slug);
        if (page == null) {
            throw new ResponseStatusException(NOT_FOUND, "Wiki page not found");
        }
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("page", page);
        model.addAttribute("pages", newsViewService.recentWikiPages(20));
        return "pages/wiki-detail";
    }
}
