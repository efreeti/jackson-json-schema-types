# Jackson JSON Schema Types

Strongly typed Java 21 models for JSON Schema draft 2020-12, with Jackson 3
serialization and deserialization. Recursive schemas, boolean schemas, type unions
and arbitrary JSON values are represented without JsonNode or untyped payloads.

Maven coordinates: **nl.efreeti:jackson-json-schema-types**. 

```java
import nl.efreeti.jsonschema.JsonSchema;
import tools.jackson.databind.json.JsonMapper;

var mapper = JsonMapper.builder().findAndAddModules().build();
var schema = mapper.readValue("{\"type\":[\"string\",\"null\"]}", JsonSchema.class);
var json = mapper.writerFor(JsonSchema.class).writeValueAsString(schema);
```

The module is discovered using Jackson's service loader. It preserves native schema
JSON rather than emitting Java union discriminators. No mix-ins are needed.

Build with Java 21 or newer and Maven:

```sh
mvn verify
```

Its only direct runtime dependency is
Jackson Databind. This library models schema documents; instance validation is a
separate concern.

See [model details](docs/model.md) and [publishing instructions](docs/publishing.md).
The library is Apache-2.0 licensed; upstream attribution and MIT terms are retained
in [NOTICE](NOTICE).
