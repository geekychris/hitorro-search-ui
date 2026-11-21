package com.hitorro.searchui.golden;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Golden REST test: every endpoint's JSON (and NDJSON/CSV export) and every JSON body the BFF sends to
 * fleet-retrieval must be exactly what it produced on Jackson 2 (Spring Boot 4.1.3 with the
 * spring-boot-jackson2 bridge for MVC and the WebClient, main before the migration).
 *
 * <p>{@code src/test/resources/golden/jackson2-rest.txt} was recorded by running this same class on main
 * with {@code -Dgolden.record=true}. fleet-retrieval is a local stub with fixed, deliberately loose JSON
 * (null ids, numbers as strings, missing fields), so the shaping code's accessor semantics are pinned.
 *
 * <p>Controllers covered: IndexController (with a type sidecar), DocController, SearchController
 * (search and search-multiple), ExportController (ndjson, json, csv), SearchExceptionHandler, plus actuator
 * and a probe of the MVC JSON converter.
 */
@SpringBootTest(properties = "golden.rest=true")
@AutoConfigureMockMvc
@DisplayName("Golden REST responses (recorded on Jackson 2)")
class GoldenRestTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/jackson2-rest.txt");
    private static final List<String> received = new ArrayList<>();
    private static final HttpServer fleet = startFleet();
    private static Path pipelines;

    static final String DOCS = "["
            + "{\"id\":\"k1\",\"ht_type\":\"mail\",\"title\":\"Hello golden\",\"body\":\"The golden body text, long enough to"
            + " make a snippet from it and then some more words so that a local snippet has to be cut somewhere.\","
            + "\"n\":3,\"f\":1.10,\"big\":12345678901234567890,\"tags\":[\"a\",\"b\"],\"meta\":{\"ok\":true,\"none\":null},\"none\":null},"
            + "{\"id\":42,\"ht_type\":null,\"title\":\"Numeric id\",\"when\":\"2024-01-02T03:04:05Z\"},"
            + "{\"id\":{\"did\":\"d-7\",\"domain\":\"x\"},\"ht_type\":7,\"title\":{\"mls\":[{\"lang\":\"en\",\"text\":\"Object id\"}]}},"
            + "{\"id\":{\"other\":1,\"nested\":{\"deep\":[1,2]}},\"title\":\"Object id without known keys\"},"
            + "{\"id\":null,\"title\":\"Null id\",\"quote\":\"he said \\\"hi\\\", then left\\nnew line\"},"
            + "{\"title\":\"No id\",\"emoji\":\"😀\",\"tab\":\"a\\tb\"}]";

    static final String EXECUTE = "{\"totalHits\":6,\"stagesUsed\":[\"IndexRetriever\",\"FacetRetriever\",7,null],"
            + "\"documents\":" + DOCS + ","
            + "\"aggregates\":[{\"_aggregate\":\"summarize\",\"byId\":{\"k1\":\"Summary of k1\",\"42\":7,\"d-7\":null}},"
            + "{\"_aggregate\":\"facets\",\"type\":{\"dimension\":\"type\",\"totalCount\":6,\"values\":[{\"value\":\"mail\",\"count\":3},"
            + "{\"value\":null,\"count\":\"2\"},{\"count\":1.9}]},\"lang\":{\"totalCount\":\"x\",\"values\":[]},\"bad\":\"string\"}],"
            + "\"searchTimeMs\":12}";

    static HttpServer startFleet() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", GoldenRestTest::handle);
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().toString();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (received) {
            received.add(ex.getRequestMethod() + " " + path + (body.isEmpty() ? "" : " " + body));
        }
        int status = 200;
        String out;
        if (path.equals("/api/retrieval/health")) {
            out = "{\"status\":\"ok\",\"indexes\":2,\"kvStores\":1,\"mode\":\"local\"}";
        } else if (path.equals("/api/retrieval/indexes")) {
            out = "[{\"name\":\"idx\",\"docCount\":6,\"lastModifiedMs\":1700000000123},"
                    + "{\"name\":\"nosidecar\",\"docCount\":\"12\"},{\"name\":\"weird\",\"lastModifiedMs\":null},{\"docCount\":1}]";
        } else if (path.startsWith("/api/retrieval/documents/idx/k1")) {
            out = "{\"id\":\"k1\",\"ht_type\":\"mail\",\"title\":\"Hello\",\"n\":3,\"f\":1.10,\"tags\":[\"a\",\"b\"],\"meta\":{\"ok\":true},\"none\":null}";
        } else if (path.startsWith("/api/retrieval/documents/mail-kv/k2")) {
            out = "{\"id\":\"k2\",\"from\":\"kv fallback\"}";
        } else if (path.startsWith("/api/retrieval/documents/")) {
            status = 404;
            out = "{\"error\":\"no such document\"}";
        } else if (path.startsWith("/api/retrieval/fields/idx")) {
            out = "{\"indexName\":\"idx\",\"numDocs\":6,\"fields\":[{\"name\":\"title.mls.en\",\"indexed\":true,\"docValuesType\":\"NONE\"},"
                    + "{\"name\":\"ht_type\",\"indexed\":true,\"docValuesType\":\"SORTED\",\"sampleTerms\":[\"mail\"]},"
                    + "{\"name\":\"date_l\",\"indexed\":true,\"docValuesType\":\"NUMERIC\",\"pointDimensionCount\":1},"
                    + "{\"name\":\"id.did\",\"indexed\":true}]}";
        } else if (path.startsWith("/api/retrieval/fields/")) {
            status = 404;
            out = "{\"error\":\"no such index\"}";
        } else if (path.equals("/api/retrieval/execute") || path.equals("/api/retrieval/search-multiple")) {
            if (body.contains("\"down\"")) {
                status = 503;
                out = "{\"error\":\"no fleet-retrieval endpoints discovered\"}";
            } else if (body.contains("\"teapot\"")) {
                status = 418;
                out = "not json at all";
            } else if (body.contains("\"empty\"")) {
                out = "{\"totalHits\":0}";
            } else if (body.contains("\"weird\"")) {
                out = "{\"totalHits\":\"7\",\"stagesUsed\":{\"x\":1},\"hits\":[{\"id\":true,\"ht_type\":[1]}],\"aggregates\":[]}";
            } else if (body.contains("\"offset\":20") || body.contains("\"offset\":100")) {
                out = "{\"totalHits\":6,\"documents\":[]}";
            } else {
                out = EXECUTE;
            }
        } else {
            status = 404;
            out = "{\"error\":\"not found\"}";
        }
        byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) throws IOException {
        pipelines = Files.createTempDirectory("searchui-golden-");
        Path sidecar = pipelines.resolve("lucene/idx/.jvs-type.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "{\"name\":\"mail\",\"super\":\"core_sysobject\",\"fields\":["
                + "{\"name\":\"title\",\"type\":\"core_mls\",\"i18n\":true,\"groups\":[{\"name\":\"index\",\"method\":\"text\",\"tags\":[\"basic\"]}]},"
                + "{\"name\":\"ht_type\",\"type\":\"core_string\",\"groups\":[{\"name\":\"index\",\"method\":\"identifier\"}]},"
                + "{\"name\":\"date\",\"type\":\"core_date\",\"groups\":[{\"name\":\"index\",\"method\":\"date\"}]},"
                + "{\"name\":\"id\",\"type\":\"core_id\"},{\"name\":\"n\",\"type\":\"core_long\",\"vector\":false},"
                + "{\"name\":\"odd\",\"type\":null,\"groups\":\"none\"},{\"type\":\"core_string\"}]}");
        reg.add("hitorro.searchui.fleet.base-url", () -> "http://127.0.0.1:" + fleet.getAddress().getPort());
        reg.add("hitorro.searchui.fleet.timeout-ms", () -> "5000");
        reg.add("hitorro.searchui.pipelines-home", pipelines::toString);
    }

    @AfterAll
    static void stop() {
        fleet.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    private GoldenRecorder g;

    @Test
    @DisplayName("every endpoint returns the Jackson 2 JSON")
    void endpointsMatchJackson2() throws Exception {
        g = new GoldenRecorder(GOLDEN)
                .volatileKeys("tookMs|timestamp")
                .replace(pipelines.toString(), "<PIPELINES>")
                .replace("127.0.0.1:" + fleet.getAddress().getPort(), "<FLEET>")
                // Accepted differences: the generic 500 handler returns the exception's own message, and these
                // two come from Jackson. Status and shape must still match; only the message text is masked.
                // withArray() on an object node: Jackson 2 UnsupportedOperationException, Jackson 3 JsonNodeException.
                .acceptDifference("search weird", "\"error\":\"[A-Za-z]+Exception: (?:[^\"\\\\]|\\\\.)*\"", "\"error\":\"<exception message>\"")
                // Jackson's wording for a string where an object is expected.
                .acceptDifference("search wrong type", "JSON parse error: (?:[^\"\\\\]|\\\\.)*\"", "JSON parse error: <Jackson message>\"");
        drain();

        step("indexes", get("/api/indexes"));
        step("index schema", get("/api/indexes/idx/schema"));
        step("index schema no sidecar", get("/api/indexes/nosidecar/schema"));

        step("doc", get("/api/docs/idx/k1"));
        step("doc kv fallback", get("/api/docs/mail-idx/k2"));
        step("doc missing", get("/api/docs/idx/nope"));

        step("search minimal", search("{\"index\":\"idx\",\"q\":\"hello\"}"));
        step("search full", search("{\"index\":\"idx\",\"q\":\"golden \\\"exact phrase\\\" a:b\",\"filters\":{\"type\":[\"mail\",\"x y\"],"
                + "\"lang\":[\"en\"]},\"facets\":[\"type\",\"lang\",\"missing\"],\"sort\":\"date_desc\",\"page\":2,\"size\":5,"
                + "\"mode\":\"analyst\",\"lang\":\"de\"}"));
        step("search sort chain", search("{\"index\":\"idx\",\"sortChain\":[{\"field\":\"date_l\",\"direction\":\"asc\"},"
                + "{\"field\":\"\",\"direction\":\"desc\"},{\"field\":\"title\"}],\"mergePolicies\":{\"facets\":\"sum\"},\"size\":500}"));
        step("search empty", search("{\"index\":\"empty\"}"));
        step("search weird", search("{\"index\":\"weird\",\"q\":\"\"}"));
        step("search upstream down", search("{\"index\":\"down\",\"q\":\"x\"}"));
        step("search upstream teapot", search("{\"index\":\"teapot\"}"));
        step("search unknown property", search("{\"index\":\"idx\",\"bogus\":{\"a\":1},\"page\":-3,\"size\":0}"));
        step("search null for int", search("{\"index\":\"idx\",\"page\":null,\"size\":\"7\"}"));
        step("search malformed", search("{\"index\":"));
        step("search wrong type", search("{\"index\":\"idx\",\"filters\":\"nope\"}"));

        step("search multiple", post("/api/search-multiple").contentType(MediaType.APPLICATION_JSON).content(
                "{\"indexes\":[\"idx\",\"other\"],\"q\":\"golden\",\"facets\":[\"type\"],\"merger\":\"rrf\",\"sort\":\"title_asc\","
                        + "\"mergePolicies\":{\"facets\":\"max\"},\"page\":1,\"size\":3}"));
        step("search multiple no indexes", post("/api/search-multiple").contentType(MediaType.APPLICATION_JSON).content("{\"q\":\"x\"}"));
        step("search multiple down", post("/api/search-multiple").contentType(MediaType.APPLICATION_JSON).content("{\"indexes\":[\"down\"]}"));

        for (String fmt : List.of("ndjson", "json", "csv")) {
            step("export " + fmt, post("/api/search/export").param("format", fmt).param("max", "20")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"index\":\"idx\",\"q\":\"golden\",\"size\":20}"));
        }
        step("export bad format", post("/api/search/export").param("format", "xml")
                .contentType(MediaType.APPLICATION_JSON).content("{\"index\":\"idx\"}"));

        step("actuator health", get("/actuator/health"));
        step("actuator info", get("/actuator/info"));
        step("spa route", get("/search"));

        ConverterProbe.run(ConverterProbe.jsonConverter(adapter), g);
        List<String> diffs = g.finish();
        assertTrue(diffs.isEmpty(), diffs.size() + " responses differ from Jackson 2:\n" + String.join("\n", diffs));
    }

    private static MockHttpServletRequestBuilder search(String body) {
        return post("/api/search").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A request, then the requests (with bodies) the BFF sent to fleet-retrieval while handling it. */
    private void step(String name, MockHttpServletRequestBuilder req) throws Exception {
        g.rest(mvc, name, req);
        String sent = drain();
        if (!sent.isEmpty()) g.put(name + " -> fleet", sent);
    }

    private static String drain() {
        synchronized (received) {
            String s = String.join("\n", received);
            received.clear();
            return s;
        }
    }
}
