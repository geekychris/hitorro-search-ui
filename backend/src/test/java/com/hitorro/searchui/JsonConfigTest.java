package com.hitorro.searchui;

import com.hitorro.searchui.config.JsonConfig;
import com.hitorro.searchui.golden.ConverterProbe;
import com.hitorro.searchui.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot's Jackson 3 mapper has the settings that keep the Jackson 2 format (see {@link JsonConfig}). */
@SpringBootTest
@DisplayName("Boot's JSON mapper keeps the Jackson 2 settings")
class JsonConfigTest {

    @org.springframework.test.context.DynamicPropertySource
    static void props(org.springframework.test.context.DynamicPropertyRegistry reg) {
        reg.add("hitorro.searchui.fleet.base-url", () -> "http://127.0.0.1:9");
    }

    @Autowired
    private JsonMapper mapper;

    @Test
    @DisplayName("nulls are left out (spring.jackson.default-property-inclusion=non_null)")
    void nonNullInclusion() {
        assertThat(mapper.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of("a", 1))))
                .isEqualTo("{\"a\":1}");
        record R(String a, String b) {}
        assertThat(mapper.writeValueAsString(new R("x", null))).isEqualTo("{\"a\":\"x\"}");
    }

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    @Test
    @DisplayName("MVC writes and reads JSON with Boot's JsonMapper, not a Jackson 2 bridge")
    void mvcUsesBootsMapper() {
        var converter = ConverterProbe.jsonConverter(adapter);
        assertThat(converter).isInstanceOf(JacksonJsonHttpMessageConverter.class);
        assertThat(((JacksonJsonHttpMessageConverter) converter).getMapper()).isSameAs(mapper);
    }

    @Test
    @DisplayName("Jackson 2 mapper settings with Boot's date and leniency defaults")
    void mapperFeatures() {
        assertThat(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)).isFalse();
        assertThat(mapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)).isFalse();
        assertThat(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        assertThat(mapper.isEnabled(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isTrue();
    }

    @Test
    @DisplayName("Jackson 2.18 stream limits (nesting 1000, strings 20M), as hitorro-core's JsonMappers (copied in Json)")
    void streamLimits() {
        StreamReadConstraints r = mapper.tokenStreamFactory().streamReadConstraints();
        StreamWriteConstraints w = mapper.tokenStreamFactory().streamWriteConstraints();
        StreamReadConstraints wantR = Json.JACKSON2_READ_CONSTRAINTS;
        assertThat(r.getMaxNestingDepth()).isEqualTo(wantR.getMaxNestingDepth()).isEqualTo(1000);
        assertThat(r.getMaxStringLength()).isEqualTo(wantR.getMaxStringLength()).isEqualTo(20_000_000);
        assertThat(r.getMaxNumberLength()).isEqualTo(wantR.getMaxNumberLength());
        assertThat(r.getMaxNameLength()).isEqualTo(wantR.getMaxNameLength());
        assertThat(r.getMaxDocumentLength()).isEqualTo(wantR.getMaxDocumentLength());
        assertThat(r.getMaxTokenCount()).isEqualTo(wantR.getMaxTokenCount());
        assertThat(w.getMaxNestingDepth()).isEqualTo(Json.JACKSON2_WRITE_CONSTRAINTS.getMaxNestingDepth()).isEqualTo(1000);
    }
}
