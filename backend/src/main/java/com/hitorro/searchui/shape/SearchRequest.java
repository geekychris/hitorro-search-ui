/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.shape;

import java.util.List;
import java.util.Map;

/**
 * UI-friendly search request. The React layer speaks THIS shape;
 * {@code JvsQueryShaper} translates it into the JVS query the coordinator
 * expects. Keeping the wire types decoupled means the UI doesn't need
 * to know how logical field paths, stages, or facet-field naming work
 * inside the retrieval pipeline.
 *
 * <p>All fields are nullable — the shaper applies sensible defaults for
 * anything omitted, so a minimal call is just
 * {@code {"index":"mail-enriched-idx","q":"chase"}}.</p>
 *
 * @param index    logical index name (matches {@code sinks[].name} in the
 *                 pipeline YAML that produced it)
 * @param q        user query string. In end-user mode, wrapped across
 *                 default text fields; in analyst mode, passed through as
 *                 Lucene syntax verbatim.
 * @param filters  map of field → allowed values (AND across fields, OR
 *                 within a field). Field names may be logical JVS paths
 *                 (e.g. {@code sender_domain}); JVSQueryParser expands
 *                 them via the index type sidecar at search time.
 * @param facets   field names to aggregate on. Only identifier-method
 *                 fields facet correctly; text fields will bucket per
 *                 token which is rarely what you want.
 * @param sort     sort spec — {@code "relevance"} (default) or
 *                 {@code "date_desc"} / {@code "date_asc"} / an explicit
 *                 field:direction pair.
 * @param page     zero-based page number.
 * @param size     page size. Capped at 100 in the shaper.
 * @param mode     {@code "end-user"} (adds summarize + fixup stages) or
 *                 {@code "analyst"} (raw query, no summarize).
 * @param lang     ISO 639-1 language code for i18n fields. Defaults to
 *                 {@code "en"}.
 */
public record SearchRequest(
        String index,
        String q,
        Map<String, List<String>> filters,
        List<String> facets,
        String sort,
        Integer page,
        Integer size,
        String mode,
        String lang
) {
    public String modeOrDefault() { return mode == null || mode.isBlank() ? "end-user" : mode; }
    public String langOrDefault() { return lang == null || lang.isBlank() ? "en" : lang; }
    public int pageOrDefault()    { return page == null || page < 0 ? 0 : page; }
    public int sizeOrDefault()    { return size == null || size <= 0 ? 20 : Math.min(size, 100); }
    public List<String> facetsOrEmpty() { return facets == null ? List.of() : facets; }
    public Map<String, List<String>> filtersOrEmpty() { return filters == null ? Map.of() : filters; }
    public String qOrMatchAll()   { return (q == null || q.isBlank()) ? "*:*" : q; }
    public String sortOrRelevance() { return sort == null || sort.isBlank() ? "relevance" : sort; }
}
