package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final NewsViewService newsViewService;

    public HomeController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("view", newsViewService.home());
        model.addAttribute("sections", newsViewService.wikiSections());
        model.addAttribute("pages", newsViewService.recentWikiPages(30));
        model.addAttribute("providers", newsViewService.providers());
        return "pages/home";
    }
}
