/**
 * The heart of the harness: what a conversation is, and the rules that keep it consistent. Plain Java with no
 * framework; the only imports are the JDK and this package itself, and a test fails the build if that changes.
 *
 * <h2>What is here</h2>
 *
 * <ul>
 *   <li><b>Aggregates</b>, which guard their own invariants and are saved as a whole:
 *       {@link com.mobybank.harness.domain.Session} (owns its {@link com.mobybank.harness.domain.Message}s and a status
 *       machine) and {@link com.mobybank.harness.domain.ConnectedFolders}.</li>
 *   <li><b>Value objects</b>, all records that validate in their constructors: the typed ids
 *       ({@link com.mobybank.harness.domain.SessionId}, {@link com.mobybank.harness.domain.MessageId},
 *       {@link com.mobybank.harness.domain.FolderId}, {@link com.mobybank.harness.domain.UserId}), file and step
 *       descriptions, {@link com.mobybank.harness.domain.ResultTable}, {@link com.mobybank.harness.domain.SessionTitle}
 *       and the requests and results exchanged with the ports.</li>
 *   <li><b>Ports</b>, the interfaces the rest of the app implements:
 *       {@link com.mobybank.harness.domain.SessionRepository}, {@link com.mobybank.harness.domain.ConnectedFoldersRepository},
 *       {@link com.mobybank.harness.domain.SandboxAgent}, {@link com.mobybank.harness.domain.SandboxTransfer} and
 *       {@link com.mobybank.harness.domain.DocumentCatalog}.</li>
 *   <li><b>Events</b> raised by aggregates ({@link com.mobybank.harness.domain.DomainEvent}) and the
 *       {@link com.mobybank.harness.domain.DomainException} family.</li>
 * </ul>
 *
 * <h2>The rules worth knowing</h2>
 *
 * <ul>
 *   <li>A session is {@link com.mobybank.harness.domain.SessionStatus#IDLE idle}, running a turn, or moving, and only an
 *       idle one accepts a message or a move.</li>
 *   <li>A move always goes to the <em>other</em> {@link com.mobybank.harness.domain.Location}, which is what makes both
 *       local-to-cloud and cloud-to-local work with one method.</li>
 *   <li>Aggregates are built by a factory ({@code Session.start}) or rebuilt from storage by {@code rehydrate}, never
 *       with {@code new}; they carry a plain {@code long version} for optimistic locking.</li>
 * </ul>
 */
package com.mobybank.harness.domain;
