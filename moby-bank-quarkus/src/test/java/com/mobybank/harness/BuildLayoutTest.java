package com.mobybank.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;

/**
 * Guards a mistake that passes every other test and still breaks {@code quarkus:dev}. {@code index.html} lives at the
 * repository root, and listing that directory as a {@code <build><resources>} entry makes Quarkus dev mode (and the
 * Quarkus tests) copy the <em>whole</em> directory into {@code target/classes}, ignoring {@code <includes>}. That
 * copies the repository, {@code .git} included, and since the repository contains this module's own
 * {@code target/classes}, it nests without end until the path is too long.
 */
class BuildLayoutTest {

    @Test
    void noResourceDirectoryPointsAboveTheModule() throws Exception {
        var pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        NodeList directories = (NodeList) XPathFactory.newInstance().newXPath()
                .evaluate("/project/build/resources/resource/directory | /project/build/testResources/testResource/directory",
                        pom, XPathConstants.NODESET);

        for (int i = 0; i < directories.getLength(); i++) {
            String directory = directories.item(i).getTextContent().trim();
            assertFalse(directory.contains(".."),
                    "resource directory '" + directory + "' reaches outside the module; copy single files with the "
                            + "copy-index-page execution instead (see BuildLayoutTest)");
        }
    }

    @Test
    void thePageIsCopiedByAnExecutionThatTakesOnlyThatFile() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<id>copy-index-page</id>"), "the copy-index-page execution is missing");
        assertTrue(pom.contains("<include>index.html</include>"), "the copy must be limited to index.html");
    }

    @Test
    void theBuildOutputHoldsNoCopyOfTheRepository() {
        Path classes = Path.of("target/classes");
        if (!Files.isDirectory(classes)) {
            return; // nothing built yet
        }
        for (String stray : new String[] {".git", "moby-bank-quarkus", "moby-bank-prototype", "agent-os"}) {
            assertFalse(Files.exists(classes.resolve(stray)),
                    "target/classes/" + stray + " exists: the repository was copied into the build output; run ./mvnw clean");
        }
        assertEquals(true, Files.exists(classes.resolve("META-INF/resources/index.html")));
    }
}
