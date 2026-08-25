/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.shape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional translator between the UI's {@link SearchRequest} +
 * {@link SearchResponse} shape and the JVS query the fleet-retrieval
 * coordinator speaks.
 *
 * <p>Kept as a stateless component so it's trivially testable — every
 * method takes its inputs, returns its output, and has no side effects
 * beyond building JSON. Unit tests live in
 * {@code src/test/java/com/hitorro/searchui/shape/JvsQueryShaperTest}.</p>
 */
@Component
public class JvsQueryShaper {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Default text fields wrapped around a bare end-user query. The
     * JVS type sidecar means logical paths like {@code body.mls.clean}
     * expand to the physical projected name at search time.
     */
    private static final List<String> DEFAULT_TEXT_FIELDS = List.of(
            "body.mls.clean", "title.mls.clean"
    );

    /**
     * Assemble the JVS request body (indexName + query with stages) for
     * {@code POST /api/retrieval/execute}. The exact stage set depends
     * on the request's {@code mode}:
     *
     * <ul>
     *   <li><b>end-user</b> — search + fetch + fixup(basic) + page + summarize.
     *       Summarize gets the query text so its highlighter has something
     *       to focus on.</li>
     *   <li><b>analyst</b> — search + fetch + fixup(basic) + page. No
     *       summarize (the analyst usually wants raw fields, not excerpts).</li>
     * </ul>
     */
    public ObjectNode toExecuteRequest(SearchRequest req) {
        String indexName = req.index() == null ? "" : req.index();

        ObjectNode body = JSON.createObjectNode();
        body.put("indexName", indexName);
        ObjectNode query = body.putObject("query");

        // ---- search stage ----
        String composed = composeQueryString(req);
        int pageSize = req.sizeOrDefault();
        int offset   = req.pageOrDefault() * pageSize;
        ObjectNode search = query.putObject("search");
        search.put("query",  composed);
        search.put("offset", offset);
        search.put("limit",  pageSize);
        search.put("lang",   req.langOrDefault());
        ArrayNode facets = search.putArray("facets");
        req.facetsOrEmpty().forEach(facets::add);

        // ---- fetch stage (always) — pulls stored _source / KV-hydrated doc ----
        query.putObject("fetch");

        // ---- fixup stage (always, basic tag) — applies type-system projections ----
        ObjectNode fixup = query.putObject("fixup");
        ArrayNode fixupTags = fixup.putArray("tags");
        fixupTags.add("basic");

        // NOTE: NO page stage. The search stage already applies
        // offset+limit at the Lucene level; adding a page stage on top
        // re-slices the already-limited result set and drops docs when
        // the input has fewer than page.rows entries (e.g. total=2,
        // limit=1 → search returns 1 → page returns 0). Since our BFF
        // maps request page/size directly onto search.offset/search.limit,
        // the page stage is strictly redundant.

        // ---- summarize stage (end-user only) ----
        if ("end-user".equals(req.modeOrDefault())) {
            ObjectNode summarize = query.putObject("summarize");
            summarize.put("maxDocs",  pageSize);
            summarize.put("maxWords", 60);
        }

        // ---- composite sort chain + per-aggregate merge policies ----
        // Both fields are optional in the UI DTO; only emit them when
        // set so the coordinator's backward-compat defaults kick in.
        // Emitted at the TOP LEVEL of the JVS body (siblings of
        // indexName + query), matching what fleet-retrieval's
        // /api/retrieval/search-multiple + /api/retrieval/execute parse.
        List<SearchRequest.SortSpec> chain = req.sortChainOrEmpty();
        if (!chain.isEmpty()) {
            ArrayNode sortArr = body.putArray("sort");
            for (SearchRequest.SortSpec s : chain) {
                if (s.field() == null || s.field().isBlank()) continue;
                ObjectNode key = sortArr.addObject();
                key.put("field", s.field());
                key.put("direction", "asc".equalsIgnoreCase(s.direction()) ? "asc" : "desc");
            }
        }
        Map<String, String> policies = req.mergePoliciesOrEmpty();
        if (!policies.isEmpty()) {
            ObjectNode p = body.putObject("mergePolicies");
            policies.forEach(p::put);
        }

        return body;
    }

    /**
     * Turn a UI {@link SearchRequest} into a Lucene query string:
     *
     * <ul>
     *   <li>End-user mode with a real term: fan out across
     *       {@link #DEFAULT_TEXT_FIELDS} with OR, join filters with AND.</li>
     *   <li>Analyst mode: pass the query string through unchanged and only
     *       AND in filter constraints.</li>
     * </ul>
     */
    public String composeQueryString(SearchRequest req) {
        String q = req.qOrMatchAll();
        List<String> clauses = new ArrayList<>();

        boolean isMatchAll = q.equals("*:*");
        boolean isFielded = !isMatchAll && q.contains(":");

        if (isMatchAll) {
            clauses.add("*:*");
        } else if ("analyst".equals(req.modeOrDefault()) || isFielded) {
            // Trust the user's syntax; wrap in parens to keep filter AND clean.
            clauses.add("(" + q + ")");
        } else {
            // End-user: expand across default text fields.
            List<String> alts = new ArrayList<>();
            for (String f : DEFAULT_TEXT_FIELDS) {
                alts.add(f + ":" + quoteIfNeeded(q));
            }
            clauses.add("(" + String.join(" OR ", alts) + ")");
        }

        // Filters — every field is required; multiple values within a field are OR.
        for (Map.Entry<String, List<String>> e : req.filtersOrEmpty().entrySet()) {
            List<String> vals = e.getValue();
            if (vals == null || vals.isEmpty()) continue;
            List<String> alts = new ArrayList<>();
            for (String v : vals) alts.add(e.getKey() + ":" + quoteIfNeeded(v));
            clauses.add("+(" + String.join(" OR ", alts) + ")");
        }
        return String.join(" ", clauses);
    }

    private static String quoteIfNeeded(String v) {
        if (v == null || v.isEmpty()) return "\"\"";
        // Quote when the value has spaces or Lucene syntax chars. Escape
        // any embedded double-quotes so the resulting query parses.
        boolean needsQuote = v.chars().anyMatch(c ->
                Character.isWhitespace(c) || "+-!(){}[]^\"~*?:\\/".indexOf(c) >= 0);
        if (!needsQuote) return v;
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ==================================================================
    //  Response translation — coordinator's response → UI SearchResponse
    // ==================================================================

    /** Unwrap the fleet-retrieval response into the UI's flat shape. */
    public SearchResponse fromExecuteResponse(SearchRequest req, JsonNode coordResp, long tookMs) {
        long total = coordResp.path("totalHits").asLong(0);
        List<String> stages = new ArrayList<>();
        for (JsonNode s : coordResp.withArray("stagesUsed")) stages.add(s.asText());

        // Documents may live under "documents" (execute) or "hits" (some
        // older paths). Both are arrays of JVS docs.
        JsonNode docsNode = coordResp.has("documents") ? coordResp.get("documents") : coordResp.get("hits");

        // Snippets from summarize stage land in the aggregates array with
        // {"_aggregate":"summarize", "byId":{"<id>":"<snippet>"}}. Pull it
        // out once and hand snippets to the per-hit assembly loop.
        Map<String, String> snippetsById = extractSnippets(coordResp);

        List<SearchResponse.Hit> hits = new ArrayList<>();
        if (docsNode != null && docsNode.isArray()) {
            for (JsonNode d : docsNode) {
                String id = extractId(d);
                String htType = d.path("ht_type").asText(null);
                String snip = snippetsById.get(id);
                if (snip == null) snip = localSnippet(d, req);
                hits.add(new SearchResponse.Hit(id, htType, snip, d));
            }
        }

        Map<String, SearchResponse.Facet> facets = extractFacets(coordResp, req.facetsOrEmpty());

        // Pass through the coordinator's `aggregates` array verbatim
        // (JsonNode list). Multi-index searches carry byIndex.<source>
        // sub-objects here; single-index searches leave it null.
        List<JsonNode> aggregates = null;
        JsonNode aggs = coordResp.get("aggregates");
        if (aggs != null && aggs.isArray() && aggs.size() > 0) {
            aggregates = new ArrayList<>(aggs.size());
            for (JsonNode a : aggs) aggregates.add(a);
        }

        return new SearchResponse(
                total,
                req.pageOrDefault(),
                req.sizeOrDefault(),
                tookMs,
                stages,
                hits,
                facets,
                aggregates
        );
    }

    /**
     * Best-effort id extraction. Order of attempts:
     * <ol>
     *   <li>Scalar {@code id} field — string / number → asText().</li>
     *   <li>Object {@code id}: try each common inner-key in order
     *       ({@code id, did, key, uuid}). This covers core_id + most
     *       composite id shapes we've seen in the wild without requiring
     *       the type schema at this layer.</li>
     *   <li>Fallback: JSON-stringify the object so the caller at least
     *       gets a stable key (better than null for cache seeding, worse
     *       for KV lookups — the client will typically ignore this).</li>
     * </ol>
     *
     * <p>A schema-driven variant would be more robust for composite
     * id shapes with non-standard inner field names; the type sidecar
     * carries the info. Kept simple here so the shaper stays pure —
     * the {@link com.hitorro.searchui.api.IndexController#schema}
     * endpoint is the right place to publish "id inner-key" if it
     * ever matters.</p>
     */
    private static String extractId(JsonNode doc) {
        JsonNode idNode = doc.get("id");
        if (idNode == null || idNode.isNull()) return null;
        if (idNode.isValueNode()) return idNode.asText();
        if (idNode.isObject()) {
            for (String key : ID_INNER_KEYS) {
                JsonNode v = idNode.get(key);
                if (v != null && !v.isNull() && v.isValueNode()) return v.asText();
            }
            return idNode.toString();
        }
        return idNode.asText();
    }

    /** Inner keys we probe when the top-level {@code id} is an object.
     *  Order matters — {@code id} first (core_id's canonical merged
     *  value), then {@code did} (core_id's original raw id), then
     *  common alternatives. */
    private static final String[] ID_INNER_KEYS = { "id", "did", "key", "uuid", "guid" };

    /** Pull the summarize-stage aggregate. Structure varies across
     *  fleet-retrieval versions; we look for {@code byId} first, then
     *  a positional array, then just return empty (local snippet takes over). */
    private static Map<String, String> extractSnippets(JsonNode coordResp) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode aggs = coordResp.get("aggregates");
        if (aggs == null || !aggs.isArray()) return out;
        for (JsonNode a : aggs) {
            if (!"summarize".equals(a.path("_aggregate").asText())) continue;
            JsonNode byId = a.get("byId");
            if (byId != null && byId.isObject()) {
                byId.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
            }
        }
        return out;
    }

    /**
     * Build the flat {@code field → Facet} map from the aggregates array.
     * The facets aggregate is an object with one key per requested field,
     * each value shaped as {@code {dimension, totalCount, values:[...]}}.
     */
    private static Map<String, SearchResponse.Facet> extractFacets(JsonNode coordResp, List<String> requested) {
        Map<String, SearchResponse.Facet> out = new LinkedHashMap<>();
        JsonNode aggs = coordResp.get("aggregates");
        if (aggs == null || !aggs.isArray()) return out;

        for (JsonNode a : aggs) {
            // Facets aggregate has one key per dimension plus "_aggregate".
            // Any object entry that isn't "_aggregate" is a dimension.
            a.fields().forEachRemaining(e -> {
                if ("_aggregate".equals(e.getKey())) return;
                JsonNode dim = e.getValue();
                if (!dim.isObject() || !dim.has("values")) return;
                long total = dim.path("totalCount").asLong(0);
                List<SearchResponse.FacetValue> values = new ArrayList<>();
                for (JsonNode v : dim.withArray("values")) {
                    values.add(new SearchResponse.FacetValue(
                            v.path("value").asText(""),
                            v.path("count").asLong(0)));
                }
                out.put(e.getKey(), new SearchResponse.Facet(e.getKey(), total, values));
            });
        }
        // Preserve requested-field order — reindex.
        if (!requested.isEmpty()) {
            Map<String, SearchResponse.Facet> ordered = new LinkedHashMap<>();
            for (String f : requested) if (out.containsKey(f)) ordered.put(f, out.get(f));
            out.forEach((k, v) -> ordered.putIfAbsent(k, v));
            return ordered;
        }
        return out;
    }

    /**
     * Naive local snippet extractor: find the query terms in the doc's
     * body text and return a ~200-char window with the first match
     * highlighted via {@code **term**} markdown-ish delimiters. Used as
     * fallback when summarize returns nothing. Kept simple; the React
     * side does the actual highlight rendering.
     */
    static String localSnippet(JsonNode doc, SearchRequest req) {
        String q = req.q();
        if (q == null || q.isBlank() || q.equals("*:*")) return null;
        // Strip Lucene syntax to get bare terms.
        String bare = q.replaceAll("[+\\-!(){}\\[\\]^\"~*?:\\\\/]", " ")
                       .replaceAll("\\s+", " ")
                       .trim()
                       .toLowerCase();
        if (bare.isEmpty()) return null;
        String firstTerm = bare.split("\\s+")[0];

        JsonNode body = doc.at("/body/mls/0/clean");
        if (body.isMissingNode() || body.isNull()) body = doc.at("/body/mls/0/text");
        if (body.isMissingNode() || body.isNull()) return null;
        String text = body.asText();
        int idx = text.toLowerCase().indexOf(firstTerm);
        if (idx < 0) return null;

        int start = Math.max(0, idx - 80);
        int end   = Math.min(text.length(), idx + firstTerm.length() + 120);
        String excerpt = text.substring(start, end).trim();
        if (start > 0) excerpt = "… " + excerpt;
        if (end < text.length()) excerpt = excerpt + " …";
        return excerpt;
    }
}
