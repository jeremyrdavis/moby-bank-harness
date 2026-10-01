package com.mobybank.harness.application;

import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.Step;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Translates between domain types and API types. The API speaks lower-case strings ({@code "local"},
 * {@code "assistant"}, ...) so it stays the same for every backend implementation.
 */
final class Dtos {

    private Dtos() {
    }

    // --- domain -> API -----------------------------------------------------------------------------------------

    static SessionDTO session(Session session, String group) {
        return new SessionDTO(
                id(session.id()),
                session.title().value(),
                name(session.location()),
                name(session.status()),
                session.moveTarget() == null ? null : name(session.moveTarget()),
                group,
                session.createdAt(),
                session.updatedAt(),
                session.messages().stream().map(Dtos::message).toList(),
                session.filesShared().stream().map(Dtos::file).toList());
    }

    static SessionSummaryDTO summary(Session session, String group) {
        return new SessionSummaryDTO(id(session.id()), session.title().value(), name(session.location()),
                name(session.status()), group, session.updatedAt());
    }

    static MessageDTO message(Message message) {
        return new MessageDTO(
                message.id().value().toString(),
                name(message.role()),
                message.text(),
                message.files().stream().map(Dtos::file).toList(),
                message.steps().stream().map(Dtos::step).toList(),
                message.paragraphs(),
                message.table().map(Dtos::table).orElse(null),
                message.createdAt());
    }

    static FileRefDTO file(FileRef file) {
        return new FileRefDTO(file.name(), name(file.source()));
    }

    static StepDTO step(Step step) {
        return new StepDTO(name(step.kind()), step.label());
    }

    static TableDTO table(ResultTable table) {
        return new TableDTO(table.cols(), table.rows());
    }

    static String id(SessionId id) {
        return id.value().toString();
    }

    static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    // --- API -> domain -----------------------------------------------------------------------------------------

    static SessionId sessionId(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("session id required");
        }
        try {
            return SessionId.parse(text.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid session id: " + text);
        }
    }

    static Location location(String text, Location whenBlank) {
        if (text == null || text.isBlank()) {
            if (whenBlank == null) {
                throw new IllegalArgumentException("location required (local or cloud)");
            }
            return whenBlank;
        }
        try {
            return Location.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown location: " + text + " (expected local or cloud)");
        }
    }

    static List<FileRef> fileRefs(List<FileRefDTO> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(Dtos::fileRef).toList();
    }

    private static FileRef fileRef(FileRefDTO dto) {
        Objects.requireNonNull(dto, "file required");
        try {
            return new FileRef(dto.name(), FileSource.valueOf(String.valueOf(dto.source()).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            if (dto.name() == null || dto.name().isBlank()) {
                throw e;
            }
            throw new IllegalArgumentException("unknown file source: " + dto.source() + " (expected upload or onedrive)");
        }
    }
}
