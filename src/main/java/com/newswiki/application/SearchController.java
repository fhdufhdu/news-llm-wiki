package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import com.newswiki.service.SearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {
    private final SearchService searchService;
    private final NewsViewService newsViewService;

    public SearchController(SearchService searchService, NewsViewService newsViewService) {
        this.searchService = searchService;
        this.newsViewService = newsViewService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String query, Model model) {
        model.addAttribute("categoryNav", newsViewService.categoryNav(""));
        model.addAttribute("activeCategorySlug", "");
        model.addAttribute("results", searchService.search(query));
        model.addAttribute("categories", newsViewService.wikiCategories());
        model.addAttribute("majorCategories", newsViewService.majorCategories());
        model.addAttribute("subcategories", newsViewService.subcategories());
        return "pages/search";
    }
}
