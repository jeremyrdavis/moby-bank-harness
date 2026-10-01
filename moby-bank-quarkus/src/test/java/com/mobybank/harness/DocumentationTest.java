package com.mobybank.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the written documentation (README.md and docs/*.md) in step with the code. It cannot judge whether the prose is
 * right, but it catches the ways documents rot: a renamed class, a removed test, a setting that was added or dropped,
 * a dead link, an endpoint nobody documented.
 *
 * <p>Names written in backticks are checked when they have the shape of a class ({@code SessionRepository}), a member
 * ({@code Session.start}), a camelCase method, field or test name ({@code postUserMessage}), or a source path.
 */
class DocumentationTest {

    private static final Path MODULE = Path.of(".");
    private static final Path REPO = MODULE.resolve("..").normalize();
    private static final Path DOCS = MODULE.resolve("docs");
    private static final Path MAIN = MODULE.resolve("src/main/java/com/mobybank/harness");
    private static final Path TEST = MODULE.resolve("src/test/java/com/mobybank/harness");

    /** Words in backticks that are prose or other tools' names, not code in this module. */
    private static final Set<String> NOT_CODE = Set.of(
            // the word itself, in a sentence about what the test forbids
            "Repository",
            // the UI module's and the JDK's names, mentioned in passing
            "EventSource", "HttpClient", "ObjectMapper", "ProcessBuilder", "CompletableFuture",
            // Java and Jakarta types that are not imported anywhere in this module's sources
            "IllegalStateException", "IllegalArgumentException", "RuntimeException", "NullPointerException",
            // product and tool names, and an HTTP header
            "JavaScript", "OpenAPI", "GraphQL", "SmallRye", "Quarkus", "JUnit", "RestAssured", "Mermaid",
            "Authorization",
            // the group labels the API returns (the UI's wording, not code)
            "Today", "Earlier",
            // proposed in an onboarding exercise; does not exist yet
            "messageCount",
            // JSON property names and UI state that are not declared in this module's Java
            "moveTarget", "onedrive", "sessionId", "messageId", "folderIds", "folderId", "folderName", "folderPath",
            "fileCount", "createdAt", "updatedAt");

    /**
     * Documents that describe work that has not been done. They name classes, settings and fields that do not exist
     * yet, so only their "Current state" section (the part that describes the code as it is) is checked against the
     * source. Links are checked everywhere.
     */
    private static final Set<String> PLAN_DOCUMENTS = Set.of("ONEDRIVE_INTEGRATION.md");

    /** Settings the docs mention that do not exist yet: a proposal in an onboarding exercise. */
    private static final Set<String> PROPOSED_SETTINGS = Set.of("harness.sbx.remove-source-after-move");

    // --- links -------------------------------------------------------------------------------------------------

    @Test
    void everyRelativeLinkPointsAtSomethingThatExists() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern link = Pattern.compile("\\]\\(([^)\\s#]+)(?:#[^)]*)?\\)");
        for (Path doc : documents()) {
            Matcher matcher = link.matcher(Files.readString(doc));
            while (matcher.find()) {
                String target = matcher.group(1);
                if (target.matches("^[a-z][a-z0-9+.-]*:.*")) {
                    continue; // http:, https:, mailto:
                }
                if (!Files.exists(doc.getParent().resolve(target).normalize())) {
                    problems.add(doc + " links to " + target + ", which does not exist");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    // --- names in code spans -----------------------------------------------------------------------------------

    @Test
    void namesInBackticksExistInTheSource() throws IOException {
        Corpus corpus = new Corpus();
        List<String> problems = new ArrayList<>();
        Pattern span = Pattern.compile("`([^`\\n]+)`");
        for (Path doc : documents()) {
            Matcher matcher = span.matcher(checkableText(doc));
            while (matcher.find()) {
                String problem = check(matcher.group(1).strip(), corpus);
                if (problem != null) {
                    problems.add(doc.getFileName() + ": `" + matcher.group(1) + "` " + problem);
                }
            }
        }
        assertEquals(List.of(), problems.stream().distinct().toList());
    }

    private static String check(String token, Corpus corpus) {
        String bare = token.replaceFirst("\\(.*\\)$", "").replaceFirst("\\(.*$", "").strip();
        if (NOT_CODE.contains(bare) || token.contains("...") || token.contains("…")
                || token.contains(" ") && !bare.matches("[A-Za-z0-9_.#]+")) {
            return null;
        }
        if (bare.matches("[A-Za-z0-9_./-]+\\.(java|md|yaml|properties|html)") && !bare.startsWith("..")) {
            return corpus.fileExists(bare) ? null : "is not a file in this module";
        }
        if (bare.matches("[A-Z][A-Za-z0-9]*[a-z][A-Za-z0-9]*")) {
            // a type, or a name the code handles as a string literal (the agent's tool names, "Read", "Glob", ...)
            return corpus.types.contains(bare) || corpus.quoted(bare)
                    ? null
                    : "is not a class, interface, record or enum in the source, nor a string the code uses";
        }
        Matcher member = Pattern.compile("([A-Z][A-Za-z0-9]*[a-z][A-Za-z0-9]*)[.#]([a-z][A-Za-z0-9]*)").matcher(bare);
        if (member.matches()) {
            String type = member.group(1);
            String name = member.group(2);
            if (!corpus.types.contains(type)) {
                return "names a type, " + type + ", that is not in the source";
            }
            return corpus.typeMentions(type, name) ? null : "names " + name + ", which " + type + " does not contain";
        }
        if (bare.matches("[a-z][a-z0-9]*[A-Z][A-Za-z0-9]*")) {
            return corpus.word(bare) ? null : "is not used anywhere in the source";
        }
        return null;
    }

    // --- configuration ----------------------------------------------------------------------------------------

    @Test
    void configurationMdListsExactlyTheSettingsTheCodeReads() throws IOException {
        Set<String> read = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = Pattern.compile("@ConfigProperty\\(name = \"(harness\\.[^\"]+)\"").matcher(Files.readString(file));
                while (m.find()) {
                    read.add(m.group(1));
                }
            }
        }
        assertFalse(read.isEmpty(), "found no @ConfigProperty settings; is the working directory the module root?");

        Set<String> documented = keysIn(Files.readString(DOCS.resolve("configuration.md")));
        assertEquals(List.of(), read.stream().filter(k -> !documented.contains(k)).toList(),
                "settings the code reads but configuration.md does not document");
        assertEquals(List.of(), documented.stream().filter(k -> !read.contains(k)).toList(),
                "settings configuration.md documents but the code does not read");
    }

    @Test
    void everySettingMentionedAnywhereInTheDocsExists() throws IOException {
        Set<String> real = keysIn(Files.readString(DOCS.resolve("configuration.md")));
        List<String> unknown = new ArrayList<>();
        for (Path doc : documents()) {
            for (String key : keysIn(checkableText(doc))) {
                if (!real.contains(key) && !PROPOSED_SETTINGS.contains(key)) {
                    unknown.add(doc.getFileName() + " mentions " + key + ", which is not a setting");
                }
            }
        }
        assertEquals(List.of(), unknown);
    }

    /** {@code harness.some.key} occurrences, leaving out wildcards like {@code harness.fake.*} and {@code harness.*}. */
    private static Set<String> keysIn(String text) {
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("(?<![A-Za-z0-9_.])harness\\.[a-z][a-z0-9.-]*[a-z0-9](?![a-z0-9.*-])").matcher(text);
        while (m.find()) {
            keys.add(m.group());
        }
        return keys;
    }

    // --- the API ----------------------------------------------------------------------------------------------

    @Test
    void apiMdDocumentsExactlyTheEndpointsInOpenapiYaml() throws IOException {
        Set<String> inContract = new TreeSet<>();
        String path = null;
        for (String line : Files.readAllLines(MODULE.resolve("openapi.yaml"))) {
            Matcher p = Pattern.compile("^  (/api/\\S+):$").matcher(line);
            if (p.matches()) {
                path = p.group(1);
                continue;
            }
            Matcher op = Pattern.compile("^    (get|post|put|patch|delete):$").matcher(line);
            if (path != null && op.matches()) {
                inContract.add(op.group(1).toUpperCase() + " " + path);
            }
        }
        assertFalse(inContract.isEmpty(), "found no operations in openapi.yaml");

        Set<String> documented = new TreeSet<>();
        Matcher d = Pattern.compile("(GET|POST|PUT|PATCH|DELETE) (/api/[A-Za-z0-9/{}_-]+)")
                .matcher(Files.readString(DOCS.resolve("api.md")));
        while (d.find()) {
            documented.add(d.group(1) + " " + d.group(2));
        }
        assertEquals(List.of(), inContract.stream().filter(e -> !documented.contains(e)).toList(),
                "endpoints in openapi.yaml that api.md does not document");
        assertEquals(List.of(), documented.stream().filter(e -> !inContract.contains(e)).toList(),
                "endpoints api.md documents that are not in openapi.yaml");
    }

    // --- the docs folder itself --------------------------------------------------------------------------------

    @Test
    void theExpectedDocumentsExistAndTheJavadocWasGenerated() {
        for (String name : List.of("README.md", "ONBOARDING.md", "architecture.md", "domain-model.md", "api.md",
                "configuration.md", "integrations.md", "testing.md", "ONEDRIVE_INTEGRATION.md")) {
            assertTrue(Files.exists(DOCS.resolve(name)), "docs/" + name + " is missing");
        }
        assertTrue(Files.exists(DOCS.resolve("apidocs/index.html")),
                "docs/apidocs/index.html is missing; run ./mvnw javadoc:javadoc");
    }

    @Test
    void theOnboardingGuideCoversEveryStopAndTheReviewChecklist() throws IOException {
        String onboarding = Files.readString(DOCS.resolve("ONBOARDING.md"));
        for (int stop = 1; stop <= 13; stop++) {
            assertTrue(onboarding.contains("### Stop " + stop + ":"), "ONBOARDING.md lacks Stop " + stop);
        }
        for (String heading : List.of("## 5. Reviewing a pull request here", "## 6. Known rough edges", "## 7. Your first changes")) {
            assertTrue(onboarding.contains(heading), "ONBOARDING.md lacks " + heading);
        }
    }

    @Test
    void aPlanDocumentHasACurrentStateSectionSoTheCheckIsNotSilentlyEmpty() throws IOException {
        for (String name : PLAN_DOCUMENTS) {
            String section = checkableText(DOCS.resolve(name));
            assertTrue(section.length() > 500, name + " needs a substantial '## … Current state …' section: "
                    + "it is the only part of a plan document that is checked against the code");
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    /**
     * The text of a document that is checked against the code: all of it, except for a plan document, where it is only
     * the section whose heading contains "Current state".
     */
    private static String checkableText(Path doc) throws IOException {
        String text = Files.readString(doc);
        if (!PLAN_DOCUMENTS.contains(doc.getFileName().toString())) {
            return text;
        }
        StringBuilder section = new StringBuilder();
        boolean inside = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("## ")) {
                inside = line.contains("Current state");
            }
            if (inside) {
                section.append(line).append('\n');
            }
        }
        return section.toString();
    }

    private static List<Path> documents() throws IOException {
        List<Path> docs = new ArrayList<>();
        docs.add(MODULE.resolve("README.md"));
        try (Stream<Path> files = Files.list(DOCS)) {
            docs.addAll(files.filter(p -> p.toString().endsWith(".md")).sorted().toList());
        }
        assertTrue(docs.size() > 1, "no documents found in docs/; is the working directory the module root?");
        return docs;
    }

    /** Everything the docs may name: the types declared in the sources, and the words used in them. */
    private static final class Corpus {

        final Set<String> types = new TreeSet<>();
        private final String all;
        private final List<Path> sources;

        Corpus() throws IOException {
            sources = new ArrayList<>();
            for (Path root : List.of(MAIN, TEST)) {
                try (Stream<Path> files = Files.walk(root)) {
                    sources.addAll(files.filter(p -> p.toString().endsWith(".java")).toList());
                }
            }
            StringBuilder text = new StringBuilder();
            Pattern declaration = Pattern.compile("\\b(?:class|interface|record|enum)\\s+([A-Z][A-Za-z0-9_]*)");
            Pattern imported = Pattern.compile("^import (?:static )?[\\w.]*\\.([A-Z][A-Za-z0-9_]*);", Pattern.MULTILINE);
            for (Path source : sources) {
                String content = Files.readString(source);
                text.append(content).append('\n');
                Matcher d = declaration.matcher(content);
                while (d.find()) {
                    types.add(d.group(1));
                }
                Matcher i = imported.matcher(content);
                while (i.find()) {
                    types.add(i.group(1));
                }
            }
            text.append(Files.readString(MODULE.resolve("src/main/resources/application.properties")));
            all = text.toString();
        }

        /** Whether the code contains the name as a string literal. */
        boolean quoted(String name) {
            return all.contains("\"" + name + "\"");
        }

        boolean word(String name) {
            return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(all).find();
        }

        /** Whether {@code name} appears in the file that declares {@code type} (or, for a nested type, anywhere). */
        boolean typeMentions(String type, String name) {
            Pattern wanted = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            List<Path> declaring = sources.stream()
                    .filter(p -> p.getFileName().toString().equals(type + ".java"))
                    .collect(Collectors.toList());
            if (declaring.isEmpty()) {
                return wanted.matcher(all).find();
            }
            return declaring.stream().anyMatch(p -> {
                try {
                    return wanted.matcher(Files.readString(p)).find();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        boolean fileExists(String path) {
            for (Path base : List.of(MODULE, DOCS, MAIN, TEST, REPO, MODULE.resolve("src/main/resources"))) {
                if (Files.exists(base.resolve(path))) {
                    return true;
                }
            }
            if (path.contains("/")) {
                return false;
            }
            // a bare file name: look for it anywhere in the repository (skipping build output and dependencies)
            try (Stream<Path> files = Files.walk(REPO)) {
                return files.filter(p -> p.getFileName().toString().equals(path))
                        .anyMatch(p -> !p.toString().contains("/target/") && !p.toString().contains("/.git/")
                                && !p.toString().contains("/node_modules/"));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
