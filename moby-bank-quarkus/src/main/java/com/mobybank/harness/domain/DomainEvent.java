package com.mobybank.harness.domain;

/** Marker for facts raised by aggregates. The application service drains and publishes them. */
public sealed interface DomainEvent permits AgentRepliedEvent, SessionMovedEvent {
}
