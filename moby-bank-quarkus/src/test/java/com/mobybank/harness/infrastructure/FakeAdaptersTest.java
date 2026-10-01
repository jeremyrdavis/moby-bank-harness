package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
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

    @Test
    void fakeModeSelectsTheFakes() {
        assertInstanceOf(FakeSandboxAgent.class, Adapters.selectAgent("fake", Duration.ZERO));
        assertInstanceOf(FakeSandboxTransfer.class, Adapters.selectTransfer("fake", Duration.ZERO, Duration.ZERO));
        assertInstanceOf(FakeDocumentCatalog.class, Adapters.selectCatalog("fake"));
    }

    @Test
    void anUnsupportedModeFailsFastAndNamesTheProperty() {
        IllegalStateException agent = assertThrows(IllegalStateException.class,
                () -> Adapters.selectAgent("sbx", Duration.ZERO));
        assertTrue(agent.getMessage().contains("harness.sandbox.mode=sbx"));
        assertThrows(IllegalStateException.class, () -> Adapters.selectTransfer("nope", Duration.ZERO, Duration.ZERO));
        IllegalStateException catalog = assertThrows(IllegalStateException.class, () -> Adapters.selectCatalog("graph"));
        assertTrue(catalog.getMessage().contains("harness.documents.mode=graph"));
    }
}
