package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import com.newswiki.service.WikiService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final com.newswiki.service.MarkdownService markdownService;

    @Autowired
    public WikiController(NewsViewService newsViewService, WikiService wikiService, com.newswiki.service.MarkdownService markdownService) {
        this.newsViewService = newsViewService;
        this.wikiService = wikiService;
        this.markdownService = markdownService;
    }

    public WikiController(NewsViewService newsViewService, WikiService wikiService) {
        this(newsViewService, wikiService, new com.newswiki.service.MarkdownService());
    }

    @GetMapping("/topics")
    public String topics(Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("summary", wikiService.pendingSummary());
        model.addAttribute("pages", newsViewService.recentWikiPages(100));
        model.addAttribute("sections", newsViewService.wikiSections());
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
        model.addAttribute("bodyHtml", markdownService.render(page.body()));
        model.addAttribute("pages", newsViewService.recentWikiPages(20));
        model.addAttribute("sections", newsViewService.wikiSections());
        return "pages/wiki-detail";
    }
}
