package com.mobybank.harness.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/** The Panache queries for {@link UserEntity}. */
@ApplicationScoped
public class UserJpaRepository implements PanacheRepositoryBase<UserEntity, String> {
}
