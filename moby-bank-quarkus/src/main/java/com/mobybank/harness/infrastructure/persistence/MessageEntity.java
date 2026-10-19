package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One message of a session. Messages never change once written, so the files, steps, paragraphs and table are kept
 * as JSON next to it rather than in tables of their own.
 */
@Entity
@Table(name = "session_message")
public class MessageEntity {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    public SessionEntity session;

    /** Position in the conversation, from 0. */
    @Column(nullable = false)
    public int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public MessageRole role;

    @Column(columnDefinition = "text", nullable = false)
    public String text = "";

    @Column(columnDefinition = "text")
    public String notice;

    @Column(nullable = false)
    public Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<FileJson> files = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public List<StepJson> steps = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> paragraphs = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public TableJson resultTable;
}
