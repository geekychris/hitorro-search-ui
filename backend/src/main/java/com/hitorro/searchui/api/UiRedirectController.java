/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback — any GET that doesn't match a static file or an /api/
 * route lands on index.html so React Router can pick up client-side
 * routing. The Spring resource handler in {@code UiCacheConfig} maps
 * {@code /} to {@code /index.html} directly; this controller adds
 * {@code /search}, {@code /analyst}, etc. as aliases so refresh works
 * on deep links.
 */
@Controller
public class UiRedirectController {

    // React Router deep-link fallbacks. Add more when the SPA grows
    // more top-level routes.
    @GetMapping({"/search", "/analyst", "/index/{name}"})
    public String forward() {
        return "forward:/index.html";
    }
}
