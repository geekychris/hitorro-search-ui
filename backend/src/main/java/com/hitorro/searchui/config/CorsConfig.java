/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for {@code /api/**}. Value comes from
 * {@code hitorro.searchui.cors.allowed-origins}:
 * <ul>
 *   <li>{@code "*"} (dev default) — any origin, no credentials. Lets
 *       {@code pnpm dev} on 5173 hit the packaged fat jar on 8100
 *       without a proxy config.</li>
 *   <li>{@code ""} (k8s profile) — no CORS, same-origin only.</li>
 *   <li>Comma-separated list — explicit allow-list (prod).</li>
 * </ul>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String origins;

    public CorsConfig(@Value("${hitorro.searchui.cors.allowed-origins:*}") String origins) {
        this.origins = origins == null ? "" : origins.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origins.isEmpty()) return;   // same-origin only
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
