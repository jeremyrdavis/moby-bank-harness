package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.MessageId;
import com.mobybank.harness.domain.MessageRole;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionStatus;
import com.mobybank.harness.domain.SessionTitle;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The demo content from the prototype: the OneDrive folder library and five seeded conversations. Ids are derived
 * from fixed keys so they are the same on every start.
 */
final class DemoData {

    static final FolderId F1 = new FolderId("f1");
    static final FolderId F2 = new FolderId("f2");
    static final FolderId F3 = new FolderId("f3");

    private static final Map<Folder, List<String>> LIBRARY = new LinkedHashMap<>();

    static {
        LIBRARY.put(new Folder(F1, "Earnings 2026", "Credit Research / Coverage / Earnings 2026", 24), List.of(
                "Fathom_Q2_2026_10-Q.pdf", "Fathom_Q2_2026_Call_Transcript.docx",
                "Kestrel_Q2_2026_Earnings_Release.pdf"));
        LIBRARY.put(new Folder(F2, "Client Financials", "Corporate Banking / Clients / Financials", 58), List.of(
                "Harborline_Logistics_FY25_Audited.pdf", "Harborline_Logistics_Q2_2026_Mgmt_Accounts.xlsx"));
        LIBRARY.put(new Folder(F3, "Peer Comps", "Credit Research / Models / Peer Comps", 12), List.of(
                "Industrials_Peer_Comps_Q2.xlsx", "Regional_Banks_NIM_Tracker.xlsx"));
        LIBRARY.put(new Folder(new FolderId("f4"), "Covenant Models", "Risk / Credit / Covenant Models", 9),
                List.of("Harborline_Covenant_Model_v3.xlsx"));
        LIBRARY.put(new Folder(new FolderId("f5"), "ALM Reports", "Treasury / ALM / Monthly", 31),
                List.of("ALM_Aug_2026.pdf"));
        LIBRARY.put(new Folder(new FolderId("f6"), "Analyst Templates", "Shared / Credit Research / Templates", 7),
                List.of("Earnings_Summary_Template.docx"));
    }

    private DemoData() {
    }

    static List<Folder> folders() {
        return List.copyOf(LIBRARY.keySet());
    }

    static List<CatalogFile> files(FolderId folderId) {
        return LIBRARY.entrySet().stream()
                .filter(entry -> entry.getKey().id().equals(folderId))
                .flatMap(entry -> entry.getValue().stream().map(name -> new CatalogFile(folderId, name)))
                .toList();
    }

    /** The folders connected when the demo starts. */
    static List<FolderId> initiallyConnected() {
        return List.of(F1, F2, F3);
    }

    /** The five seeded conversations, dated relative to now so they fall into the prototype's history groups. */
    static List<Session> sessions(Clock clock) {
        Instant now = clock.instant();
        ZonedDateTime startOfToday = now.atZone(clock.getZone()).toLocalDate().atStartOfDay(clock.getZone());
        Instant earlierToday = startOfToday.toInstant().plus(Duration.between(startOfToday.toInstant(), now).dividedBy(2));
        List<Session> sessions = new ArrayList<>();

        sessions.add(session("c1", "Fathom Industrial — Q2 2026 earnings", Location.LOCAL, now, List.of(
                user("c1", 1, "Summarize Fathom’s Q2 results against consensus and flag anything that changes our credit view.",
                        "Fathom_Q2_2026_10-Q.pdf", "Fathom_Q2_2026_Call_Transcript.docx",
                        "Industrials_Peer_Comps_Q2.xlsx"),
                assistant("c1", 2,
                        List.of(read("OneDrive/Earnings 2026/Fathom_Q2_2026_10-Q.pdf"),
                                read("OneDrive/Earnings 2026/Fathom_Q2_2026_Call_Transcript.docx"),
                                read("OneDrive/Peer Comps/Industrials_Peer_Comps_Q2.xlsx"),
                                compute("variance_vs_consensus(revenue, ebitda, eps)"),
                                compute("leverage(net_debt, ltm_ebitda)")),
                        List.of("Fathom beat on revenue but missed on margin. Revenue of $4.12B was 2.1% above consensus, "
                                        + "driven by aftermarket services. Adjusted EBITDA margin fell 80 bps year over year to "
                                        + "17.4% on input costs and a one-time restructuring charge in the Fluid Systems segment.",
                                "For credit, the main change is leverage. Net debt rose $310M after the Dunmore acquisition "
                                        + "closed in May, taking net leverage to 2.9x from 2.4x. Management reiterated a 2.5x "
                                        + "target by year-end 2027 on the call, but free cash flow guidance was cut by $120M."),
                        Optional.of(new ResultTable(List.of("Metric", "Q2 2026", "Consensus", "Q2 2025"), List.of(
                                List.of("Revenue", "$4.12B", "$4.04B", "$3.87B"),
                                List.of("Adj. EBITDA margin", "17.4%", "18.0%", "18.2%"),
                                List.of("Adj. EPS", "$1.86", "$1.91", "$1.79"),
                                List.of("Net leverage", "2.9x", "—", "2.4x"))))),
                user("c1", 3, "How does 2.9x compare with peers?"),
                assistant("c1", 4,
                        List.of(read("OneDrive/Peer Comps/Industrials_Peer_Comps_Q2.xlsx"),
                                compute("rank(net_leverage, peer_set=7)")),
                        List.of("Fathom is now fifth of seven in the peer set on net leverage. The peer median is 2.3x, "
                                + "and only Ormond (3.4x) and Calder (3.1x) are higher. Before the acquisition Fathom "
                                + "ranked second."),
                        Optional.empty()))));

        sessions.add(session("c2", "Kestrel Foods — earnings release review", Location.CLOUD, earlierToday, List.of(
                user("c2", 1, "Pull the key numbers from Kestrel’s Q2 release.", "Kestrel_Q2_2026_Earnings_Release.pdf"),
                assistant("c2", 2,
                        List.of(read("OneDrive/Earnings 2026/Kestrel_Q2_2026_Earnings_Release.pdf")),
                        List.of("Net sales of $1.38B were flat year over year; organic volume declined 1.2%, offset by "
                                + "price. Gross margin expanded 60 bps to 31.9%. Full-year EPS guidance was narrowed to "
                                + "$2.40–$2.48."),
                        Optional.empty()))));

        sessions.add(session("c3", "Harborline Logistics — cash flow vs covenants", Location.LOCAL,
                now.minus(Duration.ofDays(2)), List.of(
                        user("c3", 1, "Check Harborline’s Q2 management accounts against the fixed charge coverage covenant.",
                                "Harborline_Logistics_Q2_2026_Mgmt_Accounts.xlsx", "Harborline_Covenant_Model_v3.xlsx"),
                        assistant("c3", 2,
                                List.of(read("OneDrive/Client Financials/Harborline_Logistics_Q2_2026_Mgmt_Accounts.xlsx"),
                                        read("OneDrive/Covenant Models/Harborline_Covenant_Model_v3.xlsx"),
                                        compute("fccr(ltm)")),
                                List.of("LTM fixed charge coverage is 1.34x against a 1.25x minimum, down from 1.48x last "
                                        + "quarter. Headroom is about 7%. The decline comes mainly from higher lease costs "
                                        + "on the two new depots."),
                                Optional.empty()))));

        sessions.add(session("c4", "Regional banks — NIM trend comparison", Location.CLOUD,
                now.minus(Duration.ofDays(4)), List.of(
                        user("c4", 1, "Compare net interest margin trends for our regional bank coverage over the last four quarters.",
                                "Regional_Banks_NIM_Tracker.xlsx"),
                        assistant("c4", 2,
                                List.of(read("OneDrive/Peer Comps/Regional_Banks_NIM_Tracker.xlsx")),
                                List.of("Median NIM compressed 14 bps over four quarters to 3.21%. Deposit repricing slowed "
                                        + "in Q2, and three of nine banks reported sequential expansion."),
                                Optional.empty()))));

        sessions.add(session("c5", "Segment revenue bridge FY25 → FY26", Location.LOCAL,
                now.minus(Duration.ofDays(20)), List.of(
                        user("c5", 1, "Build a segment revenue bridge for Fathom from FY25 to FY26 guidance."),
                        assistant("c5", 2, List.of(),
                                List.of("Share the FY25 10-K and the latest guidance deck and I’ll build the bridge by "
                                        + "segment, separating organic growth, acquisitions and FX."),
                                Optional.empty()))));
        return sessions;
    }

    private static Session session(String key, String title, Location location, Instant updatedAt,
                                   List<Message> messages) {
        List<Message> dated = messages.stream()
                .map(m -> Message.rehydrate(m.id(), m.role(), m.text(), m.files(), m.steps(), m.paragraphs(),
                        m.table(), updatedAt))
                .toList();
        return Session.rehydrate(new SessionId(uuid("session-" + key)), new SessionTitle(title), location,
                SessionStatus.IDLE, null, dated, updatedAt.minus(Duration.ofMinutes(5)), updatedAt, 0L);
    }

    private static Message user(String key, int n, String text, String... fileNames) {
        List<FileRef> files = java.util.Arrays.stream(fileNames)
                .map(name -> new FileRef(name, FileSource.ONEDRIVE)).toList();
        return Message.rehydrate(new MessageId(uuid("message-" + key + "-" + n)), MessageRole.USER, text, files,
                List.of(), List.of(), Optional.empty(), Instant.EPOCH);
    }

    private static Message assistant(String key, int n, List<Step> steps, List<String> paragraphs,
                                     Optional<ResultTable> table) {
        return Message.rehydrate(new MessageId(uuid("message-" + key + "-" + n)), MessageRole.ASSISTANT, "",
                List.of(), steps, paragraphs, table, Instant.EPOCH);
    }

    private static Step read(String label) {
        return new Step(StepKind.READ, label);
    }

    private static Step compute(String label) {
        return new Step(StepKind.COMPUTE, label);
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("moby-bank-demo/" + key).getBytes(StandardCharsets.UTF_8));
    }
}
