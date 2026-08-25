/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Thin adapter around the fleet-retrieval REST surface. Every call is
 * blocking (via {@code .block()}) — this BFF runs on the servlet stack
 * and only uses WebClient for its convenient timeouts / connection
 * pooling, not for reactive backpressure.
 *
 * <p>Errors are re-thrown as {@link FleetException} carrying the upstream
 * status + body so {@code SearchExceptionHandler} can translate them
 * into clean JSON responses for the browser.</p>
 */
@Component
public class FleetRetrievalClient {

    private final WebClient client;

    // Injects the `fleetWebClient` bean from FleetRetrievalConfig by name.
    public FleetRetrievalClient(WebClient fleetWebClient) {
        this.client = fleetWebClient;
    }

    /** GET /api/retrieval/health — returns {status, indexes, kvStores, mode}. */
    public JsonNode health() {
        return get("/api/retrieval/health");
    }

    /** GET /api/retrieval/indexes — list of {name, docCount, lastModifiedMs}. */
    public JsonNode indexes() {
        return get("/api/retrieval/indexes");
    }

    /** POST /api/retrieval/execute — coordinator with the full JVS query. */
    public JsonNode execute(JsonNode body) {
        try {
            return client.post()
                    .uri("/api/retrieval/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new FleetException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    /** GET /api/retrieval/documents/{indexName}/{key} — one KV doc. */
    public JsonNode document(String indexName, String key) {
        return get("/api/retrieval/documents/" + enc(indexName) + "/" + enc(key));
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode get(String path) {
        try {
            return client.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new FleetException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Thrown when fleet-retrieval returns a non-2xx. Carries the upstream
     * status + body so exception-handler middleware can surface actionable
     * detail (e.g. "no fleet-retrieval endpoints discovered" for 503) in
     * the JSON response the browser sees.
     */
    public static class FleetException extends RuntimeException {
        public final int upstreamStatus;
        public final String upstreamBody;
        public FleetException(int status, String body, Throwable cause) {
            super("fleet-retrieval " + status + ": " + body, cause);
            this.upstreamStatus = status;
            this.upstreamBody = body;
        }
    }
}
