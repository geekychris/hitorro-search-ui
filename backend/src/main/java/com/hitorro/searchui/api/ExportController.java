/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.searchui.client.FleetRetrievalClient;
import com.hitorro.searchui.shape.JvsQueryShaper;
import com.hitorro.searchui.shape.SearchRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Streaming export of a search result set as JSON array, NDJSON stream,
 * or CSV rows. Backed by paginated calls to fleet-retrieval — pulls
 * pages of {@link #EXPORT_PAGE_SIZE} until the requested {@code max} is
 * reached or the index runs out. Each page becomes one write to the
 * response's OutputStream so browsers can start downloading before the
 * whole set is ready.
 *
 * <p>Kept as its own controller (not on {@link SearchController}) so
 * the streaming lifecycle + Content-Disposition headers don't tangle
 * the search endpoint's JSON DTO contract.</p>
 *
 * <p>Query shape matches {@code POST /api/search} exactly — the whole
 * UI-friendly {@link SearchRequest} is accepted. {@code page} and
 * {@code size} are ignored here (we page through internally); pass
 * everything else (index / q / filters / mode / lang / sort) to scope
 * the export to what the user was looking at.</p>
 */
@RestController
@RequestMapping("/api")
public class ExportController {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** One page of results per fleet-retrieval call. 100 is a sweet
     *  spot — bigger pages amortise the coordinator round-trip; smaller
     *  ones start streaming sooner. */
    private static final int EXPORT_PAGE_SIZE = 100;

    public enum Format { JSON, NDJSON, CSV }

    private final FleetRetrievalClient fleet;
    private final JvsQueryShaper shaper;

    public ExportController(FleetRetrievalClient fleet, JvsQueryShaper shaper) {
        this.fleet = fleet;
        this.shaper = shaper;
    }

    @PostMapping(value = "/search/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody SearchRequest req,
            @RequestParam(defaultValue = "ndjson") String format,
            @RequestParam(defaultValue = "10000") int max) {

        Format fmt = parseFormat(format);
        int cap = Math.max(1, Math.min(max, 100_000));

        String contentType = switch (fmt) {
            case JSON   -> MediaType.APPLICATION_JSON_VALUE;
            case NDJSON -> "application/x-ndjson";
            case CSV    -> "text/csv";
        };
        String ext = switch (fmt) { case JSON -> "json"; case NDJSON -> "ndjson"; case CSV -> "csv"; };
        String filename = String.format("%s.%s", req.index() == null ? "export" : req.index(), ext);

        StreamingResponseBody body = out -> {
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                new Streamer(fleet, shaper, req, cap, fmt, w).run();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType + "; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static Format parseFormat(String s) {
        try { return Format.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format must be one of json | ndjson | csv (got: " + s + ")");
        }
    }

    /**
     * Pulls pages from fleet-retrieval and writes each row to the
     * response stream in the requested format. Kept as an inner class
     * so the writer + format + accumulator state doesn't leak into the
     * controller method's readability.
     */
    private static final class Streamer {
        private final FleetRetrievalClient fleet;
        private final JvsQueryShaper shaper;
        private final SearchRequest baseReq;
        private final int cap;
        private final Format fmt;
        private final BufferedWriter w;

        Streamer(FleetRetrievalClient fleet, JvsQueryShaper shaper, SearchRequest baseReq,
                 int cap, Format fmt, BufferedWriter w) {
            this.fleet = fleet; this.shaper = shaper; this.baseReq = baseReq;
            this.cap = cap; this.fmt = fmt; this.w = w;
        }

        void run() throws java.io.IOException {
            // CSV needs a header row derived from the first page's keys.
            // For JSON we emit an opening `[` and comma between rows.
            // NDJSON needs nothing between rows — just newlines.
            List<String> csvCols = null;
            boolean first = true;
            if (fmt == Format.JSON) w.write('[');

            int written = 0;
            int page = 0;
            while (written < cap) {
                int wanted = Math.min(EXPORT_PAGE_SIZE, cap - written);
                SearchRequest req = new SearchRequest(
                        baseReq.index(), baseReq.q(), baseReq.filters(), baseReq.facets(),
                        baseReq.sort(), page, wanted,
                        baseReq.mode() == null ? "analyst" : baseReq.mode(),
                        baseReq.lang());
                JsonNode coordResp = fleet.execute(shaper.toExecuteRequest(req));
                JsonNode docs = coordResp.has("documents") ? coordResp.get("documents") : coordResp.get("hits");
                if (docs == null || !docs.isArray() || docs.size() == 0) break;

                if (fmt == Format.CSV && csvCols == null) {
                    csvCols = collectCsvHeader(docs);
                    writeCsvRow(csvCols, w);
                }

                for (JsonNode doc : docs) {
                    if (written >= cap) break;
                    switch (fmt) {
                        case JSON -> {
                            if (!first) w.write(",");
                            w.write(JSON.writeValueAsString(doc));
                            first = false;
                        }
                        case NDJSON -> {
                            w.write(JSON.writeValueAsString(doc));
                            w.write('\n');
                        }
                        case CSV -> writeCsvRow(csvCols, doc, w);
                    }
                    written++;
                }
                w.flush();

                // Only advance if we got the requested page-size (else EOF).
                if (docs.size() < wanted) break;
                page++;
            }
            if (fmt == Format.JSON) w.write(']');
        }

        /** Merge the top-level keys of the first page's docs into a
         *  stable header list. Nested objects render as JSON blobs;
         *  users who need a specific flattening can post a downstream
         *  transform themselves. */
        private static List<String> collectCsvHeader(JsonNode docs) {
            // Preserve iteration order across the first page for a
            // deterministic column order.
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            for (JsonNode d : docs) {
                if (!d.isObject()) continue;
                Iterator<String> it = d.fieldNames();
                while (it.hasNext()) keys.add(it.next());
            }
            return new ArrayList<>(keys);
        }

        private static void writeCsvRow(List<String> cols, BufferedWriter w) throws java.io.IOException {
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) w.write(',');
                w.write(csvEscape(cols.get(i)));
            }
            w.write('\n');
        }

        private static void writeCsvRow(List<String> cols, JsonNode doc, BufferedWriter w) throws java.io.IOException {
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) w.write(',');
                JsonNode v = doc.get(cols.get(i));
                if (v == null || v.isNull()) continue;
                w.write(csvEscape(v.isValueNode() ? v.asText() : v.toString()));
            }
            w.write('\n');
        }

        /** RFC 4180: quote values containing comma / quote / newline;
         *  double any embedded quotes. */
        private static String csvEscape(String s) {
            if (s == null) return "";
            boolean needsQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                    || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
            if (!needsQuote) return s;
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
    }
}
