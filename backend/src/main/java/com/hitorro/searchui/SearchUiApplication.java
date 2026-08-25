/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the hitorro-search-ui BFF. Serves the bundled React SPA
 * from {@code classpath:/static/} plus a small REST surface under
 * {@code /api/*} that proxies {@code hitorro-fleet-retrieval}.
 *
 * <p>All config is driven from {@code application.yml}; profiles for
 * {@code docker} and {@code k8s} override the fleet URL + CORS shape.</p>
 */
@SpringBootApplication
public class SearchUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchUiApplication.class, args);
    }
}
