/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole BFF against a stub fleet-retrieval (a JDK HttpServer on a
 * random loopback port), so it runs offline.
 *
 * <p>Boot 4 defaults HTTP JSON to Jackson 3 (tools.jackson). This app keeps
 * Jackson 2 both ways: WebClient decodes fleet-retrieval bodies into a
 * com.fasterxml {@link JsonNode} (spring.http.codecs.preferred-json-mapper),
 * and Spring MVC writes JsonNode responses (spring.http.converters.preferred-json-mapper).
 * A Jackson 3 writer would render a JsonNode as a bean
 * ({"array":false,"nodeType":"OBJECT",...}); a Jackson 3 decoder cannot build
 * one at all. These tests pin the bodies.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchUiApplicationTest {

    static final ObjectMapper JSON = new ObjectMapper();

    static final String DOC =
            "{\"id\":\"k1\",\"ht_type\":\"mail\",\"title\":\"Hello\",\"n\":3,\"tags\":[\"a\",\"b\"],\"meta\":{\"ok\":true}}";

    static final String EXECUTE_RESPONSE =
            "{\"totalHits\":1,\"stagesUsed\":[\"lucene\"],\"documents\":[" + DOC + "]}";

    static final AtomicReference<String> lastExecuteBody = new AtomicReference<>();

    static final HttpServer fleet = startFleetStub();

    static HttpServer startFleetStub() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/api/retrieval/documents/idx/k1", ex -> reply(ex, 200, DOC));
            s.createContext("/api/retrieval/execute", ex -> {
                lastExecuteBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(ex, 200, EXECUTE_RESPONSE);
            });
            s.createContext("/", ex -> reply(ex, 404, "{\"error\":\"not found\"}"));
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    @AfterAll
    static void stopFleet() { fleet.stop(0); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) {
        reg.add("hitorro.searchui.fleet.base-url",
                () -> "http://127.0.0.1:" + fleet.getAddress().getPort());
        reg.add("hitorro.searchui.fleet.timeout-ms", () -> "5000");
        reg.add("hitorro.searchui.pipelines-home", () -> {
            try { return Files.createTempDirectory("searchui-pipelines-").toString(); }
            catch (IOException e) { throw new IllegalStateException(e); }
        });
    }

    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerAdapter adapter;

    @Test
    void contextLoads() {
        assertThat(mvc).isNotNull();
    }

    @Test
    void jackson2ConverterIsTheJsonWriter() {
        HttpMessageConverter<?> firstJson = adapter.getMessageConverters().stream()
                .filter(c -> c.canWrite(ObjectNode.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJson).isInstanceOf(MappingJackson2HttpMessageConverter.class);
    }

    /** DocController returns the JsonNode WebClient decoded — must round-trip unchanged. */
    @Test
    void docEndpointPassesJsonNodeThroughAsPlainJson() throws Exception {
        mvc.perform(get("/api/docs/idx/k1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(DOC, true));
    }

    @Test
    void searchEndpointWritesNestedJsonNodesAndOmitsNulls() throws Exception {
        String body = mvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"index\":\"idx\",\"q\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.stages[0]").value("lucene"))
                .andExpect(jsonPath("$.hits[0].id").value("k1"))
                .andExpect(jsonPath("$.hits[0].htType").value("mail"))
                .andReturn().getResponse().getContentAsString();

        // spring.jackson2.default-property-inclusion=non_null: the null `aggregates`
        // is left out, not written as "aggregates":null.
        assertThat(JSON.readTree(body).has("aggregates")).isFalse();

        // The nested JsonNode (Hit.doc) is the upstream document, verbatim.
        JsonNode doc = JSON.readTree(body).path("hits").path(0).path("doc");
        assertThat(doc).isEqualTo(JSON.readTree(DOC));

        // The outbound request body (a JsonNode encoded by WebClient) is plain JSON too.
        JsonNode sent = JSON.readTree(lastExecuteBody.get());
        assertThat(sent.isObject()).isTrue();
        assertThat(sent.has("nodeType")).isFalse();
    }
}
