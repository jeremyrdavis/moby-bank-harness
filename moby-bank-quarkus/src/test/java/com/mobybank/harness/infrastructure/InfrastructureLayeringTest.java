package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Infrastructure implements the ports of the inner layers; it must never reach out to the REST layer. */
class InfrastructureLayeringTest {

    private static final Path SOURCES = Path.of("src/main/java/com/mobybank/harness/infrastructure");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "^import (static )?(com\\.mobybank\\.harness\\.interfaces\\.|jakarta\\.ws\\.rs\\.).*");

    @Test
    void infrastructureDoesNotDependOnTheRestLayer() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.list(SOURCES)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(sources.isEmpty(), "no infrastructure sources found; is the working directory the module root?");
        for (Path source : sources) {
            for (String line : Files.readAllLines(source)) {
                assertFalse(FORBIDDEN.matcher(line).matches(), source.getFileName() + " has a forbidden import: " + line);
            }
        }
    }
}
