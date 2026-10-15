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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A stand-in for the sandbox agent that answers with the same canned reply as the prototype: it "reads" each
 * attached file, "computes" one calculation, and returns a short summary plus a table when files were attached.
 * It lets the whole app run with no sandbox installed. It can also pretend a cloud sandbox was lost, to show the
 * analyst the notice that follows.
 */
public final class FakeSandboxAgent implements SandboxAgent {

    static final String COMPUTE_LABEL = "extract_financials(income_statement, balance_sheet, cash_flow)";

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
                return 0;
            }
            return done + 1;
        });
        return expired[0] == 1;
    }

    private static AgentReply answer(List<FileRef> files, List<Step> steps) {
        if (files.isEmpty()) {
            return new AgentReply(steps, List.of("I can work from the filings in your connected OneDrive folders. "
                    + "Tell me which company and period, or attach the documents directly."), Optional.empty());
        }
        String names = files.stream().map(FileRef::name).collect(Collectors.joining(", "));
        return new AgentReply(steps,
                List.of("I read " + names + " and extracted the income statement, balance sheet and cash flow "
                        + "figures. Revenue, margin and leverage are summarized below; I can compare them against "
                        + "consensus or the peer set next."),
                Optional.of(new ResultTable(List.of("Metric", "Current", "Prior year", "Change"), List.of(
                        List.of("Revenue", "$2.46B", "$2.31B", "+6.5%"),
                        List.of("EBITDA margin", "15.8%", "16.3%", "−50 bps"),
                        List.of("Net leverage", "2.2x", "2.0x", "+0.2x")))));
    }
}
