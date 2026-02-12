# REPORT: POJO Generation with Lombok + Jackson via jsonschema2pojo

## Summary

Successfully implemented **Option A (Link as Dependency)** from PLAN.md.
The `avro-to-json-maven-plugin` now supports a new `generate-pojo` goal that produces
Java POJO classes with Lombok and Jackson annotations from Avro schemas.

**Pipeline:** `.avsc` (Avro) → `.json` (JSON Schema) → `.java` (Lombok + Jackson POJOs)

---

## What Was Done

### 1. Created GitHub Fork

- Forked `joelittlejohn/jsonschema2pojo` → [`org-metalib/jsonschema2pojo`](https://github.com/org-metalib/jsonschema2pojo)
- Added `org-metalib` remote to local clone

### 2. Added Dependencies

**Parent POM** (`avro-to-json/pom.xml`):
- `org.jsonschema2pojo:jsonschema2pojo-core:1.3.3` in `<dependencyManagement>`
- `org.projectlombok:lombok:1.18.36` in `<dependencyManagement>` (scope: provided)

**Plugin POM** (`avro-to-json-maven-plugin/pom.xml`):
- `jsonschema2pojo-core` dependency
- `maven-core` dependency (for `MavenProject.addCompileSourceRoot()`)

### 3. Created LombokAnnotator

**File:** `avro-to-json-maven-plugin/src/main/java/org/metalib/schema/avro/json/maven/LombokAnnotator.java`

A custom `AbstractAnnotator` that adds four Lombok annotations to every generated class:
- `@Data` — getters, setters, equals, hashCode, toString
- `@Builder` — fluent builder pattern
- `@NoArgsConstructor` — required for Jackson deserialization
- `@AllArgsConstructor` — required by `@Builder`

Annotations are added in the `propertyOrder()` callback, which fires once per class.

### 4. Created AvroToJsonPojoMojo

**File:** `avro-to-json-maven-plugin/src/main/java/org/metalib/schema/avro/json/maven/AvroToJsonPojoMojo.java`

New Maven goal `generate-pojo` (phase: `GENERATE_SOURCES`) that:

1. Converts `.avsc` → `.json` using `avro-to-json-core`
2. Invokes `Jsonschema2Pojo.generate()` on the JSON Schema output
3. Adds the output directory to Maven's compile source roots

**Configuration parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `sourceDirectory` | `src/main/avro` | Directory with `.avsc` files |
| `jsonSchemaDirectory` | `target/generated-resources/json-schema` | Intermediate JSON Schema output |
| `pojoOutputDirectory` | `target/generated-sources/avro-pojo` | Generated Java output |
| `targetPackage` | `""` | Target Java package |
| `strict` | `false` | Strict JSON Schema mode |
| `draft` | `draft-07` | JSON Schema draft version |
| `useLombok` | `true` | Add Lombok annotations |

**Key design decisions:**
- When `useLombok=true`, disables jsonschema2pojo's built-in generation of getters, setters, equals, hashCode, toString, builders, and constructors (Lombok handles all of these)
- Sets `refFragmentPathDelimiters` to `#/` (without `.`) to prevent dotted namespace keys like `com.example.User` from being incorrectly split during `$ref` resolution
- Disables `additionalProperties` and `@Generated` annotation for cleaner output
- Uses `CompositeAnnotator` internally (Jackson2Annotator + LombokAnnotator)

### 5. Updated Sample Project

**File:** `samples/avro-to-json-maven-plugin-sample/pom.xml`

Added:
- `jackson-databind`, `jackson-annotations` as compile dependencies
- `lombok` as provided dependency
- New `generate-pojo` execution with `targetPackage=com.example`

**New tests** in `AvroToJsonMavenPluginSampleTest`:
- `pojoClassesGenerated()` — verifies User.java, Order.java, Category.java exist
- `userPojoContainsLombokAnnotations()` — verifies `@Data`, `@Builder`, etc.
- `userPojoContainsJacksonAnnotations()` — verifies `@JsonProperty`, `@JsonInclude`

---

## Generated Output Examples

### User.java (from User.avsc)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "username", "email", "createdAt"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @JsonProperty("id")
    public Integer id;
    @JsonProperty("username")
    public String username;
    @JsonProperty("email")
    public String email = null;
    @JsonProperty("createdAt")
    public Integer createdAt;
}
```

### Order.java (from Order.avsc)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"orderId", "userId", "amount", "items", "metadata", "status", "placedAt"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @JsonProperty("orderId")
    public String orderId;
    @JsonProperty("userId")
    public Integer userId;
    @JsonProperty("amount")
    public Double amount;
    @JsonProperty("items")
    public List<String> items = new ArrayList<String>();
    @JsonProperty("metadata")
    public Metadata metadata;
    @JsonProperty("status")
    public Order.Status status;
    @JsonProperty("placedAt")
    public Integer placedAt;

    public enum Status {
        PENDING("PENDING"), CONFIRMED("CONFIRMED"), SHIPPED("SHIPPED"),
        DELIVERED("DELIVERED"), CANCELLED("CANCELLED");
        // ... @JsonValue, @JsonCreator methods
    }
}
```

---

## Phase 1 Test Results

```
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0  (avro-to-json-core)
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0  (avro-to-json-cli)
Tests run:  6, Failures: 0, Errors: 0, Skipped: 0  (sample project)
BUILD SUCCESS
```

---

## Known Limitations & Future Improvements

1. **`javaType` hints not fully honored**: The avro-to-json-core converter adds `"javaType": "java.time.Instant"` for timestamp-millis fields, but jsonschema2pojo 1.3.3 generates `Integer` instead. This could be addressed by adding `formatTypeMapping` configuration or post-processing.

2. **Recursive type naming**: Self-referential types like `Category` (which has a `parent` field of type `Category`) produce a separate `ComExampleCategory` class from the `$ref` definition. This is because `$ref: "#/definitions/com.example.Category"` resolves to a separately named type. Could be improved by customizing avro-to-json-core's definition key format.

3. **Map types**: Avro map types generate a separate `Metadata` class instead of `Map<String, String>`. This is jsonschema2pojo's default behavior for `{"type": "object", "additionalProperties": {...}}`.

4. **Field visibility**: Generated fields are `public` (jsonschema2pojo's default when getters/setters are disabled). Since `@Data` generates getters/setters anyway, the fields could be made `private` by implementing a custom `RuleFactory`.

---

## Usage

```xml
<plugin>
    <groupId>org.metalib.schema.avro.json</groupId>
    <artifactId>avro-to-json-maven-plugin</artifactId>
    <version>0.0.3-SNAPSHOT</version>
    <executions>
        <!-- Optional: also generate standalone JSON Schema files -->
        <execution>
            <id>generate-json-schema</id>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
        <!-- Generate Lombok + Jackson POJOs -->
        <execution>
            <id>generate-pojo</id>
            <goals>
                <goal>generate-pojo</goal>
            </goals>
            <configuration>
                <targetPackage>com.example</targetPackage>
                <useLombok>true</useLombok>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Required dependencies in the consuming project:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

---

## Phase 2: CLI POJO Generation

### What Was Done

Extended `avro-to-json-cli` to support POJO generation with the same pipeline as the Maven plugin.

#### New CLI Options

| Flag | Description | Default |
|------|-------------|---------|
| `--generate-pojo` | Generate Java POJO source files instead of JSON Schema | off |
| `-p`, `--package` | Target Java package for generated POJOs | `""` |
| `--pojo-output` | Output directory for generated `.java` files | current dir |
| `--no-lombok` | Disable Lombok annotations (only use Jackson) | off (Lombok enabled) |

#### Execution Flow with `--generate-pojo`

1. Read/fetch Avro schema (existing logic — file or registry)
2. Convert Avro → JSON Schema (existing `AvroToJsonSchemaConverter`)
3. Write JSON Schema to a temp directory
4. Call `Jsonschema2Pojo.generate()` with the same config as the Maven plugin
5. Clean up temp directory
6. Print summary of generated files

#### Files Modified/Created (Phase 2)

| File | Action |
|------|--------|
| `avro-to-json-cli/pom.xml` | Modified — added `jsonschema2pojo-core` dependency |
| `avro-to-json-cli/.../AvroToJsonCli.java` | Modified — added POJO options + `generatePojoFiles()` method |
| `avro-to-json-cli/.../AvroToJsonCliTest.java` | Modified — added 3 new POJO generation tests |
| `avro-to-json-cli/README.md` | Modified — added POJO generation usage docs |

#### Design Decisions

- **Temp directory approach**: JSON Schema is written to a temp dir consumed by jsonschema2pojo, then cleaned up. This avoids polluting the user's filesystem.
- **`NoopRuleLogger`**: CLI uses a silent logger since there's no Maven log context. Errors are caught and printed to stderr.
- **`--no-lombok` flag**: When active, re-enables jsonschema2pojo's getters, setters, equals, hashCode, and toString generation.

#### CLI Usage Examples

```shell
# Generate Lombok + Jackson POJOs from a file
java -jar avro-to-json-cli.jar schema.avsc \
    --generate-pojo -p com.example --pojo-output /tmp/pojo

# Generate POJOs without Lombok (Jackson only)
java -jar avro-to-json-cli.jar schema.avsc \
    --generate-pojo --no-lombok -p com.example --pojo-output /tmp/pojo

# Generate POJOs from Schema Registry
java -jar avro-to-json-cli.jar \
    --registry http://localhost:8081 --subject my-topic-value \
    --generate-pojo -p com.example --pojo-output /tmp/pojo
```

---

## Phase 3: Shared LombokAnnotator

### What Was Done

Moved `LombokAnnotator` from per-module duplicates into `avro-to-json-core` so it is shared
by both `avro-to-json-maven-plugin` and `avro-to-json-cli`.

### Changes

1. Added `jsonschema2pojo-core` dependency to `avro-to-json-core/pom.xml`
2. Created `LombokAnnotator.java` in `org.metalib.schema.avro.json` package (core module)
3. Deleted `LombokAnnotator.java` from both `...maven` and `...cli` packages
4. Added `import org.metalib.schema.avro.json.LombokAnnotator` to `AvroToJsonPojoMojo.java` and `AvroToJsonCli.java`

### Architecture After Phase 3

```
avro-to-json-core
├── AvroToJsonSchemaConverter   (Avro → JSON Schema)
├── ConverterOptions / JsonSchemaDraft
└── LombokAnnotator             (shared — adds Lombok annotations via jsonschema2pojo)

avro-to-json-cli         → depends on core (gets LombokAnnotator + jsonschema2pojo transitively)
avro-to-json-maven-plugin → depends on core (gets LombokAnnotator + jsonschema2pojo transitively)
```

---

## Test Results (All Phases)

```
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0  (avro-to-json-core)
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0  (avro-to-json-cli — 5 existing + 3 new POJO tests + 4 registry client)
Tests run:  6, Failures: 0, Errors: 0, Skipped: 0  (sample project)
BUILD SUCCESS — Total: 51 tests
```

---

## Files Modified/Created (All Phases)

### Phase 1: Maven Plugin

| File | Action |
|------|--------|
| `avro-to-json/pom.xml` | Modified — added jsonschema2pojo + lombok to dependency management |
| `avro-to-json/avro-to-json-maven-plugin/pom.xml` | Modified — added jsonschema2pojo-core + maven-core deps |
| `avro-to-json-maven-plugin/.../AvroToJsonPojoMojo.java` | **Created** — generate-pojo goal |
| `samples/.../pom.xml` | Modified — added generate-pojo execution + deps |
| `samples/.../AvroToJsonMavenPluginSampleTest.java` | Modified — added POJO verification tests |

### Phase 2: CLI Tool

| File | Action |
|------|--------|
| `avro-to-json-cli/pom.xml` | Modified — added `jsonschema2pojo-core` dependency |
| `avro-to-json-cli/.../AvroToJsonCli.java` | Modified — added POJO generation options + logic |
| `avro-to-json-cli/.../AvroToJsonCliTest.java` | Modified — added 3 POJO generation tests |
| `avro-to-json-cli/README.md` | Modified — added POJO generation docs |

### Phase 3: Shared LombokAnnotator

| File | Action |
|------|--------|
| `avro-to-json-core/pom.xml` | Modified — added `jsonschema2pojo-core` dependency |
| `avro-to-json-core/.../LombokAnnotator.java` | **Created** — shared Lombok annotator |
| `avro-to-json-maven-plugin/.../LombokAnnotator.java` | **Deleted** — replaced by core version |
| `avro-to-json-cli/.../LombokAnnotator.java` | **Deleted** — replaced by core version |
| `avro-to-json-maven-plugin/.../AvroToJsonPojoMojo.java` | Modified — updated import |
| `avro-to-json-cli/.../AvroToJsonCli.java` | Modified — updated import |