package com.hitorro.searchui.json;

// Copied from hitorro-core 4.0.0 (commit d27ea01) with its recorded Jackson 2.18 results.

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link JsonValues} to Jackson 2 behaviour.
 *
 * <p>{@code jackson2-accessors.txt} was produced by running exactly this matrix (same nodes,
 * same order) against Jackson 2.18.2's own {@code JsonNode} methods
 * ({@code node.textValue()}, {@code node.asLong(7)}, ...). Lines whose value is an identity
 * hash ({@code byte[]#toString}) were dropped. Every line must come out the same here.
 */
class JsonValuesTest {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    static List<JsonNode> nodes() {
        List<JsonNode> ns = new ArrayList<>();
        for (String x : new String[]{"123", " 12 ", "3.7", "-3.7", "+5", "-", "+", "1e3", "0x10", "", "abc", "true",
                " false ", "True", "9999999999999999999", "-0", "NaN", "Infinity", "1d", "2147483648", "AQID", "1,5"}) {
            ns.add(F.stringNode(x));
        }
        ns.add(F.numberNode(3.7));
        ns.add(F.numberNode(-3.7));
        ns.add(F.numberNode(0.0));
        ns.add(F.numberNode(Double.NaN));
        ns.add(F.numberNode(1e300));
        ns.add(F.numberNode(3.5f));
        ns.add(F.numberNode(0.1f));
        ns.add(F.numberNode(5));
        ns.add(F.numberNode(0));
        ns.add(F.numberNode(5L));
        ns.add(F.numberNode(Long.MAX_VALUE));
        ns.add(F.numberNode((short) 7));
        ns.add(F.numberNode(new BigInteger("123456789012345678901234567890")));
        ns.add(F.numberNode(BigInteger.ZERO));
        ns.add(F.numberNode(new BigDecimal("1e30")));
        ns.add(F.numberNode(new BigDecimal("1.10")));
        ns.add(F.numberNode(new BigDecimal("0.5")));
        ns.add(F.booleanNode(true));
        ns.add(F.booleanNode(false));
        ns.add(F.nullNode());
        ns.add(MissingNode.getInstance());
        ns.add(F.objectNode().put("a", 1));
        ns.add(F.arrayNode().add(1));
        ns.add(F.binaryNode(new byte[]{1, 2, 3}));
        ns.add(F.pojoNode(42));
        ns.add(F.pojoNode(Boolean.TRUE));
        ns.add(F.pojoNode("str"));
        ns.add(F.pojoNode(new byte[]{9}));
        ns.add(F.pojoNode(null));
        ns.add(F.pojoNode(2.5));
        return ns;
    }

    private static String show(Object v) {
        if (v instanceof byte[] b) {
            return "bytes" + Arrays.toString(b);
        }
        return v == null ? "<null>" : v.getClass().getSimpleName() + ":" + v;
    }

    private static void t(List<String> out, String label, Callable<Object> c) {
        String r;
        try {
            r = show(c.call());
        } catch (Throwable e) {
            r = "!" + (e instanceof NullPointerException ? "NPE" : "EX");
        }
        out.add(label + " = " + r);
    }

    static List<String> matrix() {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (JsonNode n : nodes()) {
            String k = "#" + (i++) + "(" + n + ")";
            t(out, k + ".textValue", () -> JsonValues.textValue(n));
            t(out, k + ".asText", () -> JsonValues.asText(n));
            t(out, k + ".asText(d)", () -> JsonValues.asText(n, "D"));
            t(out, k + ".booleanValue", () -> JsonValues.booleanValue(n));
            t(out, k + ".numberValue", () -> JsonValues.numberValue(n));
            t(out, k + ".intValue", () -> JsonValues.intValue(n));
            t(out, k + ".longValue", () -> JsonValues.longValue(n));
            t(out, k + ".shortValue", () -> JsonValues.shortValue(n));
            t(out, k + ".floatValue", () -> JsonValues.floatValue(n));
            t(out, k + ".doubleValue", () -> JsonValues.doubleValue(n));
            t(out, k + ".decimalValue", () -> JsonValues.decimalValue(n));
            t(out, k + ".bigIntegerValue", () -> JsonValues.bigIntegerValue(n));
            t(out, k + ".binaryValue", () -> JsonValues.binaryValue(n));
            t(out, k + ".asInt", () -> JsonValues.asInt(n));
            t(out, k + ".asLong", () -> JsonValues.asLong(n));
            t(out, k + ".asDouble", () -> JsonValues.asDouble(n));
            t(out, k + ".asBoolean", () -> JsonValues.asBoolean(n));
            t(out, k + ".asInt(7)", () -> JsonValues.asInt(n, 7));
            t(out, k + ".asLong(7)", () -> JsonValues.asLong(n, 7L));
            t(out, k + ".asDouble(7)", () -> JsonValues.asDouble(n, 7.0));
            t(out, k + ".asBoolean(T)", () -> JsonValues.asBoolean(n, true));
        }
        return out;
    }

    @Test
    @DisplayName("every accessor returns what Jackson 2.18 returned, for every kind of node")
    void matchesJackson2() throws Exception {
        List<String> expected = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                JsonValuesTest.class.getResourceAsStream("jackson2-accessors.txt"), StandardCharsets.UTF_8))) {
            r.lines().forEach(expected::add);
        }
        List<String> actual = matrix().stream().filter(l -> !l.contains("[B@")).toList();
        assertThat(expected).hasSize(1090);
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("the Jackson 3 methods throw where JsonValues does not (why the helper exists)")
    void jackson3Throws() {
        JsonNode number = F.numberNode(5);
        JsonNode text = F.stringNode("abc");
        assertThatThrownBy(number::textValue).isInstanceOf(tools.jackson.databind.exc.JsonNodeException.class);
        assertThatThrownBy(text::longValue).isInstanceOf(tools.jackson.databind.exc.JsonNodeException.class);
        assertThat(F.nullNode().asText()).isEmpty();
        assertThat(JsonValues.textValue(number)).isNull();
        assertThat(JsonValues.longValue(text)).isZero();
        assertThat(JsonValues.asText(F.nullNode())).isEqualTo("null");
    }

    @Test
    @DisplayName("a null node reference throws NullPointerException, as calling the method on it did")
    void nullReference() {
        assertThatThrownBy(() -> JsonValues.textValue(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> JsonValues.asText(null)).isInstanceOf(NullPointerException.class);
    }
}
