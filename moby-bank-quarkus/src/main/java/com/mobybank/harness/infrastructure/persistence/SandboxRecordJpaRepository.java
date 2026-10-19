package com.mobybank.harness.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** The Panache queries for {@link SandboxRecordEntity}. */
@ApplicationScoped
public class SandboxRecordJpaRepository implements PanacheRepositoryBase<SandboxRecordEntity, UUID> {
}
