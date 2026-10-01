package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the ddd-foundations rules for the application layer: it may use the domain and CDI, but never the
 * infrastructure or REST layers, nor persistence or JAX-RS types. Application services are named
 * *ApplicationService.
 */
class ApplicationLayeringTest {

    private static final Path SOURCES = Path.of("src/main/java/com/mobybank/harness/application");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "^import (static )?(com\\.mobybank\\.harness\\.(infrastructure|interfaces)\\.|jakarta\\.ws\\.rs\\.|jakarta\\.persistence\\.).*");

    @Test
    void applicationDoesNotDependOnInfrastructureOrInterfaces() throws IOException {
        for (Path source : javaFiles()) {
            for (String line : Files.readAllLines(source)) {
                assertFalse(FORBIDDEN.matcher(line).matches(), source.getFileName() + " has a forbidden import: " + line);
            }
        }
    }

    @Test
    void serviceClassesCarryTheApplicationQualifier() throws IOException {
        for (Path source : javaFiles()) {
            String name = source.getFileName().toString();
            if (name.endsWith("Service.java")) {
                assertTrue(name.endsWith("ApplicationService.java"), name + " must be an *ApplicationService");
            }
        }
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.list(SOURCES)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "no application sources found; is the working directory the module root?");
            return sources;
        }
    }
}
