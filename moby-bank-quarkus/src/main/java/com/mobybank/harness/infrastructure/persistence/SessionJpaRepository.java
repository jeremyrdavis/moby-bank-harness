package com.mobybank.harness.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** The Panache queries for {@link SessionEntity}. Only {@link PostgresSessionRepository} uses it. */
@ApplicationScoped
public class SessionJpaRepository implements PanacheRepositoryBase<SessionEntity, UUID> {
}
