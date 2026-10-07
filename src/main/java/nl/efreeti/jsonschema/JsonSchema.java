package nl.efreeti.jsonschema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** A draft 2020-12 schema is either a boolean or an object of schema keywords. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(JsonSchema.BooleanSchema.class),
        @JsonSubTypes.Type(JsonSchema.ObjectSchema.class)
})
public sealed interface JsonSchema {
    /** Accepts every value when true and rejects every value when false. */
    @JsonTypeName("BooleanSchema")
    record BooleanSchema(boolean value) implements JsonSchema {
    }

    /** A schema object whose keywords may be combined without an explicit primitive type. */
    @JsonTypeName("ObjectSchema")
    record ObjectSchema(SchemaKeywords keywords) implements JsonSchema {
    }
}
