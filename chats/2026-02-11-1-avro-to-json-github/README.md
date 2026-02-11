# Publish avro-to-json to GitHub

## Task

Create a public repository under `https://github.com/org-metalib/` for the `avro-to-json` project and push the code there.

## Result

**Repository:** https://github.com/org-metalib/avro-to-json

## Steps Completed

1. **Initialized git repo** in `avro-to-json/` — the directory already had a `.gitignore` excluding `target/`, `.idea/`, and `*.iml`.
2. **Staged all project files** (12 files, 1655 lines) — parent POM, core module, CLI module, and tests.
3. **Created initial commit** with message: "Initial commit: avro-to-json schema converter".
4. **Created public GitHub repo** `org-metalib/avro-to-json` via `gh repo create` with description "Converts Apache Avro schemas to JSON Schema (draft-07)".
5. **Pushed `main` branch** to origin.

## Files Pushed

```
.gitignore
pom.xml
avro-to-json-core/pom.xml
avro-to-json-core/src/main/java/org/metalib/schema/avro/json/AvroToJsonSchemaConverter.java
avro-to-json-core/src/main/java/org/metalib/schema/avro/json/ConverterOptions.java
avro-to-json-core/src/main/java/org/metalib/schema/avro/json/JsonSchemaDraft.java
avro-to-json-core/src/test/java/org/metalib/schema/avro/json/AvroToJsonSchemaConverterTest.java
avro-to-json-cli/pom.xml
avro-to-json-cli/src/main/java/org/metalib/schema/avro/json/cli/AvroToJsonCli.java
avro-to-json-cli/src/main/java/org/metalib/schema/avro/json/cli/SchemaRegistryClient.java
avro-to-json-cli/src/test/java/org/metalib/schema/avro/json/cli/AvroToJsonCliTest.java
avro-to-json-cli/src/test/java/org/metalib/schema/avro/json/cli/SchemaRegistryClientTest.java
```
