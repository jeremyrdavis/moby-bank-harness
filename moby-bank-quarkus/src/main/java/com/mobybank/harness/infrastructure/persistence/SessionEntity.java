package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The stored form of a {@code Session}. {@code version} is the aggregate's version, advanced by the repository. */
@Entity
@Table(name = "agent_session")
public class SessionEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public Location moveTarget;

    /** The analyst chose where it runs, or it has messages or was moved: see {@code Session.started()}. */
    public boolean launched;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @Column(nullable = false)
    public long version;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    public List<MessageEntity> messages = new ArrayList<>();
}
