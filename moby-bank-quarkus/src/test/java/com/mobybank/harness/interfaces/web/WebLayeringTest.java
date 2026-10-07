package com.mobybank.harness.interfaces.web;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the ddd-foundations rule for the HTML layer: it depends on the application layer only, never on the
 * domain, the infrastructure, persistence, or the JSON layer next to it.
 */
class WebLayeringTest {

    private static final Path SOURCES = Path.of("src/main/java/com/mobybank/harness/interfaces/web");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "^import (static )?(com\\.mobybank\\.harness\\.(infrastructure|domain|interfaces\\.rest)|jakarta\\.persistence)\\..*");

    @Test
    void webDependsOnTheApplicationLayerOnly() throws IOException {
        for (Path source : javaFiles()) {
            for (String line : Files.readAllLines(source)) {
                assertFalse(FORBIDDEN.matcher(line).matches(), source.getFileName() + " imports a forbidden layer: " + line);
            }
        }
    }

    @Test
    void webNeverTouchesARepository() throws IOException {
        for (Path source : javaFiles()) {
            assertFalse(Files.readString(source).contains("Repository"), source.getFileName() + " must not touch a repository");
        }
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.list(SOURCES)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "no web sources found; is the working directory the module root?");
            return sources;
        }
    }
}
