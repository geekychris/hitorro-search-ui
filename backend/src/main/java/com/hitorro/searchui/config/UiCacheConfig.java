/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Cache policy for the bundled React SPA.
 *
 * <ul>
 *   <li>{@code index.html} — {@code no-cache, must-revalidate}. Browsers
 *       always re-check so a new deploy is picked up immediately.</li>
 *   <li>{@code assets/*.js} / {@code assets/*.css} — Vite emits hashed
 *       filenames, so we can safely give these a long TTL.</li>
 * </ul>
 *
 * <p>Mirrors the driver-app's {@code UiCacheConfig} pattern.</p>
 */
@Configuration
public class UiCacheConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/index.html", "/")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        // Catch-all for anything else in /static/ (favicon etc). Short TTL.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
    }
}
