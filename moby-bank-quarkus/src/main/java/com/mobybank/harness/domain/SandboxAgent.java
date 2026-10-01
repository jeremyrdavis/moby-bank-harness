package com.mobybank.harness.domain;

import java.util.function.Consumer;

/**
 * The agent running in a Docker Sandbox. Implementations decide how to reach it (the fake, the local sandbox, the
 * cloud sandbox); the turn's {@link Location} tells them which one to use.
 */
public interface SandboxAgent {

    /**
     * Runs one turn to completion.
     *
     * @param onStep called for each step of the trace as it happens, before the reply is returned
     * @throws SandboxFailureException if the agent could not produce a reply
     */
    AgentReply runTurn(AgentTurn turn, Consumer<Step> onStep);
}
