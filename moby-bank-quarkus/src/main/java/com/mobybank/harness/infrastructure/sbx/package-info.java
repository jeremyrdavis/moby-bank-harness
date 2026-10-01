/**
 * The real sandbox adapters, which drive Docker Sandboxes through the {@code sbx} command line. Used when
 * {@code harness.sandbox.mode=sbx}.
 *
 * <ul>
 *   <li>{@link com.mobybank.harness.infrastructure.sbx.SbxSandboxAgent} runs a turn: it finds or creates the session's
 *       sandbox, copies attached files in, runs the agent headless, and turns its output into steps and a reply.</li>
 *   <li>{@link com.mobybank.harness.infrastructure.sbx.SbxSandboxTransfer} moves a session between local and cloud with
 *       {@code sbx move}.</li>
 *   <li>{@link com.mobybank.harness.infrastructure.sbx.SbxCli} builds every {@code sbx} command in one place, and
 *       {@link com.mobybank.harness.infrastructure.sbx.CommandRunner} is the seam tests replace so no real {@code sbx} is
 *       needed.</li>
 *   <li>{@link com.mobybank.harness.infrastructure.sbx.SandboxRegistry} remembers which sandbox backs which session and
 *       how sandboxes are named.</li>
 * </ul>
 */
package com.mobybank.harness.infrastructure.sbx;
