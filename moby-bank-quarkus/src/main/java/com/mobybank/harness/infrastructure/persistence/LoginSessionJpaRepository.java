package com.mobybank.harness.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/** The Panache queries for {@link LoginSessionEntity}. */
@ApplicationScoped
public class LoginSessionJpaRepository implements PanacheRepositoryBase<LoginSessionEntity, String> {
}
