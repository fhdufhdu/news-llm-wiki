package com.newswiki.application;

import com.newswiki.dto.CategoryNavItem;
import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class CategoryController {
    private final NewsViewService newsViewService;

    public CategoryController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/categories/{slug}")
    public String category(@PathVariable String slug, Model model) {
        List<CategoryNavItem> categoryNav = newsViewService.categoryNav(slug);
        model.addAttribute("categoryNav", categoryNav);
        model.addAttribute("activeCategorySlug", slug);
        model.addAttribute("categoryTitle", categoryNav.stream()
                .filter(item -> item.slug().equals(slug))
                .map(CategoryNavItem::title)
                .findFirst()
                .orElseGet(() -> newsViewService.wikiCategories().stream()
                        .filter(category -> category.slug().equals(slug))
                        .map(com.newswiki.dto.WikiCategory::title)
                        .findFirst()
                        .orElse("소분류")));
        model.addAttribute("pages", newsViewService.wikiPagesByCategory(slug));
        model.addAttribute("categories", newsViewService.wikiCategories());
        model.addAttribute("majorCategories", newsViewService.majorCategories());
        model.addAttribute("subcategories", newsViewService.subcategories());
        return "pages/categories";
    }
}
