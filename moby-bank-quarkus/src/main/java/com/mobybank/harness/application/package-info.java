/**
 * The use cases. Each {@code *ApplicationService} loads an aggregate, changes it, saves it, and publishes what
 * happened; none of them contains business rules (those are in the domain) or knows about HTTP.
 *
 * <ul>
 *   <li>{@link com.mobybank.harness.application.SessionApplicationService}: start a conversation, send a message,
 *       attach an upload, move a session. Agent turns and moves run in the background.</li>
 *   <li>{@link com.mobybank.harness.application.FolderApplicationService}: browse the folder library, connect folders,
 *       list the documents of connected folders.</li>
 *   <li>{@link com.mobybank.harness.application.CurrentUserApplicationService}: who the sidebar shows.</li>
 * </ul>
 *
 * <p>The services speak in commands and DTOs ({@code *Command}, {@code *DTO}), which use only strings, numbers and
 * other DTOs, so the REST layer never touches a domain type. {@link com.mobybank.harness.application.SessionEvent}
 * lists what a client can be told as it happens.
 *
 * <p>Three small ports belong here rather than in the domain because only the application needs them:
 * {@link com.mobybank.harness.application.BackgroundRunner}, {@link com.mobybank.harness.application.SessionEventStream}
 * and {@link com.mobybank.harness.application.UploadStore}.
 */
package com.mobybank.harness.application;
