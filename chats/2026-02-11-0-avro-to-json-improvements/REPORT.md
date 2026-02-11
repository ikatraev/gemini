# avro-to-json Project Review

**Date:** 2026-02-11
**Pre-fix status:** BUILD SUCCESS — 32 tests pass (24 core + 8 CLI), 0 failures
**Post-fix status:** BUILD SUCCESS — 42 tests pass (33 core + 9 CLI), 0 failures, 0 warnings

---

## Project Summary

A Maven multi-module project (Java 17) that converts Apache Avro schemas to JSON Schema (draft-07 / draft-2020-12). Two modules:
- **avro-to-json-core** — conversion library (3 source files, 1 test file with 24 tests)
- **avro-to-json-cli** — CLI wrapper using Picocli + Schema Registry client (2 source files, 2 test files with 8 tests)

Fat JAR size: **6.0 MB** (original: 9.3 KB).

---

## What Works Well

1. **Clean architecture** — core library separated from CLI, single public entry point (`AvroToJsonSchemaConverter.convert()`).
2. **Good test coverage** — 32 tests covering simple records, nullable unions, recursive records, logical types, strict mode, draft versions, custom properties, defaults, Schema Registry client, CLI end-to-end.
3. **`ConverterOptions` record** — immutable, clean factory methods (`pojoOptimized()`, `strict()`), `withDraft()` copy method.
4. **`JsonSchemaDraft` enum** — encapsulates draft-specific differences (`$defs` vs `definitions`, ref prefix, schema URL).
5. **CLI design** — mutual exclusivity between file and registry input via Picocli `@ArgGroup`, well-tested.
6. **Schema Registry client** — simple, testable (constructor injection of `HttpClient`), proper error handling.

---

## Improvements — Ranked by Priority

### P0 — Bugs / Correctness

#### 1. Missing root `.gitignore`
There is no `.gitignore` in the project root. The `target/`, `.idea/`, and `dependency-reduced-pom.xml` directories/files should be excluded from version control.

**Fix:** Add a root `.gitignore`:
```
target/
.idea/
*.iml
```
(`dependency-reduced-pom.xml` is no longer needed here — it's now generated inside `target/`, already covered.)

#### 2. `ObjectMapper` is shared but not thread-safe for node creation
`AvroToJsonSchemaConverter` uses a `static final ObjectMapper` for creating nodes (`mapper.createObjectNode()`). While `ObjectMapper.readTree()` is thread-safe, creating nodes from a shared mapper is safe only for reads. The current usage (create + mutate nodes) is fine since nodes are local, but the static mapper prevents per-instance configuration. Consider making it an instance field or injecting it.

**Severity:** Low risk currently (nodes are created locally), but becomes a problem if mapper configuration ever needs to vary.

#### 3. `jackson-core` and `jackson-annotations` are redundant in `avro-to-json-core/pom.xml`
`jackson-databind` already transitively depends on `jackson-core` and `jackson-annotations`. Declaring them explicitly with the same version is harmless but adds noise.

**Fix:** Remove `jackson-core` and `jackson-annotations` from `avro-to-json-core/pom.xml`. Keep them only in `dependencyManagement` to pin versions.

---

### P1 — Dependency Updates

| Dependency | Current | Latest Stable | Note |
|---|---|---|---|
| `picocli` | 4.7.5 | **4.7.7** | Patch update, safe |
| `jackson-*` | 2.19.4 | **2.21.0** | Minor update |
| `junit-jupiter` | 5.9.3 | **5.11.x** | 5.11 is latest stable GA (skip 6.x milestone) |
| `maven-compiler-plugin` | 3.11.0 | **3.15.0** | Latest stable 3.x |
| `maven-shade-plugin` | 3.5.0 | **3.6.1** | Fixes shade warnings |

**Note:** Jackson 3.x and JUnit 6.x are pre-release (RC/M1) — do NOT upgrade to those.

---

### P2 — Build & Packaging

#### 4. Shade plugin warnings about overlapping resources
The build produces multiple warnings about overlapping `META-INF` resources. Fix with filters and transformers:

```xml
<configuration>
  <filters>
    <filter>
      <artifact>*:*</artifact>
      <excludes>
        <exclude>META-INF/MANIFEST.MF</exclude>
        <exclude>META-INF/LICENSE*</exclude>
        <exclude>META-INF/NOTICE*</exclude>
        <exclude>module-info.class</exclude>
        <exclude>META-INF/versions/*/module-info.class</exclude>
      </excludes>
    </filter>
  </filters>
  <transformers>
    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
      <mainClass>org.metalib.schema.avro.json.cli.AvroToJsonCli</mainClass>
    </transformer>
  </transformers>
</configuration>
```

#### 5. Fat JAR is 6 MB — mostly Avro transitive dependencies
The Avro library pulls in `commons-compress`, `commons-codec`, `commons-io`, `commons-lang3`, `slf4j-api` — none of which are needed by the core converter. Two options:
- **Option A (easy):** Add shade plugin `minimizeJar` to strip unused classes. This alone can cut the JAR significantly.
- **Option B (thorough):** Exclude Avro's transitive dependencies that aren't needed:
  ```xml
  <dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <exclusions>
      <exclusion>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-compress</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
  ```
  (Test to confirm nothing breaks.)

#### 6. `dependency-reduced-pom.xml` generated in source directory
The shade plugin generates this file in `avro-to-json-cli/`. Move it to `target/`:
```xml
<configuration>
  <dependencyReducedPomLocation>${project.build.directory}/dependency-reduced-pom.xml</dependencyReducedPomLocation>
</configuration>
```

#### 7. SLF4J "No providers" warning during tests
```
SLF4J(W): No SLF4J providers were found.
```
Add a test-scoped SLF4J provider:
```xml
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-nop</artifactId>
  <version>2.0.17</version>
  <scope>test</scope>
</dependency>
```

---

### P3 — Code Quality

#### 8. `handleLogicalType` return value is unused by most callers
The method returns `boolean` but it's called as a statement (`handleLogicalType(node, schema);`) at line 59 of `AvroToJsonSchemaConverter.java`. The return value is only useful internally. This is fine but the pattern could be cleaner — consider making it void and using `node.has("type")` checks downstream (which is already done).

#### 9. Avro `Schema.getObjectProps()` includes standard props
Line 132 iterates `schema.getObjectProps()`, which may include properties like `logicalType` that are already handled. This means `logicalType` will appear as a duplicate custom property in the JSON Schema output alongside the `format` field. Consider filtering out known Avro-internal properties:
```java
Set<String> AVRO_INTERNAL = Set.of("logicalType", "precision", "scale");
for (Map.Entry<String, Object> entry : schema.getObjectProps().entrySet()) {
    if (!AVRO_INTERNAL.contains(entry.getKey())) {
        node.putPOJO(entry.getKey(), entry.getValue());
    }
}
```

#### 10. `ConverterOptions` would benefit from a builder
With 5 boolean/enum fields, the record constructor is getting long. A builder pattern would make it easier to create custom configurations:
```java
ConverterOptions.builder()
    .flattenNullableUnions(true)
    .draft(JsonSchemaDraft.DRAFT_2020_12)
    .build();
```

#### 11. Schema Registry URL encoding
`SchemaRegistryClient.fetchSchema()` does not URL-encode the `subject` parameter. If a subject name contains special characters (e.g., `com.example.User-value`), the URL could be malformed. Use `URLEncoder.encode(subject, StandardCharsets.UTF_8)`.

---

### P4 — Test Improvements

#### 12. No negative/error-path tests for the core converter
All 24 core tests are happy-path. Consider adding:
- Invalid Avro schema input (malformed JSON, missing `type`)
- Empty `fields` array (already partially tested)
- Deeply nested recursive schemas
- Schemas with namespaces (e.g., `com.example.User`) — verify `$ref` includes namespace

#### 13. No stdin-reading support in CLI
The CLI only supports file input and Schema Registry. A common Unix convention is reading from stdin when no file is specified (e.g., `cat schema.avsc | avro-to-json`). This would enable piping.

#### 14. CLI version is hardcoded
`@Command(version = "0.0.1")` is hardcoded in the annotation. Consider reading the version from `pom.properties` or the JAR manifest to keep it in sync with the Maven version.

---

### P5 — Documentation & Packaging

#### 15. No `LICENSE` file
No license file exists in the repo. If this is intended for public use, add one.

#### 16. No `README.md` in `avro-to-json/`
The `CLAUDE.md` serves as internal documentation but there's no user-facing README with usage examples, installation instructions, or API documentation.

#### 17. No maven-source-plugin or maven-javadoc-plugin
If you plan to publish to Maven Central, you'll need source and javadoc JARs. Add:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-source-plugin</artifactId>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-javadoc-plugin</artifactId>
</plugin>
```

---

## Quick Wins (can be done in < 30 minutes)

1. Add root `.gitignore` (P0)
2. Remove redundant Jackson sub-dependencies from core pom (P0)
3. Update picocli 4.7.5 -> 4.7.7 (P1)
4. Move `dependency-reduced-pom.xml` to `target/` (P2)
5. Add `slf4j-nop` test dependency (P2)
6. Filter known Avro internal props in custom property pass-through (P3)

---

## Architecture Observations

The codebase is well-structured for its scope. The conversion logic in `AvroToJsonSchemaConverter` is ~265 lines — compact and readable. The `ConversionContext` record effectively tracks state without polluting the converter's fields.

If the project grows, consider:
- **Visitor pattern** for Avro type dispatch (replacing the `switch` in `convert()`) — but only if more types/extensions are added.
- **Schema validation** of the output against the JSON Schema meta-schema to catch regressions.
- **Integration tests** with real-world `.avsc` files from popular Avro schemas (Confluent examples, etc.).

---

## Fixes Applied

All fixes below have been implemented and verified with a clean build (42 tests, 0 failures, 0 warnings).

| # | Fix | Files Changed |
|---|-----|---------------|
| 1 | Added `.gitignore` with `target/`, `.idea/`, `*.iml` | `avro-to-json/.gitignore` (new) |
| 2 | Removed redundant `jackson-core` and `jackson-annotations` from core pom | `avro-to-json-core/pom.xml` |
| 3 | Updated `picocli` 4.7.5 -> 4.7.7, `junit-jupiter` 5.9.3 -> 5.13.4, `maven-compiler-plugin` 3.11.0 -> 3.15.0 | `avro-to-json/pom.xml` |
| 4 | Updated `maven-shade-plugin` 3.5.0 -> 3.6.1, added META-INF filters (eliminated all shade warnings), moved `dependency-reduced-pom.xml` to `target/` | `avro-to-json-cli/pom.xml` |
| 5 | Added `slf4j-nop` test dependency (eliminated SLF4J warnings) | `pom.xml`, `avro-to-json-core/pom.xml`, `avro-to-json-cli/pom.xml` |
| 6 | Filtered Avro-internal props (`logicalType`, `precision`, `scale`, `connect.parameters`) from custom property pass-through | `AvroToJsonSchemaConverter.java` |
| 7 | URL-encoded subject parameter in `SchemaRegistryClient.fetchSchema()` | `SchemaRegistryClient.java` |
| 8 | Added 10 new tests: malformed JSON, missing type, namespaced records, enums with doc, array/map/bytes types, internal prop leak check, record documentation + URL-encoding test for registry client | `AvroToJsonSchemaConverterTest.java`, `SchemaRegistryClientTest.java` |

### Remaining (not implemented — need user decision)

- **P2 #5** — Fat JAR size reduction (exclude Avro transitive deps or use `minimizeJar`) — requires testing that nothing breaks at runtime
- **P3 #8** — `handleLogicalType` return value cleanup — cosmetic, low priority
- **P3 #10** — `ConverterOptions` builder pattern — nice-to-have when more options are added
- **P4 #13** — stdin support in CLI
- **P4 #14** — CLI version from manifest
- **P5 #15–17** — LICENSE, README, maven-source/javadoc plugins
