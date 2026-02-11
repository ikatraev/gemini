# Progress Report: Avro to JSON Schema Converter
**Date:** 2026-02-08
**Status:** Feature Complete (Version 0.0.1-SNAPSHOT) - Java 17 Modernized

## Completed Tasks
1.  **Project Initialization**: Created a Maven project with coordinates `org.metalib.schema.avro.json:avro-to-json:0.0.1-SNAPSHOT`.
2.  **Architecture Refactoring**:
    -   Converted to a **Multi-module Maven Project**.
    -   `avro-to-json` (Root): Parent POM managing versions and plugins.
    -   `avro-to-json-core` (Module): Core library containing all logic and tests.
    -   `avro-to-json-cli` (Module): CLI wrapper for the library.
3.  **Java 17 Modernization**:
    -   Updated project to target **Java 17** (`maven.compiler.release = 17`).
    -   Refactored code to use **Switch Expressions** and **Records**.
    -   Updated tests to use **Text Blocks** for improved readability of JSON/Avro schemas.
4.  **Core Implementation (in `avro-to-json-core`)**:
    -   Implemented `AvroToJsonSchemaConverter.java`.
    -   Supported Types: `RECORD`, `ARRAY`, `MAP`, `ENUM`, `UNION`, and all primitive types.
    -   Implemented smart union handling.
    -   **Recursive Records**: Implemented full support for recursive data structures (e.g., Linked Lists, Trees) using JSON Schema `definitions` and `$ref`.
    -   **Logical Types**: Added mapping for Avro logical types:
        -   `decimal` -> `number`
        -   `timestamp-millis`, `timestamp-micros` -> `integer` (format: `utc-millisec`)
        -   `date` -> `string` (format: `date`)
        -   `time-millis`, `time-micros` -> `string` (format: `time`)
        -   `uuid` -> `string` (format: `uuid`)
    -   **Custom Properties**: Implemented pass-through for custom metadata properties from Avro to JSON Schema.
5.  **CLI Wrapper (in `avro-to-json-cli`)**:
    -   Implemented `AvroToJsonCli.java` using **picocli**.
    -   Supports file input and output (optional, defaults to stdout).
    -   Packaged as an **executable shaded JAR**.
6.  **Testing**:
    -   Extended `AvroToJsonSchemaConverterTest.java`.
    -   Added `AvroToJsonCliTest.java` for the CLI module.
    -   Verified successful conversion of standard records, nullable fields, **recursive records**, **logical types**, and **custom properties**.
    -   Confirmed successful build with `mvn clean test` on the multi-module structure.

## Current Project Structure
```
avro-to-json/
├── pom.xml (Parent)
├── avro-to-json-core/
│   ├── pom.xml (Module)
│   └── src/
│       ├── main/java/org/metalib/schema/avro/json/AvroToJsonSchemaConverter.java
│       └── test/java/org/metalib/schema/avro/json/AvroToJsonSchemaConverterTest.java
└── avro-to-json-cli/
    ├── pom.xml (Module)
    └── src/
        ├── main/java/org/metalib/schema/avro/json/cli/AvroToJsonCli.java
        └── test/java/org/metalib/schema/avro/json/cli/AvroToJsonCliTest.java
```

## Next Steps
-   [x] Create a CLI wrapper module (`avro-to-json-cli`).
-   [ ] (Optional) Publish to a package registry.
