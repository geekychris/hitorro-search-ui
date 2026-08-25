/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.searchui.client.FleetRetrievalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Index discovery + schema. Two endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/indexes} — list of {name, docCount, lastModifiedMs,
 *       hasSidecar}. {@code hasSidecar=true} means we can serve a schema
 *       for it; the React shell disables end-user mode's per-type
 *       overrides when it's false and falls back to the generic renderer.</li>
 *   <li>{@code GET /api/indexes/{name}/schema} — the raw JVS type JSON
 *       plus a small "renderer hints" section listing which fields are
 *       facet-friendly / date / text / mls.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class IndexController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FleetRetrievalClient fleet;
    private final Path pipelinesHome;

    public IndexController(
            FleetRetrievalClient fleet,
            @Value("${hitorro.searchui.pipelines-home}") String pipelinesHome) {
        this.fleet = fleet;
        this.pipelinesHome = Path.of(pipelinesHome);
    }

    @GetMapping("/indexes")
    public ArrayNode indexes() {
        ArrayNode out = JSON.createArrayNode();
        JsonNode upstream = fleet.indexes();
        for (JsonNode i : upstream) {
            ObjectNode row = out.addObject();
            String name = i.path("name").asText();
            row.put("name", name);
            row.put("docCount",       i.path("docCount").asLong(-1));
            row.put("lastModifiedMs", i.path("lastModifiedMs").asLong(0));
            row.put("hasSidecar",     Files.exists(sidecarPath(name)));
        }
        return out;
    }

    @GetMapping("/indexes/{name}/schema")
    public ResponseEntity<ObjectNode> schema(@PathVariable String name) throws IOException {
        Path sidecar = sidecarPath(name);
        if (!Files.exists(sidecar)) {
            ObjectNode err = JSON.createObjectNode();
            err.put("error", "no .jvs-type.json sidecar for index: " + name);
            err.put("path", sidecar.toString());
            return ResponseEntity.status(404).body(err);
        }
        JsonNode type = JSON.readTree(sidecar.toFile());
        JsonNode luceneFields = safeFleetFields(name);

        ObjectNode out = JSON.createObjectNode();
        out.set("type", type);
        out.set("rendererHints", rendererHints(type, luceneFields));
        return ResponseEntity.ok(out);
    }

    /** Fetch {@code /api/retrieval/fields/{name}} and tolerate any
     *  upstream failure (missing endpoint on older fleet, network hiccup)
     *  — the type-driven classifier still runs, just without the
     *  physical-field-augmented hints. */
    private JsonNode safeFleetFields(String indexName) {
        try {
            return fleet.fields(indexName);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    /**
     * Walk the type's fields once and emit per-field hints the React
     * renderer uses to pick components without re-parsing the type tree
     * on every doc:
     *
     * <pre>{@code
     * {
     *   "facetable": ["sender_domain", "read", "flagged"],
     *   "textSearch": ["title.mls.clean", "body.mls.clean"],
     *   "date":      ["times.date_received"],
     *   "mls":       ["title", "body"],
     *   "identifier":["id", "sender_domain", ...],
     *   "sortable":  ["size_bytes", "times.date_received", ...]
     * }
     * }</pre>
     *
     * <p><b>Two-pass classification.</b></p>
     * <ol>
     *   <li>Walk the type sidecar's direct {@code fields[]} — this covers
     *       what the pipeline declared. Only sees fields on THIS type,
     *       not inherited ones from {@code super}.</li>
     *   <li>Walk the Lucene index's {@code FieldInfos} via the fleet's
     *       {@code /api/retrieval/fields/{name}}. This is the ground truth
     *       for what's actually queryable / sortable / facetable — it
     *       includes every field the projection pipeline emitted whether
     *       declared on the concrete type, on a super, or dynamically.
     *       We strip the {@code LuceneFieldType} suffix scheme to
     *       recover the logical JVS path, then bucket by suffix:
     *       {@code date_s/date_m → date + sortable}, {@code long_*} /
     *       {@code double_*} / {@code int_* → sortable}, {@code identifier_s
     *       → identifier + facetable}, {@code text_*_m → textSearch (via
     *       the parent .mls path)}.</li>
     * </ol>
     * <p>Pass 2 supplements pass 1 — anything already in a bucket doesn't
     * duplicate. Missing / failed fleet call just leaves the type-driven
     * hints unaugmented (schema still usable).</p>
     */
    private static ObjectNode rendererHints(JsonNode type, JsonNode luceneFields) {
        ObjectNode hints = JSON.createObjectNode();
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        buckets.put("facetable",  new ArrayList<>());
        buckets.put("textSearch", new ArrayList<>());
        buckets.put("date",       new ArrayList<>());
        buckets.put("mls",        new ArrayList<>());
        buckets.put("identifier", new ArrayList<>());
        buckets.put("sortable",   new ArrayList<>());

        // Pass 1 — direct fields on the concrete type. Fastest to walk
        // and doesn't require fleet-retrieval to be reachable.
        JsonNode fields = type.get("fields");
        if (fields != null && fields.isArray()) {
            for (JsonNode f : fields) {
                String fname = f.path("name").asText();
                String ftype = f.path("type").asText();
                classifyField(fname, ftype, f, buckets);
            }
        }
        // Pass 2 — physical Lucene FieldInfos from the fleet. Recovers
        // inherited fields (title/body/times from sysobject) and any
        // dynamically-projected fields the sidecar can't advertise on
        // its own.
        classifyLuceneFields(luceneFields, buckets);

        buckets.forEach((k, v) -> {
            ArrayNode arr = hints.putArray(k);
            v.forEach(arr::add);
        });
        return hints;
    }

    private static void classifyField(String name, String type, JsonNode fieldDef, Map<String, List<String>> b) {
        if ("core_mls".equals(type)) {
            addUnique(b.get("mls"), name);
            addUnique(b.get("textSearch"), name + ".mls.clean");
        }
        if ("core_date".equals(type)) {
            addUnique(b.get("date"), name);
            // NOTE: don't add to sortable from the type sidecar — only
            // fields that the pipeline ACTUALLY indexed with DocValues
            // can be sorted, and that's what pass 2 (Lucene FieldInfos)
            // reports. A sidecar-declared field the pipeline hasn't
            // indexed yet must NOT show up in the sort menu.
        }
        // Same reasoning applies to long / int / double — sortability
        // is a pipeline reality, not a type declaration.

        JsonNode groups = fieldDef.get("groups");
        if (groups != null && groups.isArray()) {
            for (JsonNode g : groups) {
                if (!"index".equals(g.path("name").asText())) continue;
                String method = g.path("method").asText();
                if ("identifier".equals(method)) {
                    addUnique(b.get("identifier"), name);
                    addUnique(b.get("facetable"), name);
                }
            }
        }
    }

    /**
     * Enumerate Lucene FieldInfos from the fleet's {@code /fields/{name}}
     * response and bucket each field by its LuceneFieldType suffix.
     * Suffix grammar (from {@code LuceneFieldType}): logical path +
     * {@code ".{indexType}[_{lang}]_{s|m}"}. So a match like
     * {@code times.date_received.date_s} strips back to logical
     * {@code times.date_received} in the {@code date} bucket, and the
     * lang segment on i18n text fields is stripped as well.
     */
    private static void classifyLuceneFields(JsonNode fleetFields, Map<String, List<String>> b) {
        if (fleetFields == null || !fleetFields.has("fields")) return;
        for (JsonNode fi : fleetFields.get("fields")) {
            String phys = fi.path("name").asText();
            String dv   = fi.path("docValuesType").asText("NONE");
            SuffixParse sp = parseSuffix(phys);
            if (sp == null) continue;                    // no recognised suffix
            String logical = sp.logical;
            String indexType = sp.indexType;

            boolean sortableDv = "NUMERIC".equals(dv) || "SORTED_NUMERIC".equals(dv);
            switch (indexType) {
                case "date" -> {
                    addUnique(b.get("date"), logical);
                    if (sortableDv) addUnique(b.get("sortable"), logical);
                }
                case "long", "int", "double" -> {
                    if (sortableDv) addUnique(b.get("sortable"), logical);
                }
                case "identifier" -> {
                    addUnique(b.get("identifier"), logical);
                    addUnique(b.get("facetable"), logical);
                }
                case "text", "textmarkup" -> {
                    // logical here already includes the .mls.<subfield>
                    // structure (e.g. "title.mls.segmented_span"); the
                    // renderer only cares about MLS-root fields for text
                    // search, so we skip these — the type-driven pass
                    // covers them via the "core_mls" branch.
                }
                default -> { /* boolean, other — no hint bucket applies */ }
            }
        }
    }

    /** Strip a {@code LuceneFieldType} suffix off a physical field name.
     *  Returns {@code null} if the name doesn't match the suffix grammar
     *  (leaves like {@code _score}, internal fields, etc.). */
    private static SuffixParse parseSuffix(String phys) {
        int dot = phys.lastIndexOf('.');
        if (dot < 0) return null;
        String tail = phys.substring(dot + 1);   // e.g. "date_s", "text_en_m", "long_m"
        String[] parts = tail.split("_");
        if (parts.length < 2) return null;
        String multi = parts[parts.length - 1];
        if (!"s".equals(multi) && !"m".equals(multi)) return null;
        String indexType = parts[0];
        return new SuffixParse(phys.substring(0, dot), indexType);
    }

    private record SuffixParse(String logical, String indexType) { }

    private static void addUnique(List<String> bucket, String value) {
        if (value != null && !value.isBlank() && !bucket.contains(value)) bucket.add(value);
    }

    private Path sidecarPath(String name) {
        return pipelinesHome.resolve("lucene").resolve(name).resolve(".jvs-type.json");
    }
}
