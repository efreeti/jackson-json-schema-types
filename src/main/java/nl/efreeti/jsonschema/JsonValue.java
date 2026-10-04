package nl.efreeti.jsonschema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Arbitrary JSON data, including explicit JSON null, without untyped payloads.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(JsonValue.NullValue.class),
        @JsonSubTypes.Type(JsonValue.BooleanValue.class),
        @JsonSubTypes.Type(JsonValue.NumberValue.class),
        @JsonSubTypes.Type(JsonValue.StringValue.class),
        @JsonSubTypes.Type(JsonValue.ArrayValue.class),
        @JsonSubTypes.Type(JsonValue.ObjectValue.class)
})
public sealed interface JsonValue {
    /**
     * Explicit JSON null, distinct from an absent schema keyword.
     */
    @JsonTypeName("NullValue")
    record NullValue() implements JsonValue {
    }

    /**
     * A JSON boolean.
     */
    @JsonTypeName("BooleanValue")
    record BooleanValue(boolean value) implements JsonValue {
    }

    /**
     * A JSON number represented without floating point precision loss.
     */
    @JsonTypeName("NumberValue")
    record NumberValue(BigDecimal value) implements JsonValue {
    }

    /**
     * A JSON string.
     */
    @JsonTypeName("StringValue")
    record StringValue(String value) implements JsonValue {
    }

    /**
     * An ordered collection of JSON values.
     */
    @JsonTypeName("ArrayValue")
    record ArrayValue(List<JsonValue> values) implements JsonValue {
        public ArrayValue {
            values = List.copyOf(values);
        }
    }

    /**
     * A JSON object with string keys and typed JSON values.
     */
    @JsonTypeName("ObjectValue")
    record ObjectValue(Map<String, JsonValue> values) implements JsonValue {
        public ObjectValue {
            values = Map.copyOf(values);
        }
    }
}
