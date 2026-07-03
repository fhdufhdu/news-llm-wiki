package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProviderController {
    private final NewsViewService newsViewService;

    public ProviderController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/providers")
    public String providers(Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("providers", newsViewService.providers());
        return "pages/providers";
    }

    @GetMapping("/providers/{slug}")
    public String provider(@PathVariable String slug, Model model) {
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("provider", newsViewService.provider(slug));
        model.addAttribute("articles", newsViewService.providerArticles(slug));
        return "pages/provider-detail";
    }
}
