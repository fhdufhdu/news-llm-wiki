package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import com.newswiki.service.WikiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
        model.addAttribute("topics", newsViewService.topics());
        return "pages/wiki-detail";
    }

    @GetMapping("/topics/{slug}")
    public String topic(@PathVariable String slug, Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("topic", newsViewService.topic(slug));
        model.addAttribute("summary", wikiService.pendingSummary());
        model.addAttribute("topics", newsViewService.topics());
        return "pages/wiki-detail";
    }
}
