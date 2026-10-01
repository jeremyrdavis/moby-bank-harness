package com.mobybank.harness.infrastructure.sbx;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A scripted {@link CommandRunner}: records every command and answers from the first rule that matches. */
final class FakeRunner implements CommandRunner {

    record Call(List<String> command, Duration timeout) {

        String line() {
            return String.join(" ", command);
        }
    }

    record Response(int exit, List<String> stdout, String stderr) {

        static Response ok(String... lines) {
            return new Response(0, List.of(lines), "");
        }

        static Response fail(int exit, String stderr) {
            return new Response(exit, List.of(), stderr);
        }
    }

    private record Rule(Predicate<List<String>> matches, java.util.function.Function<List<String>, Response> answer) {
    }

    final List<Call> calls = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();

    FakeRunner on(Predicate<List<String>> matches, Response response) {
        rules.add(new Rule(matches, command -> response));
        return this;
    }

    FakeRunner on(Predicate<List<String>> matches, java.util.function.Function<List<String>, Response> answer) {
        rules.add(new Rule(matches, answer));
        return this;
    }

    /** True when the command contains all of the tokens. */
    static Predicate<List<String>> has(String... tokens) {
        return command -> command.containsAll(List.of(tokens));
    }

    @Override
    public CommandResult run(List<String> command, Duration timeout, Consumer<String> onStdoutLine) {
        calls.add(new Call(List.copyOf(command), timeout));
        for (Rule rule : rules) {
            if (rule.matches().test(command)) {
                Response response = rule.answer().apply(command);
                response.stdout().forEach(onStdoutLine);
                return new CommandResult(response.exit(), String.join("\n", response.stdout()), response.stderr());
            }
        }
        return new CommandResult(0, "", "");
    }

    List<String> lines() {
        return calls.stream().map(Call::line).toList();
    }

    /** The commands that contain the token, as single lines. */
    List<String> linesWith(String token) {
        return calls.stream().filter(c -> c.command().contains(token)).map(Call::line).toList();
    }
}
