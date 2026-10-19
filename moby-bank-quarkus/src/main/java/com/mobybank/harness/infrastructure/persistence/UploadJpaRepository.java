package com.mobybank.harness.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/** The Panache queries for {@link UploadEntity}. */
@ApplicationScoped
public class UploadJpaRepository implements PanacheRepositoryBase<UploadEntity, String> {
}
