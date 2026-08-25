/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.searchui.client.FleetRetrievalClient;
import com.hitorro.searchui.shape.JvsQueryShaper;
import com.hitorro.searchui.shape.SearchRequest;
import com.hitorro.searchui.shape.SearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The two endpoints the React SearchBox / QueryBar POST to:
 *
 * <ul>
 *   <li>{@code POST /api/search} — single-index query. Shapes the
 *       UI-friendly {@link SearchRequest} into a full JVS pipeline
 *       (search + fetch + fixup + [summarize] + facets) and delegates
 *       to fleet-retrieval's {@code /api/retrieval/execute}.</li>
 *   <li>{@code POST /api/search-multiple} — cross-index federation.
 *       Takes {@code {indexes:[…], q, filters, facets, sort, page,
 *       size, lang}} and delegates to fleet-retrieval's
 *       {@code /api/retrieval/search-multiple}, which merges hits
 *       from multiple indexes using the requested merger strategy.</li>
 * </ul>
 *
 * Errors bubble to {@code SearchExceptionHandler}.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FleetRetrievalClient fleet;
    private final JvsQueryShaper shaper;

    public SearchController(FleetRetrievalClient fleet, JvsQueryShaper shaper) {
        this.fleet = fleet;
        this.shaper = shaper;
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest req) {
        long t0 = System.currentTimeMillis();
        JsonNode body = shaper.toExecuteRequest(req);
        JsonNode coord = fleet.execute(body);
        return shaper.fromExecuteResponse(req, coord, System.currentTimeMillis() - t0);
    }

    /**
     * Cross-index federation. Takes the same UI DTO as {@code /search}
     * but reads {@code index} as a comma-separated list (or an
     * additional {@code indexes} field carrying the array). Passes
     * through to fleet-retrieval's flat merger endpoint; unwraps into
     * the same {@link SearchResponse} the frontend expects — hits are
     * tagged with their source index in the {@code doc._sourceIndex}
     * field so the UI can render a badge.
     */
    @PostMapping("/search-multiple")
    public SearchResponse searchMultiple(@RequestBody MultiSearchRequest req) {
        long t0 = System.currentTimeMillis();
        List<String> indexes = req.indexes();
        if (indexes == null || indexes.isEmpty()) {
            throw new IllegalArgumentException("indexes array is required");
        }
        // Compose a single query string with the same rules as the
        // single-index shaper — reuse it via a synthetic SearchRequest
        // carrying every field including the new sortChain + policies.
        SearchRequest single = new SearchRequest(
                indexes.get(0), req.q(), req.filters(), req.facets(),
                req.sort(), req.page(), req.size(),
                req.mode() == null ? "analyst" : req.mode(),
                req.lang(),
                req.sortChain(),
                req.mergePolicies());
        String composed = shaper.composeQueryString(single);

        ObjectNode body = JSON.createObjectNode();
        ArrayNode idxArr = body.putArray("indexNames");
        indexes.forEach(idxArr::add);
        body.put("query", composed);
        body.put("offset", single.pageOrDefault() * single.sizeOrDefault());
        body.put("limit",  single.sizeOrDefault());
        body.put("lang",   single.langOrDefault());
        ArrayNode facets = body.putArray("facets");
        single.facetsOrEmpty().forEach(facets::add);
        if (req.merger() != null && !req.merger().isBlank()) body.put("merger", req.merger());

        // Forward the composite sort chain + per-aggregate merge policies
        // to the coordinator so it can drive the SelectTreeMerger with
        // the caller's preferences. Both optional — coordinator falls
        // back to sane defaults when missing.
        java.util.List<SearchRequest.SortSpec> chain = single.sortChainOrEmpty();
        if (!chain.isEmpty()) {
            ArrayNode sortArr = body.putArray("sort");
            for (SearchRequest.SortSpec s : chain) {
                if (s.field() == null || s.field().isBlank()) continue;
                ObjectNode key = sortArr.addObject();
                key.put("field", s.field());
                key.put("direction", "asc".equalsIgnoreCase(s.direction()) ? "asc" : "desc");
            }
        }
        java.util.Map<String, String> policies = single.mergePoliciesOrEmpty();
        if (!policies.isEmpty()) {
            ObjectNode p = body.putObject("mergePolicies");
            policies.forEach(p::put);
        }

        JsonNode coord = fleet.searchMultiple(body);
        return shaper.fromExecuteResponse(single, coord, System.currentTimeMillis() - t0);
    }

    /** UI-friendly request DTO for the multi-index endpoint. Mirrors
     *  {@link SearchRequest} — an {@code indexes} list instead of a
     *  single {@code index}, plus the shared sort / mergePolicies /
     *  merger controls. */
    public record MultiSearchRequest(
            List<String> indexes, String q,
            java.util.Map<String, List<String>> filters,
            List<String> facets,
            String sort, Integer page, Integer size,
            String mode, String lang,
            String merger,
            /** Composite sort chain — same as SearchRequest.sortChain. */
            List<SearchRequest.SortSpec> sortChain,
            /** Per-aggregate policy overrides — same as SearchRequest.mergePolicies. */
            java.util.Map<String, String> mergePolicies
    ) { }
}
