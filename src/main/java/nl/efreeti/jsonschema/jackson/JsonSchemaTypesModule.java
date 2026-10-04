package nl.efreeti.jsonschema.jackson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import nl.efreeti.jsonschema.JsonSchema;
import nl.efreeti.jsonschema.JsonValue;
import nl.efreeti.jsonschema.SchemaKeywords;
import nl.efreeti.jsonschema.SchemaPrimitive;
import nl.efreeti.jsonschema.TypeDeclaration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Maps the project's tagged Java unions to native JSON Schema wire shapes.
 */
public final class JsonSchemaTypesModule extends SimpleModule {
    /**
     * Registers native schema, type declaration, and JSON value codecs.
     */
    public JsonSchemaTypesModule() {
        super("JsonSchemaTypesModule");
        addSerializer(JsonSchema.class, new SchemaSerializer());
        addDeserializer(JsonSchema.class, new SchemaDeserializer());
        addSerializer(JsonValue.class, new JsonValueSerializer());
        addDeserializer(JsonValue.class, new JsonValueDeserializer());
        addSerializer(TypeDeclaration.class, new TypeSerializerCodec());
        addDeserializer(TypeDeclaration.class, new TypeDeserializerCodec());
        addDeserializer(SchemaKeywords.class, new KeywordsDeserializer());
    }

    // These hooks own the external shape even when @JsonTypeInfo is present. No mix-ins
    // are needed: Jackson delegates polymorphic reads and writes to these methods.
    private abstract static class NativeSerializer<T> extends ValueSerializer<T> {
        @Override
        public final void serializeWithType(T value, JsonGenerator generator, SerializationContext context, TypeSerializer typeSerializer) {
            serialize(value, generator, context);
        }
    }

    private abstract static class NativeDeserializer<T> extends ValueDeserializer<T> {
        @Override
        public final T deserializeWithType(JsonParser parser, DeserializationContext context, TypeDeserializer typeDeserializer) {
            return deserialize(parser, context);
        }
    }

    private static final class SchemaSerializer extends NativeSerializer<JsonSchema> {
        @Override
        public void serialize(JsonSchema value, JsonGenerator generator, SerializationContext context) {
            switch (value) {
                case JsonSchema.BooleanSchema schema -> generator.writeBoolean(schema.value());
                case JsonSchema.ObjectSchema schema -> context.writeValue(generator, schema.keywords());
            }
        }
    }

    private static final class SchemaDeserializer extends NativeDeserializer<JsonSchema> {
        @Override
        public JsonSchema deserialize(JsonParser parser, DeserializationContext context) {
            return switch (parser.currentToken()) {
                case VALUE_TRUE, VALUE_FALSE -> new JsonSchema.BooleanSchema(parser.getBooleanValue());
                case START_OBJECT -> new JsonSchema.ObjectSchema(context.readValue(parser, SchemaKeywords.class));
                default -> context.reportInputMismatch(JsonSchema.class, "Expected a schema object or boolean");
            };
        }

        @Override
        public JsonSchema getNullValue(DeserializationContext context) {
            return context.reportInputMismatch(JsonSchema.class, "JSON null is not a schema");
        }
    }

    private static final class TypeSerializerCodec extends NativeSerializer<TypeDeclaration> {
        @Override
        public void serialize(TypeDeclaration value, JsonGenerator generator, SerializationContext context) {
            switch (value) {
                case TypeDeclaration.Single single -> context.writeValue(generator, single.value());
                case TypeDeclaration.Multiple multiple -> context.writeValue(generator, multiple.values());
            }
        }
    }

    private static final class TypeDeserializerCodec extends NativeDeserializer<TypeDeclaration> {
        @Override
        public TypeDeclaration deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                return new TypeDeclaration.Single(context.readValue(parser, SchemaPrimitive.class));
            } else if (parser.currentToken() == JsonToken.START_ARRAY) {
                var values = new ArrayList<SchemaPrimitive>();

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    requireToken(parser, context, JsonToken.VALUE_STRING);

                    values.add(context.readValue(parser, SchemaPrimitive.class));
                }

                return new TypeDeclaration.Multiple(values);
            } else {
                return context.reportInputMismatch(TypeDeclaration.class, "Expected a type string or array");
            }
        }

        @Override
        public TypeDeclaration getNullValue(DeserializationContext context) {
            return context.reportInputMismatch(TypeDeclaration.class, "JSON null is not a type declaration");
        }
    }

    private static final class JsonValueSerializer extends NativeSerializer<JsonValue> {
        @Override
        public void serialize(JsonValue value, JsonGenerator generator, SerializationContext context) {
            switch (value) {
                case JsonValue.NullValue ignored -> generator.writeNull();
                case JsonValue.BooleanValue bool -> generator.writeBoolean(bool.value());
                case JsonValue.NumberValue number -> generator.writeNumber(number.value());
                case JsonValue.StringValue string -> generator.writeString(string.value());
                case JsonValue.ArrayValue array -> {
                    generator.writeStartArray();

                    for (JsonValue element : array.values()) {
                        serialize(element, generator, context);
                    }

                    generator.writeEndArray();
                }
                case JsonValue.ObjectValue object -> {
                    generator.writeStartObject();

                    for (var entry : object.values().entrySet()) {
                        generator.writeName(entry.getKey());
                        serialize(entry.getValue(), generator, context);
                    }

                    generator.writeEndObject();
                }
            }
        }
    }

    private static final class JsonValueDeserializer extends NativeDeserializer<JsonValue> {
        @Override
        public JsonValue deserialize(JsonParser parser, DeserializationContext context) {
            return switch (parser.currentToken()) {
                case VALUE_NULL -> new JsonValue.NullValue();
                case VALUE_TRUE, VALUE_FALSE -> new JsonValue.BooleanValue(parser.getBooleanValue());
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new JsonValue.NumberValue(parser.getDecimalValue());
                case VALUE_STRING -> new JsonValue.StringValue(parser.getString());
                case START_ARRAY -> {
                    var values = new ArrayList<JsonValue>();

                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        values.add(deserialize(parser, context));
                    }

                    yield new JsonValue.ArrayValue(values);
                }
                case START_OBJECT -> {
                    var values = new LinkedHashMap<String, JsonValue>();

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String name = parser.currentName();
                        parser.nextToken();
                        values.put(name, deserialize(parser, context));
                    }

                    yield new JsonValue.ObjectValue(values);
                }
                default -> context.reportInputMismatch(JsonValue.class, "Expected a JSON value");
            };
        }

        @Override
        public JsonValue getNullValue(DeserializationContext context) {
            return new JsonValue.NullValue();
        }
    }

    private static void requireToken(JsonParser parser, DeserializationContext context, JsonToken token) {
        if (parser.currentToken() != token) {
            context.reportInputMismatch(
                    SchemaKeywords.class, "Expected %s for keyword %s, got %s", token, parser.currentName(), parser.currentToken()
            );
        }
    }

    private static List<String> readStrings(JsonParser parser, DeserializationContext context) {
        requireToken(parser, context, JsonToken.START_ARRAY);

        var values = new ArrayList<String>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            requireToken(parser, context, JsonToken.VALUE_STRING);

            values.add(parser.getString());
        }

        return values;
    }

    private static Map<String, List<String>> readDependentRequired(JsonParser parser, DeserializationContext context) {
        var values = new LinkedHashMap<String, List<String>>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var name = parser.currentName();
            parser.nextToken();
            values.put(name, readStrings(parser, context));
        }

        return values;
    }

    private static Map<String, Boolean> readVocabulary(JsonParser parser, DeserializationContext context) {
        var values = new LinkedHashMap<String, Boolean>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var name = parser.currentName();
            parser.nextToken();
            if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                context.reportInputMismatch(SchemaKeywords.class, "Expected a vocabulary boolean for %s", name);
            }
            values.put(name, parser.getBooleanValue());
        }

        return values;
    }

    // Reading the keyword object explicitly preserves const/default: null as a present
    // NullValue. Jackson's ordinary Optional binding treats JSON null as Optional.empty().
    private static final class KeywordsDeserializer extends ValueDeserializer<SchemaKeywords> {
        @Override
        public SchemaKeywords deserialize(JsonParser parser, DeserializationContext context) {
            requireToken(parser, context, JsonToken.START_OBJECT);
            var id = Optional.<String>empty();
            var comment = Optional.<String>empty();
            var schema = Optional.<String>empty();
            var anchor = Optional.<String>empty();
            var dynamicAnchor = Optional.<String>empty();
            var title = Optional.<String>empty();
            var description = Optional.<String>empty();
            var ref = Optional.<String>empty();
            var dynamicRef = Optional.<String>empty();
            var vocabulary = Optional.<Map<String, Boolean>>empty();
            var defs = Optional.<Map<String, JsonSchema>>empty();
            var properties = Optional.<Map<String, JsonSchema>>empty();
            var patternProperties = Optional.<Map<String, JsonSchema>>empty();
            var dependentSchemas = Optional.<Map<String, JsonSchema>>empty();
            var deprecated = Optional.<Boolean>empty();
            var readOnly = Optional.<Boolean>empty();
            var writeOnly = Optional.<Boolean>empty();
            var uniqueItems = Optional.<Boolean>empty();
            var type = Optional.<TypeDeclaration>empty();
            var enumValues = Optional.<List<JsonValue>>empty();
            var examples = Optional.<List<JsonValue>>empty();
            var constValue = Optional.<JsonValue>empty();
            var defaultValue = Optional.<JsonValue>empty();
            var multipleOf = Optional.<BigDecimal>empty();
            var maximum = Optional.<BigDecimal>empty();
            var exclusiveMaximum = Optional.<BigDecimal>empty();
            var minimum = Optional.<BigDecimal>empty();
            var exclusiveMinimum = Optional.<BigDecimal>empty();
            var format = Optional.<String>empty();
            var pattern = Optional.<String>empty();
            var contentEncoding = Optional.<String>empty();
            var contentMediaType = Optional.<String>empty();
            var maxLength = Optional.<BigInteger>empty();
            var minLength = Optional.<BigInteger>empty();
            var maxItems = Optional.<BigInteger>empty();
            var minItems = Optional.<BigInteger>empty();
            var maxContains = Optional.<BigInteger>empty();
            var minContains = Optional.<BigInteger>empty();
            var maxProperties = Optional.<BigInteger>empty();
            var minProperties = Optional.<BigInteger>empty();
            var contentSchema = Optional.<JsonSchema>empty();
            var items = Optional.<JsonSchema>empty();
            var contains = Optional.<JsonSchema>empty();
            var unevaluatedItems = Optional.<JsonSchema>empty();
            var additionalProperties = Optional.<JsonSchema>empty();
            var propertyNames = Optional.<JsonSchema>empty();
            var unevaluatedProperties = Optional.<JsonSchema>empty();
            var not = Optional.<JsonSchema>empty();
            var ifSchema = Optional.<JsonSchema>empty();
            var thenSchema = Optional.<JsonSchema>empty();
            var elseSchema = Optional.<JsonSchema>empty();
            var prefixItems = Optional.<List<JsonSchema>>empty();
            var anyOf = Optional.<List<JsonSchema>>empty();
            var allOf = Optional.<List<JsonSchema>>empty();
            var oneOf = Optional.<List<JsonSchema>>empty();
            var required = Optional.<List<String>>empty();
            var dependentRequired = Optional.<Map<String, List<String>>>empty();
            var extensions = new LinkedHashMap<String, JsonValue>();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var name = parser.currentName();
                parser.nextToken();
                switch (name) {
                    case "$id" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        id = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$comment" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        comment = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$schema" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        schema = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$anchor" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        anchor = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$dynamicAnchor" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        dynamicAnchor = Optional.of(context.readValue(parser, String.class));
                    }
                    case "title" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        title = Optional.of(context.readValue(parser, String.class));
                    }
                    case "description" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        description = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$ref" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        ref = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$dynamicRef" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        dynamicRef = Optional.of(context.readValue(parser, String.class));
                    }
                    case "$vocabulary" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        vocabulary = Optional.of(readVocabulary(parser, context));
                    }
                    case "$defs" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        defs = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "properties" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        properties = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "patternProperties" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        patternProperties = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "dependentSchemas" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        dependentSchemas = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "deprecated" -> {
                        if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected boolean for %s", name);
                        }
                        deprecated = Optional.of(context.readValue(parser, Boolean.class));
                    }
                    case "readOnly" -> {
                        if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected boolean for %s", name);
                        }
                        readOnly = Optional.of(context.readValue(parser, Boolean.class));
                    }
                    case "writeOnly" -> {
                        if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected boolean for %s", name);
                        }
                        writeOnly = Optional.of(context.readValue(parser, Boolean.class));
                    }
                    case "uniqueItems" -> {
                        if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected boolean for %s", name);
                        }
                        uniqueItems = Optional.of(context.readValue(parser, Boolean.class));
                    }
                    case "type" -> {
                        if (parser.currentToken() == JsonToken.VALUE_NULL) {
                            context.reportInputMismatch(SchemaKeywords.class, "Type cannot be null");
                        }
                        type = Optional.of(context.readValue(parser, TypeDeclaration.class));
                    }
                    case "enum" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        enumValues = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "examples" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        examples = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "const" -> {
                        constValue = Optional.of(context.readValue(parser, JsonValue.class));
                    }
                    case "default" -> {
                        defaultValue = Optional.of(context.readValue(parser, JsonValue.class));
                    }
                    case "multipleOf" -> {
                        if (!parser.currentToken().isNumeric()) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected number for %s", name);
                        }
                        multipleOf = Optional.of(context.readValue(parser, BigDecimal.class));
                    }
                    case "maximum" -> {
                        if (!parser.currentToken().isNumeric()) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected number for %s", name);
                        }
                        maximum = Optional.of(context.readValue(parser, BigDecimal.class));
                    }
                    case "exclusiveMaximum" -> {
                        if (!parser.currentToken().isNumeric()) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected number for %s", name);
                        }
                        exclusiveMaximum = Optional.of(context.readValue(parser, BigDecimal.class));
                    }
                    case "minimum" -> {
                        if (!parser.currentToken().isNumeric()) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected number for %s", name);
                        }
                        minimum = Optional.of(context.readValue(parser, BigDecimal.class));
                    }
                    case "exclusiveMinimum" -> {
                        if (!parser.currentToken().isNumeric()) {
                            context.reportInputMismatch(SchemaKeywords.class, "Expected number for %s", name);
                        }
                        exclusiveMinimum = Optional.of(context.readValue(parser, BigDecimal.class));
                    }
                    case "format" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        format = Optional.of(context.readValue(parser, String.class));
                    }
                    case "pattern" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        pattern = Optional.of(context.readValue(parser, String.class));
                    }
                    case "contentEncoding" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        contentEncoding = Optional.of(context.readValue(parser, String.class));
                    }
                    case "contentMediaType" -> {
                        requireToken(parser, context, JsonToken.VALUE_STRING);
                        contentMediaType = Optional.of(context.readValue(parser, String.class));
                    }
                    case "maxLength" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        maxLength = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "minLength" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        minLength = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "maxItems" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        maxItems = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "minItems" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        minItems = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "maxContains" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        maxContains = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "minContains" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        minContains = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "maxProperties" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        maxProperties = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "minProperties" -> {
                        requireToken(parser, context, JsonToken.VALUE_NUMBER_INT);
                        minProperties = Optional.of(context.readValue(parser, BigInteger.class));
                    }
                    case "contentSchema" -> {
                        contentSchema = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "items" -> {
                        items = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "contains" -> {
                        contains = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "unevaluatedItems" -> {
                        unevaluatedItems = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "additionalProperties" -> {
                        additionalProperties = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "propertyNames" -> {
                        propertyNames = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "unevaluatedProperties" -> {
                        unevaluatedProperties = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "not" -> {
                        not = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "if" -> {
                        ifSchema = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "then" -> {
                        thenSchema = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "else" -> {
                        elseSchema = Optional.of(context.readValue(parser, JsonSchema.class));
                    }
                    case "prefixItems" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        prefixItems = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "anyOf" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        anyOf = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "allOf" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        allOf = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "oneOf" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        oneOf = Optional.of(context.readValue(parser, new TypeReference<>() {}));
                    }
                    case "required" -> {
                        requireToken(parser, context, JsonToken.START_ARRAY);
                        required = Optional.of(readStrings(parser, context));
                    }
                    case "dependentRequired" -> {
                        requireToken(parser, context, JsonToken.START_OBJECT);
                        dependentRequired = Optional.of(readDependentRequired(parser, context));
                    }
                    default -> extensions.put(name, context.readValue(parser, JsonValue.class));
                }
            }
            return new SchemaKeywords(
                    id,
                    comment,
                    schema,
                    anchor,
                    dynamicAnchor,
                    title,
                    description,
                    ref,
                    dynamicRef,
                    vocabulary,
                    defs,
                    properties,
                    patternProperties,
                    dependentSchemas,
                    deprecated,
                    readOnly,
                    writeOnly,
                    uniqueItems,
                    type,
                    enumValues,
                    examples,
                    constValue,
                    defaultValue,
                    multipleOf,
                    maximum,
                    exclusiveMaximum,
                    minimum,
                    exclusiveMinimum,
                    format,
                    pattern,
                    contentEncoding,
                    contentMediaType,
                    maxLength,
                    minLength,
                    maxItems,
                    minItems,
                    maxContains,
                    minContains,
                    maxProperties,
                    minProperties,
                    contentSchema,
                    items,
                    contains,
                    unevaluatedItems,
                    additionalProperties,
                    propertyNames,
                    unevaluatedProperties,
                    not,
                    ifSchema,
                    thenSchema,
                    elseSchema,
                    prefixItems,
                    anyOf,
                    allOf,
                    oneOf,
                    required,
                    dependentRequired,
                    extensions
            );
        }
    }
}
