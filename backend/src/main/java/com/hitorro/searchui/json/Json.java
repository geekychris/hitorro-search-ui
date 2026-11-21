/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.searchui.json;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 2-compatible JSON for this BFF's own code (building request bodies and error bodies,
 * writing export rows, reading type sidecars), the same settings as hitorro-core 4.0.0's
 * {@code JsonMappers}: Jackson 2 mapper and factory defaults, Jackson 2.18's stream limits and
 * {@link Jackson2JdkModule}. The BFF has no hitorro-core dependency, so the settings live here.
 * Spring MVC and the WebClient use Boot's mapper (see {@code config.JsonConfig}).
 */
public final class Json {

    /** Jackson 2.18's read limits (Jackson 3 halved the nesting depth and raised the string length). */
    public static final StreamReadConstraints JACKSON2_READ_CONSTRAINTS = StreamReadConstraints.builder()
            .maxNestingDepth(1000)
            .maxStringLength(20_000_000)
            .maxNumberLength(1000)
            .maxNameLength(50_000)
            .maxDocumentLength(-1L)
            .maxTokenCount(-1L)
            .build();

    /** Jackson 2.18's write limits. */
    public static final StreamWriteConstraints JACKSON2_WRITE_CONSTRAINTS = StreamWriteConstraints.builder()
            .maxNestingDepth(1000)
            .build();

    /** Shared, immutable, thread safe. */
    public static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builderWithJackson2Defaults()
                    .streamReadConstraints(JACKSON2_READ_CONSTRAINTS)
                    .streamWriteConstraints(JACKSON2_WRITE_CONSTRAINTS)
                    .build())
            .configureForJackson2()
            .addModule(new Jackson2JdkModule())
            .build();

    private Json() {}
}
