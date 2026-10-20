package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.MessageId;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionTitle;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import com.mobybank.harness.domain.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Converts a {@link Session} to and from its {@link SessionEntity}. */
final class SessionMapper {

    private SessionMapper() {
    }

    /** Rebuilds the aggregate, at the version the row holds. */
    static Session toAggregate(SessionEntity entity) {
        List<Message> messages = entity.messages.stream().map(SessionMapper::toMessage).toList();
        UserId owner = entity.ownerId == null ? UserId.DEMO : new UserId(entity.ownerId);
        return Session.rehydrate(new SessionId(entity.id), owner, new SessionTitle(entity.title), entity.location,
                entity.status, entity.moveTarget, messages, entity.launched, entity.createdAt, entity.updatedAt,
                entity.version);
    }

    /**
     * Copies the aggregate's state into the entity. Messages never change, so existing rows are kept and only the new
     * messages are added.
     */
    static void copyInto(Session session, SessionEntity entity) {
        entity.id = session.id().value();
        entity.ownerId = session.owner().value();
        entity.title = session.title().value();
        entity.location = session.location();
        entity.status = session.status();
        entity.moveTarget = session.moveTarget();
        entity.launched = session.started();
        entity.createdAt = session.createdAt();
        entity.updatedAt = session.updatedAt();

        Map<UUID, MessageEntity> existing = new HashMap<>();
        entity.messages.forEach(message -> existing.put(message.id, message));
        List<Message> messages = session.messages();
        List<MessageEntity> merged = new ArrayList<>();
        for (int position = 0; position < messages.size(); position++) {
            Message message = messages.get(position);
            MessageEntity row = existing.get(message.id().value());
            if (row == null) {
                row = toEntity(message, position, entity);
            }
            merged.add(row);
        }
        entity.messages.clear();
        entity.messages.addAll(merged);
    }

    private static MessageEntity toEntity(Message message, int position, SessionEntity session) {
        MessageEntity row = new MessageEntity();
        row.id = message.id().value();
        row.session = session;
        row.position = position;
        row.role = message.role();
        row.text = message.text();
        row.notice = message.notice().orElse(null);
        row.createdAt = message.createdAt();
        row.files = message.files().stream().map(file -> new FileJson(file.name(), file.source().name())).toList();
        row.steps = message.steps().stream().map(step -> new StepJson(step.kind().name(), step.label())).toList();
        row.paragraphs = message.paragraphs();
        row.resultTable = message.table().map(table -> new TableJson(table.cols(), table.rows())).orElse(null);
        return row;
    }

    private static Message toMessage(MessageEntity row) {
        List<FileRef> files = row.files == null ? List.of() : row.files.stream()
                .map(file -> new FileRef(file.name(), FileSource.valueOf(file.source()))).toList();
        List<Step> steps = row.steps == null ? List.of() : row.steps.stream()
                .map(step -> new Step(StepKind.valueOf(step.kind()), step.label())).toList();
        Optional<ResultTable> table = Optional.ofNullable(row.resultTable)
                .map(json -> new ResultTable(json.cols(), json.rows()));
        return Message.rehydrate(new MessageId(row.id), row.role, row.text, files, steps,
                row.paragraphs == null ? List.of() : row.paragraphs, table, Optional.ofNullable(row.notice),
                row.createdAt);
    }
}
