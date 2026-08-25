/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.searchui.client.FleetRetrievalClient;
import com.hitorro.searchui.client.FleetRetrievalClient.FleetException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-doc retrieval — used by the detail drawer when the user clicks
 * a search hit. Proxies fleet-retrieval's
 * {@code /api/retrieval/documents/{indexName}/{key}}, which reads from a
 * paired KV store keyed by the {@code indexName} argument.
 *
 * <p>Naming resolution: the pipeline convention is that a Lucene index
 * named {@code foo-idx} is paired with a KV store named {@code foo-kv}
 * (see mail-enrich-search.yaml for an example). The React shell only
 * knows the Lucene index name (that's what the search results carry),
 * so this endpoint tries the caller's name verbatim first and, on 404,
 * retries after swapping a trailing {@code -idx} for {@code -kv}. Any
 * other pairing naming (e.g. same-name index and KV) already works via
 * the verbatim first attempt.</p>
 */
@RestController
@RequestMapping("/api")
public class DocController {

    private final FleetRetrievalClient fleet;

    public DocController(FleetRetrievalClient fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/docs/{index}/{key}")
    public JsonNode doc(@PathVariable String index, @PathVariable String key) {
        try {
            return fleet.document(index, key);
        } catch (FleetException e) {
            if (e.upstreamStatus == 404 && index.endsWith("-idx")) {
                String kvName = index.substring(0, index.length() - 4) + "-kv";
                return fleet.document(kvName, key);
            }
            throw e;
        }
    }
}
