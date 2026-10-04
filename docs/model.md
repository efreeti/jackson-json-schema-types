# Typed JSON Schema model

`nl.efreeti.jsonschema` represents the document shapes described by
[ya-json-schema-types](https://github.com/nfroidure/ya-json-schema-types/blob/main/src/index.ts).
It uses Java 21 sealed interfaces and record variants, following project union
conventions. No JsonNode or untyped Object payloads are used in the model or codecs.

- `JsonSchema`: boolean schemas or object schemas containing `SchemaKeywords`.
- `SchemaKeywords`: all 57 source keywords, with Optional for absent keywords.
- `TypeDeclaration`: a scalar primitive name or a list of primitive names.
- `JsonValue`: null, boolean, decimal number, string, array or object. Used for
  const/default/enum/examples and extension keyword values.
- `SchemaPrimitive`: the seven JSON Schema primitive names.

An object schema uses the complete keyword vocabulary rather than dispatching on
`type`: schemas can omit `type`, combine types and compose arbitrary keywords.
This represents both ordinary and expressive source schemas, but does not recreate
TypeScript's narrower compile-time constraints as separate Java types. Format names
remain strings so custom formats are preserved. Schema semantic validation remains
the responsibility of a separate schema validator.

`JsonSchemaTypesModule` maps these tagged Java unions to native JSON Schema JSON.
It overrides `serializeWithType` and `deserializeWithType`, so Jackson does not emit
or require @type for these values. No mix-ins are installed. Ordinary mappers retain
the project discriminator representation. A keyword or arbitrary JSON value actually
named @type is preserved as data.

The module is registered in `META-INF/services/tools.jackson.databind.JacksonModule`.
Use the framework-managed mapper with module discovery enabled, or for a standalone
Jackson 3 mapper:

```java
var mapper = JsonMapper.builder().findAndAddModules().build();
JsonSchema schema = mapper.readValue(json, JsonSchema.class);
String jsonAgain = mapper.writerFor(JsonSchema.class).writeValueAsString(schema);
```

The keyword deserializer reads fields explicitly to preserve `"default": null` and
`"const": null` as Optional.of(new JsonValue.NullValue()). An omitted field is
Optional.empty(). Ordinary Optional deserialization would collapse those two states.
Numbers use BigDecimal and integer keyword limits use BigInteger to avoid precision
loss. Unknown keywords preserve typed JSON values in the extensions map.

Validation sequence:

1. The initial model and 35 tests compiled; the test run reported 1 failure and
   34 errors. Serialization emitted {"@type":"BooleanSchema","value":false};
   native schema parsing required a missing @type discriminator.
2. Adding only the codecs and service registration made all 35 tests pass.
3. Additional tests cover malformed wire shapes, service discovery, independent
   union codecs and ordinary mapper behavior. Mix-ins were not necessary.

Run the module's tests and build checks:

```sh
mvn verify
```

The library describes and parses schema documents; it does not validate instances against them.
