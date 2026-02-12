# Publish avro-to-json to GitHub

## Task


Create `jsonschema2pojo` repository and create a suggestions to utilize it in
avro-to-json-maven-plugin for code generation. The generated code should be based on Lombok and Jackson annotations.
Consider both options: either embed some of the code or link it.

Add the summary of the execution to the PLAN.md

## Implementation

### Phase 1: Maven Plugin

Go ahead with the implementation of the recommended Option A: Link as Dependency.
Create a progress report as the REPORT.md.

### Phase 2: CLI Tool

Add POJO generation support to `avro-to-json-cli` with `--generate-pojo`, `-p/--package`,
`--pojo-output`, and `--no-lombok` options. Both file input and registry input work with POJO generation.

### Phase 3: Shared LombokAnnotator

Move `LombokAnnotator` into `avro-to-json-core` module so it is shared by both
`avro-to-json-maven-plugin` and `avro-to-json-cli`. Add `jsonschema2pojo-core` as a
dependency of `avro-to-json-core`.
