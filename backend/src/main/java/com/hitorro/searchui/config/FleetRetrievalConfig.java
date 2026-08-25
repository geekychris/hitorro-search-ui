/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient bean pointing at hitorro-fleet-retrieval. One instance is
 * reused across the whole BFF (Netty pooling; cheap and thread-safe).
 *
 * <p>Timeout is scoped snug because the coordinator is expected to be
 * co-located (same K8s namespace / same host / same Docker network).
 * A too-slow coordinator surfaces as a 504 in {@code SearchExceptionHandler}
 * rather than hanging the UI thread.</p>
 */
@Configuration
public class FleetRetrievalConfig {

    // Bean name intentionally `fleetWebClient` — the @Component class
    // FleetRetrievalClient wants the auto-registered bean name
    // `fleetRetrievalClient`, and naming this WebClient factory the same
    // triggers a BeanDefinitionOverrideException at startup.
    @Bean
    public WebClient fleetWebClient(
            @Value("${hitorro.searchui.fleet.base-url:http://localhost:8095}") String baseUrl,
            @Value("${hitorro.searchui.fleet.timeout-ms:15000}") int timeoutMs) {

        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.min(5000, timeoutMs))
                .doOnConnected(c -> c.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .clientConnector(new ReactorClientHttpConnector(http))
                // 2 MiB — some hit payloads (full enriched JVS with segmented_ner
                // per sentence) push past the 256 KiB Reactor default.
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}
