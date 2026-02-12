package org.metalib.schema.avro.json.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.metalib.schema.avro.json.AvroToJsonSchemaConverter;
import org.metalib.schema.avro.json.ConverterOptions;
import org.metalib.schema.avro.json.JsonSchemaDraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Converts Avro schema files (.avsc) to JSON Schema files.
 *
 * <p>Scans the configured {@code sourceDirectory} for {@code .avsc} files and
 * writes the converted JSON Schema to the {@code outputDirectory}, preserving
 * the directory structure and replacing the {@code .avsc} extension with {@code .json}.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class AvroToJsonMojo extends AbstractMojo {

    /**
     * Directory containing Avro schema files (.avsc).
     */
    @Parameter(property = "avro-to-json.sourceDirectory", defaultValue = "${project.basedir}/src/main/avro")
    private File sourceDirectory;

    /**
     * Output directory for generated JSON Schema files.
     */
    @Parameter(property = "avro-to-json.outputDirectory", defaultValue = "${project.build.directory}/generated-resources/json-schema")
    private File outputDirectory;

    /**
     * Use strict JSON Schema mode (no POJO optimizations).
     */
    @Parameter(property = "avro-to-json.strict", defaultValue = "false")
    private boolean strict;

    /**
     * JSON Schema draft version: {@code draft-07} or {@code draft-2020-12}.
     */
    @Parameter(property = "avro-to-json.draft", defaultValue = "draft-07")
    private String draft;

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDirectory.isDirectory()) {
            getLog().info("Source directory does not exist, skipping: " + sourceDirectory);
            return;
        }

        JsonSchemaDraft schemaDraft = switch (draft) {
            case "draft-2020-12" -> JsonSchemaDraft.DRAFT_2020_12;
            default -> JsonSchemaDraft.DRAFT_07;
        };
        ConverterOptions options = (strict ? ConverterOptions.strict() : ConverterOptions.pojoOptimized())
                .withDraft(schemaDraft);
        AvroToJsonSchemaConverter converter = new AvroToJsonSchemaConverter(options);

        Path sourcePath = sourceDirectory.toPath();
        int count = 0;

        try (Stream<Path> paths = Files.walk(sourcePath)) {
            for (Path avscFile : paths.filter(p -> p.toString().endsWith(".avsc")).toList()) {
                Path relativePath = sourcePath.relativize(avscFile);
                String jsonFileName = relativePath.toString().replaceAll("\\.avsc$", ".json");
                Path outputPath = outputDirectory.toPath().resolve(jsonFileName);

                Files.createDirectories(outputPath.getParent());

                String avroSchema = Files.readString(avscFile);
                String jsonSchema = converter.convert(avroSchema);
                Files.writeString(outputPath, jsonSchema);

                getLog().info("Converted " + relativePath + " -> " + jsonFileName);
                count++;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to process Avro schema files", e);
        }

        if (count == 0) {
            getLog().info("No .avsc files found in " + sourceDirectory);
        } else {
            getLog().info("Converted " + count + " Avro schema(s) to JSON Schema");
        }
    }
}
