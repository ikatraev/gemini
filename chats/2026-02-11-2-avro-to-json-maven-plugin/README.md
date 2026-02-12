# Publish avro-to-json to GitHub

## Task

Create new module `avro-to-json-maven-plugin` to `avro-to-json` with the similar feature as the cli module.

Add the summary of the execution to the README.md

## Result

Created the `avro-to-json-maven-plugin` module with the following structure:

```
avro-to-json-maven-plugin/
├── pom.xml
└── src/main/java/org/metalib/schema/avro/json/maven/
    └── AvroToJsonMojo.java
```

### What was done

1. **Created `avro-to-json-maven-plugin/pom.xml`** — Maven plugin packaging, depends on `avro-to-json-core`, `maven-plugin-api` 3.9.9, and `maven-plugin-annotations` 3.15.2. Uses `maven-plugin-plugin` 3.15.2 for descriptor generation.

2. **Created `AvroToJsonMojo.java`** — The Mojo class with goal `generate` (default phase: `GENERATE_SOURCES`). It mirrors the CLI functionality:
   - Scans `sourceDirectory` (default: `src/main/avro`) for `.avsc` files recursively
   - Converts each file using `AvroToJsonSchemaConverter` from the core module
   - Writes JSON Schema output to `outputDirectory` (default: `target/generated-resources/json-schema`)
   - Preserves directory structure, replaces `.avsc` extension with `.json`
   - Supports `strict` mode and `draft` version selection (same as CLI)
   - Skips gracefully if source directory doesn't exist

3. **Updated parent `pom.xml`** — Added `avro-to-json-maven-plugin` to the `<modules>` list.

4. **Updated `CLAUDE.md`** — Documented the new module.

### Usage

```xml
<plugin>
  <groupId>org.metalib.schema.avro.json</groupId>
  <artifactId>avro-to-json-maven-plugin</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <!-- <sourceDirectory>${project.basedir}/src/main/avro</sourceDirectory> -->
    <!-- <outputDirectory>${project.build.directory}/generated-resources/json-schema</outputDirectory> -->
    <!-- <strict>false</strict> -->
    <!-- <draft>draft-07</draft> -->
  </configuration>
</plugin>
```

### Build verification

- All 4 modules build successfully: `BUILD SUCCESS`
- All 42 existing tests pass (33 core + 9 CLI)
- Maven plugin descriptor generated correctly (1 mojo descriptor found)

