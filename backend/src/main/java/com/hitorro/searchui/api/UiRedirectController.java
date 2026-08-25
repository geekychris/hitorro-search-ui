/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback — any GET that doesn't match a static file or an /api/
 * route lands on index.html so React Router / URL-hash routing can
 * pick up client-side navigation on refresh.
 *
 * <p>{@code /{path:^(?!api|actuator|assets|index\.html|.*\..*).*}} —
 * catchall covering every top-level path that isn't api/actuator,
 * doesn't live under /assets/, isn't index.html itself, and doesn't
 * contain a dot (favicon.ico, robots.txt land on the static handler).
 * Adding new SPA routes no longer needs a matching @GetMapping — the
 * regex covers them by default.</p>
 */
@Controller
public class UiRedirectController {

    @GetMapping({"/", "/{path:^(?!api|actuator|assets)[^.]*$}",
                 "/{path:^(?!api|actuator|assets)[^.]*$}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
