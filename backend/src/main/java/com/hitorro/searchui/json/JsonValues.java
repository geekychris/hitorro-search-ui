/*
 * Copyright (c) 2006-2026 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.searchui.json;

// Copied unchanged from hitorro-core 4.0.0 (com.hitorro.util.json.JsonValues, commit d27ea01) so this BFF
// keeps Jackson 2's behaviour without depending on hitorro-core. Keep in sync with the original.

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.POJONode;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * {@link JsonNode} value accessors with Jackson 2 semantics.
 *
 * <p>In Jackson 2 the value accessors were lenient: {@code textValue()} on a number returned
 * {@code null}, {@code longValue()} on a string, null or missing node returned 0,
 * {@code asText()} on a null node returned {@code "null"} and on an object {@code ""}. In
 * Jackson 3 the same calls throw {@code JsonNodeException}. HiTorro code reads optional and
 * loosely typed JSON everywhere and relies on the old behaviour, so the Jackson 3 migration
 * rewrites {@code node.textValue()} to {@code JsonValues.textValue(node)} (and so on for every
 * method here) wherever the node's type is not certain. Each method returns exactly what the
 * Jackson 2.18 method of the same name returned for the same node, including the
 * truncation and overflow of numeric conversions.
 *
 * <p>Like the Jackson 2 methods, these throw {@link NullPointerException} for a {@code null} node.
 */
public final class JsonValues {

    private JsonValues() {
    }

    // ---------------------------------------------------------------- xxxValue(): no coercion

    /** The string of a string node, otherwise {@code null}. */
    public static String textValue(JsonNode n) {
        return n.isString() ? n.stringValue() : null;
    }

    /** The value of a boolean node, otherwise {@code false}. */
    public static boolean booleanValue(JsonNode n) {
        return n.isBoolean() && n.booleanValue();
    }

    /** The number of a numeric node, otherwise {@code null}. */
    public static Number numberValue(JsonNode n) {
        return n.isNumber() ? n.numberValue() : null;
    }

    /** A numeric node's value as an int (Java narrowing: truncates and wraps), otherwise 0. */
    public static int intValue(JsonNode n) {
        return n.isNumber() ? n.numberValue().intValue() : 0;
    }

    /** A numeric node's value as a long (Java narrowing: truncates and wraps), otherwise 0. */
    public static long longValue(JsonNode n) {
        return n.isNumber() ? n.numberValue().longValue() : 0L;
    }

    /** A numeric node's value as a short (Java narrowing), otherwise 0. */
    public static short shortValue(JsonNode n) {
        return n.isNumber() ? n.numberValue().shortValue() : 0;
    }

    /** A numeric node's value as a float, otherwise 0. */
    public static float floatValue(JsonNode n) {
        return n.isNumber() ? n.numberValue().floatValue() : 0.0f;
    }

    /** A numeric node's value as a double, otherwise 0. */
    public static double doubleValue(JsonNode n) {
        return n.isNumber() ? n.numberValue().doubleValue() : 0.0;
    }

    /** A numeric node's value as a BigDecimal, otherwise {@link BigDecimal#ZERO}. */
    public static BigDecimal decimalValue(JsonNode n) {
        if (!n.isNumber()) {
            return BigDecimal.ZERO;
        }
        Number v = n.numberValue();
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (v instanceof Double || v instanceof Float) {
            return BigDecimal.valueOf(v.doubleValue());
        }
        return BigDecimal.valueOf(v.longValue());
    }

    /** A numeric node's value as a BigInteger (fractions truncated), otherwise {@link BigInteger#ZERO}. */
    public static BigInteger bigIntegerValue(JsonNode n) {
        if (!n.isNumber()) {
            return BigInteger.ZERO;
        }
        Number v = n.numberValue();
        if (v instanceof BigInteger bi) {
            return bi;
        }
        if (n.isIntegralNumber()) {
            return BigInteger.valueOf(v.longValue());
        }
        return decimalValue(n).toBigInteger();
    }

    /**
     * The bytes of a binary node, the Base64-decoded bytes of a string node (throws if it is
     * not valid Base64), the bytes held by a POJO node holding a {@code byte[]}, otherwise
     * {@code null}.
     */
    public static byte[] binaryValue(JsonNode n) {
        if (n.isBinary() || n.isString()) {
            return n.binaryValue();
        }
        if (n.isPojo() && ((POJONode) n).getPojo() instanceof byte[] bytes) {
            return bytes;
        }
        return null;
    }

    // ---------------------------------------------------------------- asXxx(): coercion

    /**
     * The node's value as text: a string node's string, a number's or boolean's JSON text,
     * Base64 for binary, {@code "null"} for a null node, {@code ""} for objects, arrays and
     * missing nodes.
     */
    public static String asText(JsonNode n) {
        switch (n.getNodeType()) {
            case OBJECT:
            case ARRAY:
            case MISSING:
                return "";
            case NULL:
                return "null";
            case POJO: {
                Object v = ((POJONode) n).getPojo();
                return v == null ? "null" : v.toString();
            }
            default:
                return n.asString();
        }
    }

    /**
     * Like {@link #asText(JsonNode)}, but {@code defaultValue} for null and missing nodes
     * (and a POJO node holding null). Objects and arrays still give {@code ""}.
     */
    public static String asText(JsonNode n, String defaultValue) {
        switch (n.getNodeType()) {
            case NULL:
            case MISSING:
                return defaultValue;
            case POJO: {
                Object v = ((POJONode) n).getPojo();
                return v == null ? defaultValue : v.toString();
            }
            default:
                return asText(n);
        }
    }

    public static int asInt(JsonNode n) {
        return asInt(n, 0);
    }

    /**
     * Numbers narrowed (Java rules), booleans 1/0, strings parsed (trimmed; non-integer
     * syntax parsed as a double and truncated; {@code defaultValue} if unparseable), POJO
     * numbers; {@code defaultValue} otherwise.
     */
    public static int asInt(JsonNode n, int defaultValue) {
        switch (n.getNodeType()) {
            case NUMBER:
                return n.numberValue().intValue();
            case BOOLEAN:
                return n.booleanValue() ? 1 : 0;
            case STRING:
                return parseAsInt(n.stringValue(), defaultValue);
            case POJO:
                return ((POJONode) n).getPojo() instanceof Number num ? num.intValue() : defaultValue;
            default:
                return defaultValue;
        }
    }

    public static long asLong(JsonNode n) {
        return asLong(n, 0L);
    }

    /** As {@link #asInt(JsonNode, int)}, for long. */
    public static long asLong(JsonNode n, long defaultValue) {
        switch (n.getNodeType()) {
            case NUMBER:
                return n.numberValue().longValue();
            case BOOLEAN:
                return n.booleanValue() ? 1L : 0L;
            case STRING:
                return parseAsLong(n.stringValue(), defaultValue);
            case POJO:
                return ((POJONode) n).getPojo() instanceof Number num ? num.longValue() : defaultValue;
            default:
                return defaultValue;
        }
    }

    public static double asDouble(JsonNode n) {
        return asDouble(n, 0.0);
    }

    /** As {@link #asInt(JsonNode, int)}, for double. */
    public static double asDouble(JsonNode n, double defaultValue) {
        switch (n.getNodeType()) {
            case NUMBER:
                return n.numberValue().doubleValue();
            case BOOLEAN:
                return n.booleanValue() ? 1.0 : 0.0;
            case STRING:
                return parseAsDouble(n.stringValue(), defaultValue);
            case POJO:
                return ((POJONode) n).getPojo() instanceof Number num ? num.doubleValue() : defaultValue;
            default:
                return defaultValue;
        }
    }

    public static boolean asBoolean(JsonNode n) {
        return asBoolean(n, false);
    }

    /**
     * Booleans as is; integer numbers {@code != 0} (floating point numbers give
     * {@code defaultValue}); strings {@code "true"}/{@code "false"} after trimming
     * (case sensitive); POJO booleans; {@code defaultValue} otherwise.
     */
    public static boolean asBoolean(JsonNode n, boolean defaultValue) {
        switch (n.getNodeType()) {
            case BOOLEAN:
                return n.booleanValue();
            case NUMBER:
                return n.isIntegralNumber() ? n.bigIntegerValue().signum() != 0 : defaultValue;
            case STRING: {
                String v = n.stringValue().trim();
                if ("true".equals(v)) {
                    return true;
                }
                if ("false".equals(v)) {
                    return false;
                }
                return defaultValue;
            }
            case POJO:
                return ((POJONode) n).getPojo() instanceof Boolean b ? b : defaultValue;
            default:
                return defaultValue;
        }
    }

    // ---------------------------------------------------------------- Jackson 2 NumberInput.parseAsXxx

    static int parseAsInt(String s, int def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return def;
        }
        int i = 0;
        char sign = s.charAt(0);
        if (sign == '+') {
            s = s.substring(1);
        } else if (sign == '-') {
            i = 1;
        }
        for (int len = s.length(); i < len; ++i) {
            char c = s.charAt(i);
            if (c > '9' || c < '0') {
                try {
                    return (int) Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static long parseAsLong(String s, long def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return def;
        }
        int i = 0;
        char sign = s.charAt(0);
        if (sign == '+') {
            s = s.substring(1);
        } else if (sign == '-') {
            i = 1;
        }
        for (int len = s.length(); i < len; ++i) {
            char c = s.charAt(i);
            if (c > '9' || c < '0') {
                try {
                    return (long) Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static double parseAsDouble(String s, double def) {
        if (s == null) {
            return def;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
