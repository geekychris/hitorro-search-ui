/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.searchui.client.FleetRetrievalClient.FleetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translate exceptions into clean JSON responses the React error boundary
 * can render. Upstream fleet-retrieval failures carry an actionable body
 * (e.g. {@code "no fleet-retrieval endpoints discovered via static — …"});
 * we surface it so users know what to fix.
 */
@RestControllerAdvice
public class SearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchExceptionHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @ExceptionHandler(FleetException.class)
    public ResponseEntity<ObjectNode> fleet(FleetException e) {
        log.warn("fleet-retrieval upstream failure: status={} body={}", e.upstreamStatus, e.upstreamBody);
        // Preserve the upstream status when it's meaningful (4xx / 5xx),
        // fall back to 502 for anything weird.
        HttpStatus status = HttpStatus.resolve(e.upstreamStatus);
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        ObjectNode body = JSON.createObjectNode();
        body.put("error",           "fleet-retrieval " + e.upstreamStatus);
        body.put("upstreamStatus",  e.upstreamStatus);
        body.put("upstreamBody",    e.upstreamBody == null ? "" : e.upstreamBody);
        body.put("hint",            hintFor(e.upstreamStatus, e.upstreamBody));
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ObjectNode> badReq(IllegalArgumentException e) {
        ObjectNode body = JSON.createObjectNode();
        body.put("error", "bad request: " + e.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> generic(Exception e) {
        log.error("search-ui unhandled failure", e);
        ObjectNode body = JSON.createObjectNode();
        body.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** Turn common upstream failures into a one-line "do this" hint the UI can show. */
    private static String hintFor(int status, String body) {
        if (body != null && body.contains("no fleet-retrieval endpoints discovered")) {
            return "Start fleet-retrieval and register it (see MESH_FLEET_RETRIEVAL in mesh-up.sh).";
        }
        if (status == 404 && body != null && body.contains("no such")) {
            return "Index not found — check /api/indexes for the right name.";
        }
        if (status == 503) {
            return "Upstream unavailable — is hitorro-fleet-retrieval running on the configured URL?";
        }
        return "";
    }
}
