package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeAdaptersTest {

    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");

    private static AgentTurn turn(String text, FileRef... files) {
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        var message = session.postUserMessage(text, List.of(files), T0);
        return new AgentTurn(session.id(), Location.LOCAL, List.of(), message);
    }

    // --- agent -------------------------------------------------------------------------------------------------

    @Test
    void theAgentReadsEachFileThenComputesAndSummarizesInATable() {
        FakeSandboxAgent agent = new FakeSandboxAgent(Duration.ZERO);
        List<Step> streamed = new ArrayList<>();

        AgentReply reply = agent.runTurn(turn("go", new FileRef("a.pdf", FileSource.ONEDRIVE),
                new FileRef("b.xlsx", FileSource.UPLOAD)), streamed::add);

        assertEquals(List.of(StepKind.READ, StepKind.READ, StepKind.COMPUTE),
                reply.steps().stream().map(Step::kind).toList());
        assertEquals(reply.steps(), streamed, "steps are streamed in the same order as the final trace");
        assertEquals("a.pdf", reply.steps().get(0).label());
        assertTrue(reply.paragraphs().get(0).startsWith("I read a.pdf, b.xlsx and extracted"));
        assertEquals(List.of("Metric", "Current", "Prior year", "Change"), reply.table().orElseThrow().cols());
        assertEquals(3, reply.table().orElseThrow().rows().size());
    }

    @Test
    void withoutFilesTheAgentAsksForOneAndReturnsNoTable() {
        AgentReply reply = new FakeSandboxAgent(Duration.ZERO).runTurn(turn("hello"), step -> { });

        assertEquals(List.of(StepKind.COMPUTE), reply.steps().stream().map(Step::kind).toList());
        assertTrue(reply.paragraphs().get(0).contains("attach the documents directly"));
        assertTrue(reply.table().isEmpty());
    }

    @Test
    void theAgentTakesTheConfiguredTime() {
        FakeSandboxAgent agent = new FakeSandboxAgent(Duration.ofMillis(120));
        long start = System.nanoTime();
        agent.runTurn(turn("hello"), step -> { });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 100, "expected roughly the configured delay but took " + elapsedMs + " ms");
    }

    // --- transfer ----------------------------------------------------------------------------------------------

    @Test
    void movingToTheCloudReportsPackagingThenTransferring() {
        List<MoveProgress> progress = new ArrayList<>();
        new FakeSandboxTransfer(Duration.ZERO, Duration.ZERO).move(
                new MoveRequest(SessionId.fresh(), Location.LOCAL, Location.CLOUD, List.of(), List.of()),
                progress::add);

        assertEquals(List.of(MoveStage.PACKAGING, MoveStage.TRANSFERRING),
                progress.stream().map(MoveProgress::stage).toList());
        assertEquals("Packaging context · 0 messages · 0 files", progress.get(0).detail());
        assertEquals("Transferring files over private link · 0 messages · 0 files", progress.get(1).detail());
    }

    @Test
    void movingBackToLocalSaysSoAndCountsSingularly() {
        Session session = Session.start(SessionId.fresh(), Location.CLOUD, T0);
        var message = session.postUserMessage("hi", List.of(new FileRef("a.pdf", FileSource.UPLOAD)), T0);
        List<MoveProgress> progress = new ArrayList<>();

        new FakeSandboxTransfer(Duration.ZERO, Duration.ZERO).move(
                new MoveRequest(session.id(), Location.CLOUD, Location.LOCAL, List.of(message),
                        session.filesShared()),
                progress::add);

        assertEquals("Restoring the session on this machine · 1 message · 1 file", progress.get(1).detail());
    }

    // --- document catalog --------------------------------------------------------------------------------------

    @Test
    void theCatalogServesThePrototypesFolderLibrary() {
        FakeDocumentCatalog catalog = new FakeDocumentCatalog();
        List<Folder> folders = catalog.folders();

        assertEquals(List.of("Earnings 2026", "Client Financials", "Peer Comps", "Covenant Models", "ALM Reports",
                "Analyst Templates"), folders.stream().map(Folder::name).toList());
        assertEquals(24, folders.get(0).fileCount());

        List<CatalogFile> files = catalog.files(new FolderId("f1"));
        assertEquals(3, files.size());
        assertEquals("Fathom_Q2_2026_10-Q.pdf", files.get(0).name());
        assertTrue(catalog.files(new FolderId("missing")).isEmpty());
    }

    // --- adapter selection -------------------------------------------------------------------------------------

    private static final SandboxAgent SBX_AGENT = (turn, onStep) -> null;
    private static final SandboxTransfer SBX_TRANSFER = (request, onProgress) -> { };

    @Test
    void fakeModeSelectsTheFakesAndNeverBuildsTheSbxAdapters() {
        assertInstanceOf(FakeSandboxAgent.class, Adapters.selectAgent("fake", Duration.ZERO, () -> {
            throw new AssertionError("the sbx agent must not be built in fake mode");
        }));
        assertInstanceOf(FakeSandboxTransfer.class, Adapters.selectTransfer("fake", Duration.ZERO, Duration.ZERO, () -> {
            throw new AssertionError("the sbx transfer must not be built in fake mode");
        }));
        assertInstanceOf(FakeDocumentCatalog.class, Adapters.selectCatalog("fake", () -> {
            throw new AssertionError("the Graph catalog must not be built in fake mode");
        }));
    }

    @Test
    void graphModeSelectsTheGraphCatalog() {
        DocumentCatalog graph = new FakeDocumentCatalog();
        assertSame(graph, Adapters.selectCatalog("graph", () -> graph));
    }

    @Test
    void sbxModeSelectsTheSbxAdapters() {
        assertSame(SBX_AGENT, Adapters.selectAgent("sbx", Duration.ZERO, () -> SBX_AGENT));
        assertSame(SBX_TRANSFER, Adapters.selectTransfer("sbx", Duration.ZERO, Duration.ZERO, () -> SBX_TRANSFER));
    }

    @Test
    void anUnsupportedModeFailsFastAndNamesTheProperty() {
        IllegalStateException agent = assertThrows(IllegalStateException.class,
                () -> Adapters.selectAgent("docker", Duration.ZERO, () -> SBX_AGENT));
        assertTrue(agent.getMessage().contains("harness.sandbox.mode=docker"), agent.getMessage());
        assertTrue(agent.getMessage().contains("fake, sbx"), agent.getMessage());
        assertThrows(IllegalStateException.class,
                () -> Adapters.selectTransfer("nope", Duration.ZERO, Duration.ZERO, () -> SBX_TRANSFER));
        IllegalStateException catalog = assertThrows(IllegalStateException.class,
                () -> Adapters.selectCatalog("sharepoint", FakeDocumentCatalog::new));
        assertTrue(catalog.getMessage().contains("harness.documents.mode=sharepoint"), catalog.getMessage());
        assertTrue(catalog.getMessage().contains("fake, graph"), catalog.getMessage());
    }

    @Test
    void theAdapterProducersAreCreatedAtStartupSoABadConfigurationStopsTheApp() {
        List<String> producers = java.util.Arrays.stream(Adapters.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(jakarta.enterprise.inject.Produces.class))
                .filter(m -> m.getReturnType() != java.time.Clock.class)
                .filter(m -> !m.isAnnotationPresent(io.quarkus.runtime.Startup.class))
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertTrue(producers.isEmpty(), "these producers are lazy, so a misconfiguration would only show on first use: " + producers);
        assertEquals(3, java.util.Arrays.stream(Adapters.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(io.quarkus.runtime.Startup.class)).count());
    }

    @Test
    void theFakeCatalogServesPlaceholderContentForListedFilesOnly() {
        FakeDocumentCatalog catalog = new FakeDocumentCatalog();
        assertEquals("Demo content of Fathom_Q2_2026_10-Q.pdf\n",
                new String(catalog.fetch("Fathom_Q2_2026_10-Q.pdf").orElseThrow()));
        assertTrue(catalog.fetch("not-in-the-library.pdf").isEmpty());
    }
}
