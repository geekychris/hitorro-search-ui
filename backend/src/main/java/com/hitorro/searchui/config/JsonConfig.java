/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.config;

import com.hitorro.searchui.json.Jackson2JdkModule;
import com.hitorro.searchui.json.Json;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

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
}
