package com.mobybank.harness.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A file the analyst uploaded for a session. The key is the session id and the file name. */
@Entity
@Table(name = "upload")
public class UploadEntity {

    /** {@code <session id>/<file name>}. */
    @Id
    public String id;

    @Column(nullable = false)
    public UUID sessionId;

    @Column(nullable = false)
    public String fileName;

    @Column(nullable = false)
    public byte[] content;
}
