package com.mobybank.harness.domain;

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
 * Enforces the ddd-foundations rule that the domain depends on nothing but the JDK and itself: no Jakarta, Quarkus
 * or other framework imports, and no imports from the application, infrastructure or interfaces layers.
 */
class DomainLayeringTest {

    private static final Path DOMAIN_SOURCES = Path.of("src/main/java/com/mobybank/harness/domain");

    private static final Pattern ALLOWED_IMPORT = Pattern.compile("^import (static )?(java\\.|com\\.mobybank\\.harness\\.domain\\.).*");

    @Test
    void domainImportsOnlyTheJdkAndItself() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.list(DOMAIN_SOURCES)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(sources.isEmpty(), "no domain sources found; is the working directory the module root?");

        for (Path source : sources) {
            for (String line : Files.readAllLines(source)) {
                if (line.startsWith("import ")) {
                    assertTrue(ALLOWED_IMPORT.matcher(line).matches(),
                            source.getFileName() + " has a forbidden import: " + line);
                }
            }
        }
    }

    @Test
    void serviceClassesAreNotNamedWithoutALayerQualifier() throws IOException {
        try (Stream<Path> files = Files.list(DOMAIN_SOURCES)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith("Service.java"))
                    .forEach(name -> assertTrue(name.endsWith("DomainService.java"),
                            name + " must be a *DomainService in the domain layer"));
        }
    }
}
