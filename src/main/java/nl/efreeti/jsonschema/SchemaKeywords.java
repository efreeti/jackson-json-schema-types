package nl.efreeti.jsonschema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All keywords from ya-json-schema-types, with recursive schema and JSON value types.
 *
 * <p>Keywords can coexist without a type declaration, as permitted by composed schemas. This models
 * schema documents; it does not validate their semantic constraints. Format is open-ended because
 * custom vocabularies may define additional formats. Unknown keywords retain their typed JSON
 * values in extensions.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record SchemaKeywords(
        @JsonProperty("$id") Optional<String> id,
        @JsonProperty("$comment") Optional<String> comment,
        @JsonProperty("$schema") Optional<String> schema,
        @JsonProperty("$anchor") Optional<String> anchor,
        @JsonProperty("$dynamicAnchor") Optional<String> dynamicAnchor,
        Optional<String> title,
        Optional<String> description,
        @JsonProperty("$ref") Optional<String> ref,
        @JsonProperty("$dynamicRef") Optional<String> dynamicRef,
        @JsonProperty("$vocabulary") Optional<Map<String, Boolean>> vocabulary,
        @JsonProperty("$defs") Optional<Map<String, JsonSchema>> defs,
        Optional<Map<String, JsonSchema>> properties,
        Optional<Map<String, JsonSchema>> patternProperties,
        Optional<Map<String, JsonSchema>> dependentSchemas,
        Optional<Boolean> deprecated,
        Optional<Boolean> readOnly,
        Optional<Boolean> writeOnly,
        Optional<Boolean> uniqueItems,
        Optional<TypeDeclaration> type,
        @JsonProperty("enum") Optional<List<JsonValue>> enumValues,
        Optional<List<JsonValue>> examples,
        @JsonProperty("const") Optional<JsonValue> constValue,
        @JsonProperty("default") Optional<JsonValue> defaultValue,
        Optional<BigDecimal> multipleOf,
        Optional<BigDecimal> maximum,
        Optional<BigDecimal> exclusiveMaximum,
        Optional<BigDecimal> minimum,
        Optional<BigDecimal> exclusiveMinimum,
        Optional<String> format,
        Optional<String> pattern,
        Optional<String> contentEncoding,
        Optional<String> contentMediaType,
        Optional<BigInteger> maxLength,
        Optional<BigInteger> minLength,
        Optional<BigInteger> maxItems,
        Optional<BigInteger> minItems,
        Optional<BigInteger> maxContains,
        Optional<BigInteger> minContains,
        Optional<BigInteger> maxProperties,
        Optional<BigInteger> minProperties,
        Optional<JsonSchema> contentSchema,
        Optional<JsonSchema> items,
        Optional<JsonSchema> contains,
        Optional<JsonSchema> unevaluatedItems,
        Optional<JsonSchema> additionalProperties,
        Optional<JsonSchema> propertyNames,
        Optional<JsonSchema> unevaluatedProperties,
        Optional<JsonSchema> not,
        @JsonProperty("if") Optional<JsonSchema> ifSchema,
        @JsonProperty("then") Optional<JsonSchema> thenSchema,
        @JsonProperty("else") Optional<JsonSchema> elseSchema,
        Optional<List<JsonSchema>> prefixItems,
        Optional<List<JsonSchema>> anyOf,
        Optional<List<JsonSchema>> allOf,
        Optional<List<JsonSchema>> oneOf,
        Optional<List<String>> required,
        Optional<Map<String, List<String>>> dependentRequired,
        @JsonAnyGetter Map<String, JsonValue> extensions) {
    /**
     * Creates a keyword object with immutable collection contents.
     */
    public SchemaKeywords {
        enumValues = enumValues.map(List::copyOf);
        examples = examples.map(List::copyOf);
        prefixItems = prefixItems.map(List::copyOf);
        anyOf = anyOf.map(List::copyOf);
        allOf = allOf.map(List::copyOf);
        oneOf = oneOf.map(List::copyOf);
        required = required.map(List::copyOf);
        vocabulary = vocabulary.map(Map::copyOf);
        defs = defs.map(Map::copyOf);
        properties = properties.map(Map::copyOf);
        patternProperties = patternProperties.map(Map::copyOf);
        dependentSchemas = dependentSchemas.map(Map::copyOf);
        dependentRequired = dependentRequired.map(requirements -> requirements.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
        ));
        extensions = Map.copyOf(extensions);
    }
}
