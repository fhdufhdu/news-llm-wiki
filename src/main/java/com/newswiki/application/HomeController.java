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
        model.addAttribute("categoryNav", newsViewService.categoryNav(""));
        model.addAttribute("activeCategorySlug", "");
        model.addAttribute("view", newsViewService.home());
        model.addAttribute("categories", newsViewService.wikiCategories());
        model.addAttribute("majorCategories", newsViewService.majorCategories());
        model.addAttribute("subcategories", newsViewService.subcategories());
        model.addAttribute("pages", newsViewService.recentWikiPages(30));
        model.addAttribute("todaySummary", newsViewService.todaySummary());
        return "pages/home";
    }
}
