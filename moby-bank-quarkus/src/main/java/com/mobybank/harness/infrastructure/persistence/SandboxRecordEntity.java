package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Which sandbox backs a session, and the agent's conversation id inside it. */
@Entity
@Table(name = "sandbox_record")
public class SandboxRecordEntity {

    @Id
    public UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public Location location;

    /** The sandbox's name exactly as {@code sbx ls} lists it; null when no sandbox is known. */
    public String sandboxName;

    public Integer generation;

    public String agentSessionId;

    /** The sandbox was created empty by a move and still needs the session's shared files. */
    @Column(nullable = false)
    public boolean awaitingFiles;
}
