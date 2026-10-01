package com.mobybank.harness.interfaces.rest;

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
 * Enforces the ddd-foundations rule that the REST layer depends on the application layer only. The one exception is
 * the exception mapper, which has to name the domain's exception types to turn them into HTTP errors.
 */
class RestLayeringTest {

    private static final Path SOURCES = Path.of("src/main/java/com/mobybank/harness/interfaces/rest");

    private static final Pattern INFRASTRUCTURE = Pattern.compile("^import (static )?com\\.mobybank\\.harness\\.infrastructure\\..*");
    private static final Pattern DOMAIN = Pattern.compile("^import (static )?com\\.mobybank\\.harness\\.domain\\.(.*)");
    private static final Pattern PERSISTENCE = Pattern.compile("^import (static )?jakarta\\.persistence\\..*");

    @Test
    void restDependsOnTheApplicationLayerOnly() throws IOException {
        for (Path source : javaFiles()) {
            boolean isMapper = source.getFileName().toString().equals("ApiExceptionMappers.java");
            for (String line : Files.readAllLines(source)) {
                assertFalse(INFRASTRUCTURE.matcher(line).matches(), source.getFileName() + " imports infrastructure: " + line);
                assertFalse(PERSISTENCE.matcher(line).matches(), source.getFileName() + " imports persistence: " + line);
                var domain = DOMAIN.matcher(line);
                if (domain.matches()) {
                    assertTrue(isMapper && domain.group(2).endsWith("Exception;"),
                            source.getFileName() + " imports a domain type it should not: " + line);
                }
            }
        }
    }

    @Test
    void resourcesNeverInjectRepositoriesOrPorts() throws IOException {
        for (Path source : javaFiles()) {
            String text = Files.readString(source);
            assertFalse(text.contains("Repository"), source.getFileName() + " must not touch a repository");
        }
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.list(SOURCES)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "no REST sources found; is the working directory the module root?");
            return sources;
        }
    }
}
