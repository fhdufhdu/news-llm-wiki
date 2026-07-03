package com.newswiki.application;

import com.newswiki.dto.SectionNavItem;
import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class SectionController {
    private final NewsViewService newsViewService;

    public SectionController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/sections/{slug}")
    public String section(@PathVariable String slug, Model model) {
        List<SectionNavItem> sectionNav = newsViewService.sectionNav(slug);
        model.addAttribute("sectionNav", sectionNav);
        model.addAttribute("activeSectionSlug", slug);
        model.addAttribute("sectionTitle", sectionNav.stream()
                .filter(item -> item.slug().equals(slug))
                .map(SectionNavItem::title)
                .findFirst()
                .orElse("섹션"));
        model.addAttribute("pages", newsViewService.wikiPagesBySection(slug));
        return "pages/sections";
    }
}
