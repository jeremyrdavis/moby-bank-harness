package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A stand-in for the sandbox agent that answers with the same canned reply as the prototype: it "reads" each
 * attached file, "computes" one calculation, and returns a short summary plus a table when files were attached.
 * It lets the whole app run with no sandbox installed.
 */
public final class FakeSandboxAgent implements SandboxAgent {

    static final String COMPUTE_LABEL = "extract_financials(income_statement, balance_sheet, cash_flow)";

    private final Duration delay;

    public FakeSandboxAgent(Duration delay) {
        this.delay = delay;
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
