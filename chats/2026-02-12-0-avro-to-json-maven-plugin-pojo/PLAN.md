# PLAN: Integrating jsonschema2pojo into avro-to-json-maven-plugin for POJO Generation

## Executive Summary

This document describes how to extend the `avro-to-json-maven-plugin` to generate
Java POJO classes (with Lombok + Jackson annotations) from Avro schemas. The pipeline is:

```
.avsc (Avro) → .json (JSON Schema) → .java (POJOs with Lombok + Jackson)
```

The `jsonschema2pojo` library (Apache-2.0 licensed) handles the second step.
A fork has been created at **https://github.com/org-metalib/jsonschema2pojo** to allow
customizations if needed.

---

## Actions Completed

1. **Forked** `joelittlejohn/jsonschema2pojo` → `org-metalib/jsonschema2pojo`
2. **Added remote** `org-metalib` to the local clone at `/Users/igor/github/gemini/jsonschema2pojo`
3. **Analyzed** the jsonschema2pojo API, annotation system, and Maven plugin configuration
4. **Analyzed** the current avro-to-json-maven-plugin Mojo and sample project

---

## Key Findings

### jsonschema2pojo Capabilities

| Feature | Details |
|---------|---------|
| Latest release | `1.3.3` on Maven Central (`org.jsonschema2pojo:jsonschema2pojo-core:1.3.3`) |
| Annotation styles | JACKSON2, JACKSON3, GSON, MOSHI1, JSONB1, JSONB2, NONE |
| Lombok support | **Not built-in** — requires a custom `Annotator` implementation |
| Programmatic API | `Jsonschema2Pojo.generate(GenerationConfig, RuleLogger)` |
| Code model | Uses `com.sun.codemodel` (JCodeModel) for Java source generation |
| Custom annotators | `Annotator` interface + `CompositeAnnotator` for combining |
| Source types | JSON Schema, JSON example, YAML Schema, YAML example |

### What a Lombok + Jackson POJO Needs

For an Avro `User` record with fields `id`, `username`, `email` (nullable), `createdAt`:

```java
package com.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "username", "email", "createdAt"})
public class User {
    @JsonProperty("id")
    private int id;
    @JsonProperty("username")
    private String username;
    @JsonProperty("email")
    private String email;
    @JsonProperty("createdAt")
    private Instant createdAt;
}
```

Lombok handles: getters, setters, equals, hashCode, toString, builder, constructors.
Jackson handles: JSON serialization/deserialization property mapping.

### jsonschema2pojo Configuration for Lombok-Style Output

```java
GenerationConfig config = new DefaultGenerationConfig() {
    // Jackson annotations
    @Override public AnnotationStyle getAnnotationStyle() { return AnnotationStyle.JACKSON2; }
    @Override public InclusionLevel getInclusionLevel() { return InclusionLevel.NON_NULL; }

    // Disable methods that Lombok generates
    @Override public boolean isIncludeHashcodeAndEquals() { return false; }
    @Override public boolean isIncludeToString() { return false; }
    @Override public boolean isIncludeGetters() { return false; }
    @Override public boolean isIncludeSetters() { return false; }
    @Override public boolean isIncludeConstructors() { return false; }
    @Override public boolean isGenerateBuilders() { return false; }
    @Override public boolean isIncludeAdditionalProperties() { return false; }

    // Custom annotator adds Lombok annotations
    @Override public Class<? extends Annotator> getCustomAnnotator() {
        return LombokAnnotator.class;
    }
};
```

---

## Option A: Link as Dependency (Recommended)

### Architecture

```
avro-to-json-maven-plugin
├── depends on: avro-to-json-core (Avro → JSON Schema conversion)
├── depends on: jsonschema2pojo-core:1.3.3 (JSON Schema → Java POJO)
└── contains:   LombokAnnotator.java (custom Annotator for Lombok)
```

### Implementation Steps

1. **Add `jsonschema2pojo-core` as dependency** to `avro-to-json-maven-plugin/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.jsonschema2pojo</groupId>
       <artifactId>jsonschema2pojo-core</artifactId>
       <version>1.3.3</version>
   </dependency>
   ```

2. **Create `LombokAnnotator`** class in the plugin that adds Lombok annotations:
   - Extends `AbstractAnnotator`
   - Overrides `typeInfo()` or `propertyOrder()` to add class-level annotations:
     `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
   - jsonschema2pojo uses `CompositeAnnotator` to combine this with `Jackson2Annotator`

3. **Add a new goal** (e.g., `generate-pojo`) to `AvroToJsonMojo` or create a separate
   `AvroToJsonPojoMojo` that:
   - Runs the existing Avro → JSON Schema conversion
   - Then invokes `Jsonschema2Pojo.generate(config, logger)` on the output
   - Configures: no getters/setters/equals/hashCode/toString/builders (Lombok does these)
   - Adds generated sources to Maven compile path via `project.addCompileSourceRoot()`

4. **New plugin parameters**:
   ```xml
   <configuration>
       <!-- Existing -->
       <sourceDirectory>${project.basedir}/src/main/avro</sourceDirectory>
       <strict>false</strict>
       <draft>draft-07</draft>
       <!-- New: POJO generation -->
       <generatePojo>true</generatePojo>
       <targetPackage>com.example.generated</targetPackage>
       <pojoOutputDirectory>${project.build.directory}/generated-sources/avro-pojo</pojoOutputDirectory>
       <useLombok>true</useLombok>
   </configuration>
   ```

5. **Update sample project** to demonstrate POJO generation.

### Pros
- Clean, minimal code in avro-to-json-maven-plugin (~100 lines for LombokAnnotator + Mojo changes)
- Leverages battle-tested jsonschema2pojo (6k+ GitHub stars, active maintenance)
- Future jsonschema2pojo improvements come for free via version bump
- The `org-metalib/jsonschema2pojo` fork exists as fallback for patches if needed
- Standard Maven dependency — no build complexity

### Cons
- Adds ~15 transitive dependencies from jsonschema2pojo-core (codemodel, commons-io, commons-text, etc.)
- Users cannot customize the underlying code generation without modifying the plugin
- Tied to jsonschema2pojo's code model (com.sun.codemodel) and its limitations

---

## Option B: Embed Code from jsonschema2pojo

### Architecture

```
avro-to-json (parent)
├── avro-to-json-core          (Avro → JSON Schema)
├── avro-to-json-pojo-core     (NEW: JSON Schema → Java POJO, extracted from jsonschema2pojo)
├── avro-to-json-maven-plugin  (Maven plugin, depends on both cores)
└── avro-to-json-cli
```

### Implementation Steps

1. **Create new module `avro-to-json-pojo-core`** containing extracted code from jsonschema2pojo:
   - `SchemaMapper`, `RuleFactory`, and related classes (~30-40 Java files)
   - `Jackson2Annotator` (or stripped-down version)
   - `LombokAnnotator` (custom)
   - Remove unused annotation styles (Gson, Moshi, JsonB) to reduce footprint
   - Keep only JSON Schema source type (remove YAML, JSON example support)

2. **Change groupId/package** to `org.metalib.schema.avro.json.pojo` to avoid conflicts.

3. **Add direct dependencies**: `com.sun.codemodel:codemodel`, `com.fasterxml.jackson.core:jackson-databind`.

4. **Wire into the Maven plugin** the same way as Option A, but referencing the internal module.

5. **Maintain fork** at `org-metalib/jsonschema2pojo` for syncing upstream changes.

### Pros
- Full control over code generation behavior
- Smaller dependency footprint (only what's needed)
- Can diverge from upstream to fix bugs or add Lombok natively
- No risk of upstream breaking changes

### Cons
- Significant initial effort to extract and refactor (~5,000+ lines of code)
- Ongoing maintenance burden to keep in sync with upstream fixes
- Must respect Apache-2.0 license (attribution, NOTICE file)
- Duplicates well-maintained open-source code

---

## Recommendation

**Option A (Link as Dependency) is strongly recommended** for the following reasons:

1. **Minimal effort**: Only need to write `LombokAnnotator` (~80 lines) and update the Mojo
2. **Proven library**: jsonschema2pojo handles edge cases (recursive types, enums, `$ref`, etc.)
3. **Fork safety net**: `org-metalib/jsonschema2pojo` exists for emergency patches
4. **The avro-to-json-maven-plugin already produces JSON Schema** that jsonschema2pojo consumes directly — no impedance mismatch

Option B should only be considered if jsonschema2pojo proves fundamentally unsuitable
(e.g., cannot handle the JSON Schema output from avro-to-json-core).

---

## Implementation Priority

### Phase 1: Maven Plugin (Completed)

| Step | Description | Effort |
|------|-------------|--------|
| 1 | Add `jsonschema2pojo-core:1.3.3` dependency | 5 min |
| 2 | Create `LombokAnnotator` (custom annotator) | 1-2 hrs |
| 3 | Create `AvroToJsonPojoMojo` (new goal or extend existing) | 2-3 hrs |
| 4 | Add plugin parameters for POJO config | 30 min |
| 5 | Update sample project with POJO generation example | 1 hr |
| 6 | Test with all 3 sample schemas (User, Order, Category) | 1-2 hrs |
| 7 | Handle enum generation (Avro enums → Java enums with Jackson) | 1 hr |

### Phase 2: CLI Tool (Completed)

| Step | Description | Effort |
|------|-------------|--------|
| 1 | Add `jsonschema2pojo-core` dependency to CLI `pom.xml` | 5 min |
| 2 | Add `--generate-pojo`, `-p`, `--pojo-output`, `--no-lombok` CLI options | 30 min |
| 3 | Implement POJO generation flow (temp dir → jsonschema2pojo → summary) | 1 hr |
| 4 | Add tests: POJO from file, POJO no-lombok, POJO from registry | 30 min |
| 5 | Update CLI README.md | 15 min |
| 6 | Build and verify all modules | 15 min |

### Phase 3: Shared LombokAnnotator (Completed)

Moved `LombokAnnotator` into `avro-to-json-core` so it is shared by both
`avro-to-json-maven-plugin` and `avro-to-json-cli`.

| Step | Description | Effort |
|------|-------------|--------|
| 1 | Add `jsonschema2pojo-core` dependency to `avro-to-json-core/pom.xml` | 5 min |
| 2 | Create `LombokAnnotator.java` in `org.metalib.schema.avro.json` package | 5 min |
| 3 | Delete duplicate copies from maven-plugin and cli packages | 5 min |
| 4 | Add `import org.metalib.schema.avro.json.LombokAnnotator` to both consumers | 5 min |
| 5 | Build and verify all 51 tests pass | 5 min |

**Architecture after Phase 3:**

```
avro-to-json-core
├── AvroToJsonSchemaConverter   (Avro → JSON Schema)
├── ConverterOptions / JsonSchemaDraft
└── LombokAnnotator             (shared — adds Lombok annotations via jsonschema2pojo)

avro-to-json-cli        → depends on core (gets LombokAnnotator + jsonschema2pojo transitively)
avro-to-json-maven-plugin → depends on core (gets LombokAnnotator + jsonschema2pojo transitively)
```

---

## References

- Fork: https://github.com/org-metalib/jsonschema2pojo
- Upstream: https://github.com/joelittlejohn/jsonschema2pojo
- jsonschema2pojo Maven Central: `org.jsonschema2pojo:jsonschema2pojo-core:1.3.3`
- Programmatic API entry point: `org.jsonschema2pojo.Jsonschema2Pojo.generate(GenerationConfig, RuleLogger)`
- Custom annotator interface: `org.jsonschema2pojo.Annotator`
- avro-to-json plugin Mojo: `org.metalib.schema.avro.json.maven.AvroToJsonMojo`