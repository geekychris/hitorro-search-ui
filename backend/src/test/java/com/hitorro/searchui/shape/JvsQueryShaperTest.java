/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.shape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JvsQueryShaperTest {

    private final JvsQueryShaper shaper = new JvsQueryShaper();

    @Nested
    @DisplayName("query string composition")
    class QueryComposition {
        @Test
        void endUserBareTermFansOutAcrossDefaultTextFields() {
            SearchRequest r = new SearchRequest(
                    "mail-enriched-idx", "chase",
                    null, null, null, null, null, "end-user", "en");
            String s = shaper.composeQueryString(r);
            assertThat(s).contains("body.mls.clean:chase");
            assertThat(s).contains("title.mls.clean:chase");
            assertThat(s).contains(" OR ");
        }

        @Test
        void analystModePassesQuerySyntaxUnchanged() {
            SearchRequest r = new SearchRequest(
                    "mail-enriched-idx", "body.mls.segmented_ner:NE_Person",
                    null, null, null, null, null, "analyst", "en");
            String s = shaper.composeQueryString(r);
            // Only the analyst clause + no filters — wrapped in parens.
            assertThat(s).isEqualTo("(body.mls.segmented_ner:NE_Person)");
        }

        @Test
        void endUserFieldedQueryLooksAnalystShaped() {
            // Even in end-user mode, a query that already has a colon is
            // treated as fielded (Lucene syntax) — no default-field fan-out.
            SearchRequest r = new SearchRequest(
                    "mail-enriched-idx", "sender_domain:redfin.com",
                    null, null, null, null, null, "end-user", "en");
            assertThat(shaper.composeQueryString(r)).isEqualTo("(sender_domain:redfin.com)");
        }

        @Test
        void filtersAppendAsRequiredClauses() {
            SearchRequest r = new SearchRequest(
                    "mail-enriched-idx", "chase",
                    Map.of("sender_domain", List.of("redfin.com", "substack.com"),
                           "read",          List.of("false")),
                    null, null, null, null, "end-user", "en");
            String s = shaper.composeQueryString(r);
            assertThat(s).contains("+(sender_domain:redfin.com OR sender_domain:substack.com)");
            assertThat(s).contains("+(read:false)");
        }

        @Test
        void valueWithSpacesGetsQuoted() {
            SearchRequest r = new SearchRequest(
                    "idx", null,
                    Map.of("subject", List.of("hello world")),
                    null, null, null, null, "end-user", "en");
            String s = shaper.composeQueryString(r);
            assertThat(s).contains("subject:\"hello world\"");
        }

        @Test
        void matchAllIsStar() {
            SearchRequest r = new SearchRequest("idx", "*:*", null, null, null, null, null, null, null);
            assertThat(shaper.composeQueryString(r)).isEqualTo("*:*");
        }
    }

    @Nested
    @DisplayName("execute-request body assembly")
    class ExecuteBody {
        @Test
        void endUserModeIncludesSummarize() {
            SearchRequest r = new SearchRequest(
                    "mail-enriched-idx", "chase",
                    null, List.of("sender_domain"), null, 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("indexName").asText()).isEqualTo("mail-enriched-idx");
            JsonNode q = body.get("query");
            assertThat(q.has("summarize")).isTrue();
            assertThat(q.get("search").get("facets").get(0).asText()).isEqualTo("sender_domain");
            assertThat(q.get("search").get("limit").asInt()).isEqualTo(20);
            assertThat(q.get("search").get("offset").asInt()).isEqualTo(0);
        }

        @Test
        void analystModeSkipsSummarize() {
            SearchRequest r = new SearchRequest(
                    "idx", "*:*", null, null, null, 0, 20, "analyst", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").has("summarize")).isFalse();
        }

        @Test
        void pageBecomesOffset() {
            SearchRequest r = new SearchRequest("idx", "chase", null, null, null, 3, 25, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").get("search").get("offset").asInt()).isEqualTo(75);
            assertThat(body.get("query").get("search").get("limit").asInt()).isEqualTo(25);
        }

        @Test
        void shorthandSortEmitsBothSearchSortAndTopLevelSort() {
            // Sort at query.search.sort drives single-index IndexRetriever;
            // top-level sort drives /search-multiple's SelectTreeMerger.
            // Same array in both — coordinator only reads the one that
            // matches its endpoint, so emitting both is safe.
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    "times.date_received:desc", 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);

            JsonNode searchSort = body.get("query").get("search").get("sort");
            JsonNode topSort    = body.get("sort");
            assertThat(searchSort).isNotNull();
            assertThat(topSort).isNotNull();
            assertThat(searchSort.get(0).get("field").asText()).isEqualTo("times.date_received");
            assertThat(searchSort.get(0).get("direction").asText()).isEqualTo("desc");
            assertThat(topSort.get(0).get("field").asText()).isEqualTo("times.date_received");
            assertThat(topSort.get(0).get("direction").asText()).isEqualTo("desc");
        }

        @Test
        void relevanceEmitsNoSortNodes() {
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    "relevance", 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").get("search").has("sort")).isFalse();
            assertThat(body.has("sort")).isFalse();
        }

        @Test
        void nullSortEmitsNoSortNodes() {
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    null, 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").get("search").has("sort")).isFalse();
            assertThat(body.has("sort")).isFalse();
        }

        @Test
        void ascShorthandStaysAsc() {
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    "title:asc", 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").get("search").get("sort").get(0).get("direction").asText())
                    .isEqualTo("asc");
        }

        @Test
        void bareFieldDefaultsToDesc() {
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    "times.date_received", 0, 20, "end-user", "en");
            ObjectNode body = shaper.toExecuteRequest(r);
            assertThat(body.get("query").get("search").get("sort").get(0).get("direction").asText())
                    .isEqualTo("desc");
        }

        @Test
        void explicitSortChainWinsOverShorthand() {
            // If a caller passes both — sortChain (structured) takes
            // precedence, shorthand is ignored. Matches resolveSortChain's
            // contract so upstream sort-menu changes don't override an
            // explicit chain composed by e.g. a saved-search feature.
            SearchRequest r = new SearchRequest(
                    "idx", "chase", null, null,
                    "score", 0, 20, "end-user", "en",
                    List.of(new SearchRequest.SortSpec("date", "asc")),
                    null);
            ObjectNode body = shaper.toExecuteRequest(r);
            JsonNode sort = body.get("query").get("search").get("sort");
            assertThat(sort.get(0).get("field").asText()).isEqualTo("date");
            assertThat(sort.get(0).get("direction").asText()).isEqualTo("asc");
        }
    }

    @Nested
    @DisplayName("response translation")
    class Response {
        private final ObjectMapper JSON = new ObjectMapper();

        @Test
        void unwrapsFacetsFromAggregatesArray() throws Exception {
            String raw = """
                {
                  "totalHits": 42,
                  "stagesUsed": ["IndexRetriever", "FacetRetriever"],
                  "documents": [
                    {"ht_type": "mail_email", "id": {"id": "710", "did": "710", "domain": "mail"},
                     "title": {"mls": [{"lang":"en","text":"hello","clean":"hello"}]}}
                  ],
                  "aggregates": [
                    {"_aggregate": "summary", "totalHits": 42},
                    {"_aggregate": "facets",
                     "sender_domain": {"dimension":"sender_domain","totalCount":42,
                                       "values":[{"value":"redfin.com","count":6}]}}
                  ]
                }
                """;
            SearchRequest req = new SearchRequest("idx", "hello", null, List.of("sender_domain"),
                                                  null, 0, 20, "end-user", "en");
            SearchResponse out = shaper.fromExecuteResponse(req, JSON.readTree(raw), 12);
            assertThat(out.total()).isEqualTo(42);
            assertThat(out.hits()).hasSize(1);
            assertThat(out.hits().get(0).id()).isEqualTo("710");
            assertThat(out.hits().get(0).htType()).isEqualTo("mail_email");
            assertThat(out.facets()).containsKey("sender_domain");
            assertThat(out.facets().get("sender_domain").values().get(0).value()).isEqualTo("redfin.com");
            assertThat(out.facets().get("sender_domain").values().get(0).count()).isEqualTo(6);
        }

        @Test
        void localSnippetFallsBackWhenSummarizeAbsent() throws Exception {
            String raw = """
                {"totalHits": 1, "stagesUsed": [],
                 "documents": [
                   {"id": {"id": "1"},
                    "body": {"mls": [{"lang":"en","clean":"lorem ipsum dolor sit chase amet consectetur adipiscing"}]}}
                 ],
                 "aggregates": []
                }""";
            SearchRequest req = new SearchRequest("idx", "chase", null, null, null, null, null, "end-user", null);
            SearchResponse out = shaper.fromExecuteResponse(req, JSON.readTree(raw), 0);
            assertThat(out.hits().get(0).snippet()).contains("chase");
        }
    }
}
