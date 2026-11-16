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
 * The demo content: a used-vehicle OneDrive folder library (its files are in the repository's {@code data/onedrive/})
 * and five seeded conversations, the first being the Q2 2026 quarterly report. The figures in the conversations come from
 * those files ({@code data/scripts/analyze_demo_data.py} prints them). Ids are derived
 * from fixed keys so they are the same on every start.
 */
final class DemoData {

    static final FolderId F1 = new FolderId("f1");
    static final FolderId F2 = new FolderId("f2");
    static final FolderId F3 = new FolderId("f3");

    private static final Map<Folder, List<String>> LIBRARY = new LinkedHashMap<>();

    static {
        LIBRARY.put(new Folder(F1, "Used Vehicle Sales", "Used Vehicles / Reporting / Sales", 24), List.of(
                "Used_Vehicle_Sales_Q3_2026.csv", "Used_Vehicle_Sales_Q2_2026.csv"));
        LIBRARY.put(new Folder(F2, "Wholesale Pricing", "Used Vehicles / Pricing / Wholesale", 58), List.of(
                "Wholesale_Price_Index_Q3_2026.csv", "Wholesale_Price_Index_Q2_2026.csv",
                "Auction_Volumes_Q3_2026.csv"));
        LIBRARY.put(new Folder(F3, "Inventory and Days-to-Turn", "Used Vehicles / Inventory / Turn", 12), List.of(
                "Days_To_Turn_By_Segment_Q3_2026.csv", "Days_To_Turn_By_Segment_Q2_2026.csv"));
        LIBRARY.put(new Folder(new FolderId("f4"), "Loan Performance", "Finance / Auto Lending / Performance", 9),
                List.of("Auto_Loan_Performance_Q3_2026.csv", "Auto_Loan_Performance_Q2_2026.csv"));
        LIBRARY.put(new Folder(new FolderId("f5"), "Quarterly Reports", "Used Vehicles / Reporting / Quarterly", 31),
                List.of("Used_Car_Quarterly_Report_Q2_2026.md"));
        LIBRARY.put(new Folder(new FolderId("f6"), "Analyst Templates", "Shared / Used Vehicles / Templates", 7),
                List.of("Quarterly_Report_Template.md"));
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

    /**
     * Where a library file's contents are on the classpath: {@code demo-data/onedrive/<folder>/<file>}, copied from the
     * repository's {@code data/onedrive/} by the build ({@code copy-demo-data} in the pom). The folder's directory is
     * its name in lower case with dashes, so "Inventory and Days-to-Turn" is {@code inventory-and-days-to-turn}.
     */
    static Optional<String> resourcePath(String fileName) {
        return LIBRARY.entrySet().stream()
                .filter(entry -> entry.getValue().contains(fileName))
                .map(entry -> "demo-data/onedrive/" + directoryOf(entry.getKey()) + "/" + fileName)
                .findFirst();
    }

    static String directoryOf(Folder folder) {
        return folder.name().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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

        sessions.add(session("c1", "Used cars — Q2 2026 quarterly report", Location.LOCAL, now, List.of(
                user("c1", 1, "Write our Q2 2026 used-car quarterly report: volume, pricing, days to turn and anything "
                        + "that changes our view on auto-loan risk.",
                        "Used_Vehicle_Sales_Q2_2026.csv", "Wholesale_Price_Index_Q2_2026.csv",
                        "Days_To_Turn_By_Segment_Q2_2026.csv"),
                assistant("c1", 2,
                        List.of(read("OneDrive/Used Vehicle Sales/Used_Vehicle_Sales_Q2_2026.csv"),
                                read("OneDrive/Wholesale Pricing/Wholesale_Price_Index_Q2_2026.csv"),
                                read("OneDrive/Inventory and Days-to-Turn/Days_To_Turn_By_Segment_Q2_2026.csv"),
                                compute("weighted_average(units, avg_retail_price, wholesale_index)"),
                                compute("days_to_turn(by_segment)")),
                        List.of("Used-vehicle sales reached 612,000 units in Q2, building every month to 216,040 in "
                                        + "June. The average retail price was $28,938. Wholesale prices softened 1.3% "
                                        + "over the quarter on the composite index, led by electric vehicles at −5.2%.",
                                "For risk, the item to watch is turn time. The average vehicle took 43.7 days to sell; "
                                        + "sedans (52) and electric vehicles (58) are the slow segments, and 19% of "
                                        + "electric stock has been on the lot for more than 60 days. Falling wholesale "
                                        + "values also erode the collateral behind loans written at last year’s peak "
                                        + "prices."),
                        Optional.of(new ResultTable(List.of("Segment", "Units", "Avg retail price", "Wholesale QoQ",
                                "Days to turn"), List.of(
                                List.of("SUV", "231,000", "$30,420", "−0.8%", "39.0"),
                                List.of("Truck", "168,000", "$34,150", "−0.4%", "37.0"),
                                List.of("Sedan", "142,000", "$19,980", "−1.9%", "52.0"),
                                List.of("Electric", "71,000", "$29,700", "−5.2%", "58.0"))))),
                user("c1", 3, "Where is inventory aging the most?"),
                assistant("c1", 4,
                        List.of(read("OneDrive/Inventory and Days-to-Turn/Days_To_Turn_By_Segment_Q2_2026.csv"),
                                compute("rank(avg_days_to_turn, by=segment_region)")),
                        List.of("Electric vehicles hold the most aging stock: 19% of electric inventory is older than "
                                + "60 days, against 7% for trucks. The single slowest market is sedans in the Midwest "
                                + "at 61 days to turn, followed by electric vehicles in the Southwest (60.7) and the "
                                + "Northeast (60.1)."),
                        Optional.empty()))));

        sessions.add(session("c2", "Electric used-price softness", Location.CLOUD, earlierToday, List.of(
                user("c2", 1, "How far did used EV prices fall in Q2?", "Wholesale_Price_Index_Q2_2026.csv"),
                assistant("c2", 2,
                        List.of(read("OneDrive/Wholesale Pricing/Wholesale_Price_Index_Q2_2026.csv")),
                        List.of("The electric wholesale index fell 5.2% in Q2, from 156.2 to 148.1, and is 14.8% below "
                                + "a year ago. The average electric auction price closed the quarter at $22,510. No "
                                + "other segment fell by more than 1.9%."),
                        Optional.empty()))));

        sessions.add(session("c3", "Subprime auto delinquency watch", Location.LOCAL,
                now.minus(Duration.ofDays(2)), List.of(
                        user("c3", 1, "Check 30 and 60 day delinquencies on the used-vehicle loan book.",
                                "Auto_Loan_Performance_Q2_2026.csv", "Used_Vehicle_Sales_Q2_2026.csv"),
                        assistant("c3", 2,
                                List.of(read("OneDrive/Loan Performance/Auto_Loan_Performance_Q2_2026.csv"),
                                        read("OneDrive/Used Vehicle Sales/Used_Vehicle_Sales_Q2_2026.csv"),
                                        compute("delinquency_rate(30d, 60d, by=cohort, tier)")),
                                List.of("30-day delinquency on the auto-loan book is 3.90% and 60-day is 1.60%. Subprime "
                                        + "loans run at 12.66% 30+. The 2025-Q1 and 2025-Q2 cohorts, written at peak "
                                        + "prices, are the worst at about 6.2%, and subprime loans from 2025-Q2 and "
                                        + "2025-Q3 now owe more than the vehicle is worth (current loan-to-value about "
                                        + "104%)."),
                                Optional.empty()))));

        sessions.add(session("c4", "Wholesale-to-retail spread by segment", Location.CLOUD,
                now.minus(Duration.ofDays(4)), List.of(
                        user("c4", 1, "Compare the gap between retail and auction prices by segment at the end of Q2.",
                                "Wholesale_Price_Index_Q2_2026.csv", "Used_Vehicle_Sales_Q2_2026.csv"),
                        assistant("c4", 2,
                                List.of(read("OneDrive/Wholesale Pricing/Wholesale_Price_Index_Q2_2026.csv"),
                                        read("OneDrive/Used Vehicle Sales/Used_Vehicle_Sales_Q2_2026.csv")),
                                List.of("The gap between the average retail price and the closing auction price is "
                                        + "widest for electric vehicles ($7,190) and SUVs ($6,190), against about $4,850 "
                                        + "for trucks and sedans. Retail EV prices have not yet followed the wholesale "
                                        + "decline."),
                                Optional.empty()))));

        sessions.add(session("c5", "Days-to-turn bridge Q1 → Q2", Location.LOCAL,
                now.minus(Duration.ofDays(20)), List.of(
                        user("c5", 1, "Build a days-to-turn bridge by segment from Q1 to Q2."),
                        assistant("c5", 2, List.of(),
                                List.of("Share the Q1 and Q2 days-to-turn files and I’ll build the bridge by segment, "
                                        + "separating mix, seasonality and pricing effects."),
                                Optional.empty()))));
        return sessions;
    }

    private static Session session(String key, String title, Location location, Instant updatedAt,
                                   List<Message> messages) {
        List<Message> dated = messages.stream()
                .map(m -> Message.rehydrate(m.id(), m.role(), m.text(), m.files(), m.steps(), m.paragraphs(),
                        m.table(), m.notice(), updatedAt))
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
