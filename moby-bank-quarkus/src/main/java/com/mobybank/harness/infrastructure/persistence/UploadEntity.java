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

    /** {@code <session id>/<file name>}. Text, so a long file name does not hit a 255-character limit. */
    @Id
    @Column(name = "id", columnDefinition = "text")
    public String id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "file_name", columnDefinition = "text", nullable = false)
    public String fileName;

    @Column(nullable = false)
    public byte[] content;
}
