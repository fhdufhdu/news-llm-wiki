package com.newswiki.application;

import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SettingsController {
    private final NewsViewService newsViewService;

    public SettingsController(NewsViewService newsViewService) {
        this.newsViewService = newsViewService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("categoryNav", newsViewService.categoryNav(""));
        model.addAttribute("activeCategorySlug", "");
        return "pages/settings";
    }
}
