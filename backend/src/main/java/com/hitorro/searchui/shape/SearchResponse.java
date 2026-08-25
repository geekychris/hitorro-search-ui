/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.shape;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * UI-friendly search response. Unwraps the coordinator's
 * {@code aggregates[]} into a flat {@code facets} map keyed by field
 * name, and pulls the query-focused summary from the summarize stage
 * (or falls back to a local snippet extractor when summarize returns
 * nothing usable).
 *
 * @param total     total matches across the index (may exceed
 *                  {@code hits.size()} due to pagination).
 * @param page      echo of the requested page (zero-based).
 * @param size      echo of the requested size.
 * @param tookMs    coordinator + BFF wall time.
 * @param stages    stages fleet-retrieval actually executed — useful in
 *                  analyst mode to confirm which retrievers participated.
 * @param hits      per-document rows — {@code {id, htType, snippet, doc}}.
 * @param facets    field → {@link Facet}. Empty when no facets requested.
 */
public record SearchResponse(
        long total,
        int page,
        int size,
        long tookMs,
        List<String> stages,
        List<Hit> hits,
        Map<String, Facet> facets,
        /**
         * Merged per-source aggregates from the coordinator's
         * cross-index merger. Passes through as {@link JsonNode} so the
         * BFF stays schema-agnostic — the React side reads them as raw
         * JSON, keying into {@code byIndex.<sourceName>} for per-source
         * drill-in. Null / empty on single-index searches.
         */
        List<JsonNode> aggregates
) {

    /** 7-arg legacy constructor — populates {@code aggregates} as null.
     *  Kept so existing callers that only care about the flat facets
     *  map don't need to pass an extra arg. */
    public SearchResponse(long total, int page, int size, long tookMs,
                          List<String> stages, List<Hit> hits,
                          Map<String, Facet> facets) {
        this(total, page, size, tookMs, stages, hits, facets, null);
    }

    /**
     * @param id        the document's canonical id (extracted from
     *                  {@code id.id} for core_id-typed docs, or the raw
     *                  {@code id} field for scalar-id types).
     * @param htType    JVS type name from {@code ht_type} — drives the
     *                  React renderer's per-type override lookup.
     * @param snippet   query-focused excerpt. Non-null in end-user mode
     *                  when the query text actually appears in the doc's
     *                  text fields; null in analyst mode / when nothing
     *                  matched to highlight.
     * @param doc       full JVS document as raw JSON — the React renderer
     *                  walks it based on the index schema.
     */
    public record Hit(String id, String htType, String snippet, JsonNode doc) { }

    /**
     * A faceted field's aggregation.
     *
     * @param field       logical field name (echo of the request).
     * @param totalCount  total docs bucketed across all values.
     * @param values      value → count pairs, ordered by count desc.
     */
    public record Facet(String field, long totalCount, List<FacetValue> values) { }

    public record FacetValue(String value, long count) { }
}
