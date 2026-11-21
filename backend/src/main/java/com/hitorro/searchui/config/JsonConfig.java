/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.config;

import com.hitorro.searchui.json.Jackson2JdkModule;
import com.hitorro.searchui.json.Json;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.boot.actuate.endpoint.jackson.EndpointJsonMapper;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the JSON format the BFF had on Jackson 2, for Spring MVC responses and for the WebClient's
 * bodies to and from fleet-retrieval (both use Boot's auto-configured {@code JsonMapper}).
 *
 * <p>{@code spring.jackson.use-jackson2-defaults=true} (application.yml) gives that mapper the settings
 * Boot's Jackson 2 mapper had: Jackson 2 defaults ({@code configureForJackson2()}: declaration-order
 * properties, lenient reads) plus Boot's ISO-8601 dates, ignored unknown properties and no default view
 * inclusion. Neither sets the <em>factory</em>: Jackson 3 halves the nesting limit (500 instead of 1000)
 * and changes the string limit, so this customizer applies Jackson 2.18's limits and factory defaults
 * (see {@link Json}). It runs after Boot's own factory customizer. {@link Jackson2JdkModule} restores
 * Jackson 2.18's handling of Locale and Month.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    JsonFactoryBuilderCustomizer jackson2StreamLimits() {
        return builder -> builder.configureForJackson2()
                .streamReadConstraints(Json.JACKSON2_READ_CONSTRAINTS)
                .streamWriteConstraints(Json.JACKSON2_WRITE_CONSTRAINTS);
    }

    @Bean
    JsonMapperBuilderCustomizer jackson2JdkTypes() {
        return builder -> builder.addModule(new Jackson2JdkModule());
    }

    /**
     * Actuator JSON. Boot 4's isolated endpoint mapper is a plain Jackson 3 mapper (alphabetical properties);
     * with {@code management.endpoints.jackson.isolated-json-mapper=false} this bean takes its place. It is
     * what Boot's former isolated Jackson 2 endpoint mapper was: Jackson 2 defaults and limits, ISO dates
     * and durations, lenient reads, no default view inclusion, nulls left out. It is {@code @Primary} so it also
     * wins where Boot's isolated mapper gets created anyway (Boot's has no {@code @ConditionalOnMissingBean}).
     *
     * <p>A health component ({@code IndicatedHealthDescriptor}) has only {@code getStatus()} and
     * {@code getDetails()}, no fields, so Jackson 2 and 3 order the two by {@code Class.getDeclaredMethods()},
     * which differs between JVM runs. The mix-in pins {@code status, details}, the usual order.
     */
    @Bean
    @Primary
    EndpointJsonMapper hitorroEndpointJsonMapper() {
        JsonMapper.Builder builder = Json.MAPPER.rebuild();
        try {
            builder.addMixIn(Class.forName(INDICATED_HEALTH_DESCRIPTOR, false, JsonConfig.class.getClassLoader()),
                    StatusFirst.class);
        } catch (ClassNotFoundException e) {
            // no health endpoint on the classpath
        }
        JsonMapper mapper = builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .changeDefaultPropertyInclusion(i -> i.withValueInclusion(JsonInclude.Include.NON_NULL)
                        .withContentInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return () -> mapper;
    }

    static final String INDICATED_HEALTH_DESCRIPTOR = "org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor";

    @JsonPropertyOrder({"status", "details"})
    interface StatusFirst {
    }
}
