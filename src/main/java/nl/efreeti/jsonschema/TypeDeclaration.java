package nl.efreeti.jsonschema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/**
 * Preserves the distinction between a scalar type and an array of types.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(TypeDeclaration.Single.class),
        @JsonSubTypes.Type(TypeDeclaration.Multiple.class)
})
public sealed interface TypeDeclaration {
    /**
     * A scalar type declaration, such as "string".
     */
    @JsonTypeName("Single")
    record Single(SchemaPrimitive value) implements TypeDeclaration {
    }

    /**
     * An array type declaration, such as ["string", "null"].
     */
    @JsonTypeName("Multiple")
    record Multiple(List<SchemaPrimitive> values) implements TypeDeclaration {
        public Multiple {
            values = List.copyOf(values);
        }
    }
}
