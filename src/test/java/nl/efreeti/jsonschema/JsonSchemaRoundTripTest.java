package nl.efreeti.jsonschema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import nl.efreeti.jsonschema.jackson.JsonSchemaTypesModule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

class JsonSchemaRoundTripTest {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @TestFactory
    Stream<DynamicTest> edgeCases() throws IOException {
        return resource("cases/index.txt").lines().map(
                name -> DynamicTest.dynamicTest(name, () -> roundTrip(resource("cases/" + name)))
        );
    }

    @Test
    void serializesConcreteAndDeclaredUnionTypes() {
        JsonSchema.BooleanSchema schema = new JsonSchema.BooleanSchema(false);
        assertEquals("false", mapper.writeValueAsString(schema));
        assertEquals("false", mapper.writerFor(JsonSchema.class).writeValueAsString(schema));
        assertEquals("null", mapper.writeValueAsString(new JsonValue.NullValue()));
        assertEquals("\"string\"", mapper.writeValueAsString(new TypeDeclaration.Single(SchemaPrimitive.STRING)));
    }

    @Test
    void preservesExplicitNullAndAbsentDefault() {
        var present = assertInstanceOf(JsonSchema.ObjectSchema.class, mapper.readValue("{\"default\":null}", JsonSchema.class));
        assertInstanceOf(JsonValue.NullValue.class, present.keywords().defaultValue().orElseThrow());
        var absent = assertInstanceOf(JsonSchema.ObjectSchema.class, mapper.readValue("{}", JsonSchema.class));
        assertFalse(absent.keywords().defaultValue().isPresent());
    }

    @Test
    void discoversModuleFromServiceManifest() {
        assertTrue(ServiceLoader.load(JacksonModule.class).stream().anyMatch(
                provider -> provider.type() == JsonSchemaTypesModule.class
        ));
    }

    @Test
    void ordinaryMapperStillUsesProjectDiscriminators() {
        var ordinary = JsonMapper.builder().build();
        assertEquals("{\"@type\":\"BooleanSchema\",\"value\":false}", ordinary.writerFor(JsonSchema.class).writeValueAsString(
                new JsonSchema.BooleanSchema(false)
        ));
        assertThrows(DatabindException.class, () -> ordinary.readValue("{\"type\":\"string\"}", JsonSchema.class));
    }

    @TestFactory
    Stream<DynamicTest> invalidWireShapes() {
        return List.of(
                "null",
                "[]",
                "1",
                "\"string\"",
                "{\"type\":null}",
                "{\"type\":\"unknown\"}",
                "{\"type\":[null]}",
                "{\"items\":[]}",
                "{\"additionalProperties\":null}",
                "{\"title\":1}",
                "{\"required\":{}}",
                "{\"maximum\":\"10\"}",
                "{\"uniqueItems\":\"false\"}",
                "{\"minItems\":1.5}",
                "{\"required\":[1]}",
                "{\"required\":[null]}",
                "{\"dependentRequired\":{\"a\":[false]}}",
                "{\"$vocabulary\":{\"https://example.test/vocab\":null}}",
                "{\"$vocabulary\":{\"https://example.test/vocab\":\"true\"}}"
        )
                .stream()
                .map(json -> DynamicTest.dynamicTest(json, () -> assertThrows(
                        DatabindException.class, () -> mapper.readValue(json, JsonSchema.class)
                )));
    }

    @TestFactory
    Stream<DynamicTest> jsonValues() {
        return List.of(
                "null",
                "true",
                "123456789012345678901234567890.123456789",
                "\"text\"",
                "[]",
                "{}",
                "{\"@type\":\"data\",\"nested\":[null,true,1]}"
        )
                .stream()
                .map(json -> DynamicTest.dynamicTest( json, () -> {
                    var value = mapper.readValue(json, JsonValue.class);
                    var serialized = mapper.writerFor(JsonValue.class).writeValueAsString(value);
                    assertEquals(mapper.readValue(json, Object.class), mapper.readValue(serialized, Object.class));
                    assertEquals(value, mapper.readValue(serialized, JsonValue.class));
                }));
    }

    @TestFactory
    Stream<DynamicTest> typeDeclarations() {
        return List.of("\"string\"", "[\"string\"]", "[\"integer\",\"null\"]").stream().map(
                json -> DynamicTest.dynamicTest(json, () -> assertEquals(
                        json, mapper.writerFor(TypeDeclaration.class).writeValueAsString(mapper.readValue(json, TypeDeclaration.class))
                ))
        );
    }

    private void roundTrip(String json) {
        var schema = mapper.readValue(json, JsonSchema.class);
        var serialized = mapper.writerFor(JsonSchema.class).writeValueAsString(schema);
        // Compare JSON values independently of the schema codecs, ignoring object order/spacing.
        assertEquals(mapper.readValue(json, Object.class), mapper.readValue(serialized, Object.class));
        assertEquals(schema, mapper.readValue(serialized, JsonSchema.class));
    }

    private static String resource(String name) throws IOException {
        try (var input = JsonSchemaRoundTripTest.class.getResourceAsStream("/jsonschema/" + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
