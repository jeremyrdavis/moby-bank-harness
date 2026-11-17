package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.StepKind;
import com.mobybank.harness.infrastructure.sbx.SbxSandboxAgent;
import io.quarkus.logging.Log;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A stand-in for the sandbox agent that answers with a canned reply: it "reads" each attached file, "computes" one
 * calculation, and returns the Q3 2026 used-car quarterly report (a short summary plus a segment table) when files
 * were attached. Its figures are the ones the demo files give ({@code data/onedrive/}), so a real agent should reach
 * the same answer.
 * It lets the whole app run with no sandbox installed. It can also pretend a cloud sandbox was lost, to show the
 * analyst the notice that follows.
 */
public final class FakeSandboxAgent implements SandboxAgent {

    static final String COMPUTE_LABEL = "build_quarterly_report(used_sales, wholesale_prices, days_to_turn)";

    private final Duration delay;
    private final int cloudExpiryTurns;
    private final Map<SessionId, Integer> cloudTurns = new ConcurrentHashMap<>();

    public FakeSandboxAgent(Duration delay) {
        this(delay, 0);
    }

    /**
     * @param cloudExpiryTurns after this many cloud turns in a session, the next cloud turn pretends the sandbox had
     *                         gone and answers with the notice a real recovery shows; 0 never does
     */
    public FakeSandboxAgent(Duration delay, int cloudExpiryTurns) {
        if (cloudExpiryTurns < 0) {
            throw new IllegalArgumentException("harness.fake.cloud-expiry-turns must not be negative");
        }
        this.delay = delay;
        this.cloudExpiryTurns = cloudExpiryTurns;
    }

    @Override
    public AgentReply runTurn(AgentTurn turn, Consumer<Step> onStep) {
        List<FileRef> files = turn.userMessage().files();
        Log.debugf("Fake agent: turn for session %s at %s with %d files, delay %s", turn.sessionId().value(),
                turn.location(), files.size(), delay);
        List<Step> steps = new ArrayList<>();
        files.forEach(file -> steps.add(new Step(StepKind.READ, file.name())));
        steps.add(new Step(StepKind.COMPUTE, COMPUTE_LABEL));

        Duration slice = delay.dividedBy(steps.size() + 1);
        for (Step step : steps) {
            Pauses.sleep(slice);
            onStep.accept(step);
        }
        Pauses.sleep(slice);

        AgentReply reply = answer(files, steps);
        Log.debugf("Fake agent: answered session %s", turn.sessionId().value());
        return expired(turn) ? reply.withNotice(SbxSandboxAgent.recreatedNotice(turn.location(), turn.sessionFiles()))
                : reply;
    }

    /** Counts cloud turns per session; the one after {@code cloudExpiryTurns} of them "finds" the sandbox gone. */
    private boolean expired(AgentTurn turn) {
        if (cloudExpiryTurns == 0 || turn.location() != Location.CLOUD) {
            return false;
        }
        int[] expired = {0};
        cloudTurns.compute(turn.sessionId(), (id, count) -> {
            int done = count == null ? 0 : count;
            if (done >= cloudExpiryTurns) {
                expired[0] = 1;
                Log.debugf("Fake agent: pretending the cloud sandbox of session %s was lost (after %d turns)",
                        id.value(), done);
                return 0;
            }
            return done + 1;
        });
        return expired[0] == 1;
    }

    private static AgentReply answer(List<FileRef> files, List<Step> steps) {
        if (files.isEmpty()) {
            return new AgentReply(steps, List.of("I can work from the used-vehicle files in your connected OneDrive "
                    + "folders. Tell me which quarter, or attach the sales, wholesale price and days-to-turn files "
                    + "directly."), Optional.empty());
        }
        String names = files.stream().map(FileRef::name).collect(Collectors.joining(", "));
        return new AgentReply(steps,
                List.of("I read " + names + " and put together the Q3 2026 used-car quarterly report. Sales rose "
                                + "2.1% on Q2 to 625,000 units, while the average retail price slipped 0.5% to $28,792 "
                                + "as the composite wholesale index fell 0.8%.",
                        "Turn time improved: the average vehicle took 42.5 days to sell, down from 43.7, and the "
                                + "share of electric stock older than 60 days fell to 16% from 19%. Electric vehicles "
                                + "are still the slowest segment at 56 days, and their wholesale index is 11.9% below a "
                                + "year ago. I can compare this with the Q2 report or add loan performance next."),
                Optional.of(new ResultTable(List.of("Segment", "Units", "Avg retail price", "Wholesale QoQ",
                        "Days to turn"), List.of(
                        List.of("SUV", "237,000", "$30,310", "−0.5%", "38.0"),
                        List.of("Truck", "171,000", "$34,090", "−0.2%", "36.0"),
                        List.of("Sedan", "144,000", "$19,820", "−1.4%", "51.0"),
                        List.of("Electric", "73,000", "$29,150", "−3.1%", "56.0")))));
    }
}
