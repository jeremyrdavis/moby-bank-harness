/**
 * The implementations of the ports, and the wiring that chooses between them.
 *
 * <ul>
 *   <li><b>State:</b> the {@code persistence} sub-package stores sessions, connected folders, uploads and the sandbox
 *       records in PostgreSQL.</li>
 *   <li><b>Plumbing:</b> {@link com.mobybank.harness.infrastructure.InMemorySessionEventStream} fans events out to
 *       listeners and {@link com.mobybank.harness.infrastructure.VirtualThreadBackgroundRunner} runs long work off the
 *       request thread.</li>
 *   <li><b>Fakes</b> that stand in for the real things so the app runs with nothing installed:
 *       {@link com.mobybank.harness.infrastructure.FakeSandboxAgent}, {@link com.mobybank.harness.infrastructure.FakeSandboxTransfer}
 *       and {@link com.mobybank.harness.infrastructure.FakeDocumentCatalog}, plus the demo content in
 *       {@link com.mobybank.harness.infrastructure.DemoDataSeeder}.</li>
 *   <li><b>Selection:</b> {@link com.mobybank.harness.infrastructure.Adapters} picks each port's implementation from
 *       {@code harness.sandbox.mode} and {@code harness.documents.mode}.</li>
 * </ul>
 *
 * <p>The real adapters are in the sub-packages {@code sbx} (Docker Sandboxes) and {@code graph} (OneDrive); the
 * database is in {@code persistence}.
 */
package com.mobybank.harness.infrastructure;
