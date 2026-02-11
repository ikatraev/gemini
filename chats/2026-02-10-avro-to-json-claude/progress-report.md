# Progress Report: Avro to JSON Schema — POJO-Optimized Unions + ConverterOptions
**Date:** 2026-02-10
**Status:** Implemented — All 32 tests passing (24 core + 8 CLI)

## Objective
Simplify the generated JSON Schema so that POJO generators (e.g., jsonschema2pojo) produce clean Java types instead of composite wrappers for Avro's nullable union pattern (`["null", "SomeType"]`).

## Changes Made

### 1. Flatten Nullable Unions (core change)
- **Before:** `["null", "string"]` → `{"type": ["null", "string"]}` or `{"oneOf": [...]}`
- **After:** `["null", "string"]` → `{"type": "string"}` (field excluded from `required`)
- Applies to all nullable union variants: primitives, records, enums, arrays, logical types, and recursive `$ref` types.
- True multi-type unions (`["null", "string", "int"]`) still use `oneOf`.

### 2. Added `additionalProperties: false` to Records
- All record schemas now emit `"additionalProperties": false` for stricter POJO generation.

### 3. Omit Empty `required` Array
- When all fields are nullable, the `required` key is omitted entirely instead of emitting `"required": []`.

### 4. Removed Dead Code
- Removed `isSimpleNullable()` and `getSimpleTypeName()` — no longer needed after the union handling rewrite.

### 5. `ConverterOptions` Configuration Layer (v3)
- **New file:** `ConverterOptions.java` — Java 17 record with 4 boolean flags + 1 enum field:
  - `flattenNullableUnions` — flatten `["null", X]` to just X (POJO mode) vs type array/oneOf (strict mode)
  - `additionalPropertiesFalse` — emit `"additionalProperties": false` on records
  - `omitEmptyRequired` — omit empty `required` array
  - `javaTypeHints` — emit `javaType` properties for logical types
  - `draft` — `JsonSchemaDraft` enum selecting output draft version
- Two factory presets: `pojoOptimized()` (all true, DRAFT_07) and `strict()` (all false, DRAFT_07)
- `withDraft(JsonSchemaDraft)` convenience method to change draft while preserving other flags
- No-arg `AvroToJsonSchemaConverter()` constructor defaults to `pojoOptimized()` for backward compatibility

### 6. `javaType` Hints for Logical Types
- When `javaTypeHints` is enabled, logical types emit a `javaType` property for POJO generators (jsonschema2pojo):
  - `uuid` → `java.util.UUID`
  - `timestamp-millis` / `timestamp-micros` → `java.time.Instant`
  - `date` → `java.time.LocalDate`
  - `time-millis` / `time-micros` → `java.time.LocalTime`
  - `decimal` → `java.math.BigDecimal`
  - `duration` → `java.time.Duration`

### 7. Strict JSON Schema Mode
- `--strict` CLI flag selects `ConverterOptions.strict()`
- Strict mode behavior:
  - Nullable simple types → `{"type": ["null", "string"]}` (type array)
  - Nullable complex types → `{"oneOf": [{"type":"null"}, ...]}`
  - No `additionalProperties: false`
  - Empty `required` array preserved (not omitted)
  - No `javaType` hints

### 8. Duration Logical Type (v4)
- **Bug fix:** Added `if (!node.has("type"))` guard to `BYTES, FIXED` case so logical types (like `decimal` on bytes) don't get overwritten with `contentEncoding: "base64"`
- **Duration support:** Avro `duration` (FIXED size 12: months/days/millis) maps to `type: "string"`, `format: "duration"` (ISO 8601), optional `javaType: "java.time.Duration"`
- **Fallback:** If Avro doesn't register `duration` as a built-in logical type, falls back to checking `schema.getProp("logicalType")`

### 9. JSON Schema draft-2020-12 Output (v4)
- **New file:** `JsonSchemaDraft.java` — enum with `DRAFT_07` and `DRAFT_2020_12`, encapsulating:
  - `schemaUrl()` — `$schema` URI
  - `definitionsKeyword()` — `"definitions"` (draft-07) vs `"$defs"` (2020-12)
  - `refPrefix()` — `"#/definitions/"` vs `"#/$defs/"`
- Converter uses `options.draft()` instead of hardcoded values (3 line changes)
- **CLI:** `--draft draft-07|draft-2020-12` option (default: `draft-07`)

### 10. Schema Registry Integration (v4)
- **New file:** `SchemaRegistryClient.java` — uses `java.net.http.HttpClient` (zero external deps), fetches `GET {baseUrl}/subjects/{subject}/versions/{version}/schema` with 10s connect timeout
- **CLI refactored** with Picocli `@ArgGroup` for mutual exclusivity:
  - File input: `<inputFile>` (positional parameter)
  - Registry input: `--registry <URL> --subject <name> [--version <ver>]` (default version: `latest`)
  - Providing both is a parse error

## Files Created
- `avro-to-json-core/src/main/java/.../ConverterOptions.java` — Configuration record with 4 flags + draft enum + 2 factory presets + withDraft()
- `avro-to-json-core/src/main/java/.../JsonSchemaDraft.java` — Enum: DRAFT_07 / DRAFT_2020_12
- `avro-to-json-cli/src/main/java/.../SchemaRegistryClient.java` — HTTP client for Confluent Schema Registry

## Files Modified
- `avro-to-json-core/src/main/java/.../AvroToJsonSchemaConverter.java` — Added options field, constructors, conditional logic, javaType hints, strict-mode union handling, `isSimpleType()`/`mapSimpleTypeName()` helpers, duration logical type, BYTES/FIXED guard fix, draft-aware `$schema`/definitions/$ref
- `avro-to-json-core/src/test/java/.../AvroToJsonSchemaConverterTest.java` — Updated 4 tests, added 17 new tests (total: 24)
- `avro-to-json-cli/src/main/java/.../AvroToJsonCli.java` — Added `--strict` flag, `--draft` option, `@ArgGroup` refactor for file vs registry input
- `avro-to-json-cli/src/test/java/.../AvroToJsonCliTest.java` — Added 3 new tests (total: 5)

## Test Files Created
- `avro-to-json-cli/src/test/java/.../SchemaRegistryClientTest.java` — 3 tests using JDK HttpServer mock

## Test Summary (32 tests)

### Core Tests (24)
| Test | Status |
|------|--------|
| testSimpleRecord | Pass |
| testNullableField | Pass (updated: expects flattened type) |
| testRecursiveRecord | Pass (updated: expects `$ref` directly) |
| testLogicalTypes | Pass |
| testCustomProperties | Pass |
| testDefaultValue | Pass (updated: expects flattened type) |
| testOptionalFields | Pass (updated: all union types flattened) |
| testMultiTypeUnion | Pass — verifies `oneOf` for 3+ type unions |
| testAdditionalPropertiesFalse | Pass — verifies `additionalProperties: false` |
| testJavaTypeHints | **New v3** — verifies all 7 logical type javaType mappings |
| testStrictModeNoJavaTypeHints | **New v3** — strict mode omits javaType |
| testStrictModeNullableUnionTypeArray | **New v3** — type array `["null","string"]` |
| testStrictModeNoAdditionalPropertiesFalse | **New v3** — no additionalProperties key |
| testStrictModeKeepsEmptyRequired | **New v3** — empty required array preserved |
| testStrictModeNullableRecordUsesOneOf | **New v3** — complex nullable uses oneOf |
| testDurationLogicalType | **New v4** — duration field → string/duration/javaType |
| testDurationLogicalTypeStrictMode | **New v4** — strict mode omits javaType for duration |
| testFixedWithoutLogicalTypeStillBase64 | **New v4** — plain FIXED → base64 (regression guard) |
| testDecimalOnBytesType | **New v4** — decimal on bytes → number, no contentEncoding (regression guard) |
| testDraft202012SchemaUrl | **New v4** — verifies `$schema` URL for 2020-12 |
| testDraft202012UsesDefsNotDefinitions | **New v4** — recursive record uses `$defs` and `#/$defs/X` |
| testDraft07BackwardCompatibility | **New v4** — default still uses `definitions` |
| testConverterOptionsWithDraft | **New v4** — `withDraft()` preserves other flags |
| testConverterOptionsPresets | **Updated v4** — factory method flag values + draft field |

### CLI Tests (5)
| Test | Status |
|------|--------|
| testCliConversion | Pass |
| testCliDraft202012 | **New v4** — end-to-end with `--draft draft-2020-12` |
| testCliStrictMode | **New v3** — end-to-end with `--strict` flag |
| testCliRegistryInput | **New v4** — end-to-end with mock server + `--registry` + `--subject` |
| testCliMutualExclusivity | **New v4** — file + `--registry` together fails |

### Schema Registry Client Tests (3)
| Test | Status |
|------|--------|
| testFetchSchemaSuccess | **New v4** — 200 response returns schema |
| testFetchSchemaNotFound | **New v4** — 404 throws IOException |
| testFetchSchemaSpecificVersion | **New v4** — version number in URL |

## Example Output Comparison

### Before (v1)
```json
{
  "properties": {
    "email": { "type": ["null", "string"], "default": null },
    "address": { "oneOf": [{"type": "null"}, {"type": "object", ...}] }
  },
  "required": []
}
```

### After (v2)
```json
{
  "properties": {
    "email": { "type": "string", "default": null },
    "address": { "type": "object", "title": "Address", ..., "default": null }
  },
  "additionalProperties": false
}
```

## Completed Next Steps (from v2)
- [x] Added `javaType` hints for logical types (uuid, date, time, timestamps, decimal)
- [x] Added configuration flag (`ConverterOptions`) for toggling between strict JSON Schema and POJO-optimized output
- [x] Added `--strict` CLI flag

## Completed Future Work (from v3)
- [x] Support for Avro `fixed` logical types (e.g., `duration`)
- [x] JSON Schema draft-2020-12 output option (`--draft draft-2020-12`)
- [x] Schema registry integration (`--registry`, `--subject`, `--version`)
