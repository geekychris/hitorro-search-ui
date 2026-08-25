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

        ObjectNode out = JSON.createObjectNode();
        out.set("type", type);
        out.set("rendererHints", rendererHints(type));
        return ResponseEntity.ok(out);
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
     *   "date": ["times.date_received"],
     *   "mls": ["title", "body"],
     *   "identifier": ["id", "sender_domain", ...]
     * }
     * }</pre>
     */
    private static ObjectNode rendererHints(JsonNode type) {
        ObjectNode hints = JSON.createObjectNode();
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        buckets.put("facetable",  new ArrayList<>());
        buckets.put("textSearch", new ArrayList<>());
        buckets.put("date",       new ArrayList<>());
        buckets.put("mls",        new ArrayList<>());
        buckets.put("identifier", new ArrayList<>());

        // Only walk direct fields — nested MLS elements are handled by
        // the walker anyway; we just need top-level hints for the
        // facet-panel + sort-menu wiring.
        JsonNode fields = type.get("fields");
        if (fields != null && fields.isArray()) {
            for (JsonNode f : fields) {
                String fname = f.path("name").asText();
                String ftype = f.path("type").asText();
                classifyField(fname, ftype, f, buckets);
            }
        }
        buckets.forEach((k, v) -> {
            ArrayNode arr = hints.putArray(k);
            v.forEach(arr::add);
        });
        return hints;
    }

    private static void classifyField(String name, String type, JsonNode fieldDef, Map<String, List<String>> b) {
        if ("core_mls".equals(type)) {
            b.get("mls").add(name);
            b.get("textSearch").add(name + ".mls.clean");
        }
        if ("core_date".equals(type)) b.get("date").add(name);

        JsonNode groups = fieldDef.get("groups");
        if (groups != null && groups.isArray()) {
            for (JsonNode g : groups) {
                if (!"index".equals(g.path("name").asText())) continue;
                String method = g.path("method").asText();
                if ("identifier".equals(method)) {
                    b.get("identifier").add(name);
                    b.get("facetable").add(name);
                }
            }
        }
    }

    private Path sidecarPath(String name) {
        return pipelinesHome.resolve("lucene").resolve(name).resolve(".jvs-type.json");
    }
}
