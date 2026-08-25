/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.searchui.client.FleetRetrievalClient;
import com.hitorro.searchui.shape.JvsQueryShaper;
import com.hitorro.searchui.shape.SearchRequest;
import com.hitorro.searchui.shape.SearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint the React SearchBox / QueryBar POSTs to.
 * Takes a UI-friendly {@link SearchRequest}, shapes it into the JVS
 * query fleet-retrieval expects, unwraps the response into a flat
 * {@link SearchResponse}. Errors bubble to
 * {@code SearchExceptionHandler}.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

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
}
