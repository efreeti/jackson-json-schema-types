package nl.efreeti.jsonschema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Primitive names defined by JSON Schema.
 */
public enum SchemaPrimitive {
    @JsonProperty("null")
    NULL,
    @JsonProperty("boolean")
    BOOLEAN,
    @JsonProperty("object")
    OBJECT,
    @JsonProperty("array")
    ARRAY,
    @JsonProperty("number")
    NUMBER,
    @JsonProperty("integer")
    INTEGER,
    @JsonProperty("string")
    STRING
}
