/**
 * The PostgreSQL implementations of the repository ports, written with Hibernate ORM and Panache.
 *
 * <p>The domain aggregates stay plain Java. Each has a separate JPA entity here ({@link
 * com.mobybank.harness.infrastructure.persistence.SessionEntity}, {@link
 * com.mobybank.harness.infrastructure.persistence.ConnectedFoldersEntity}, {@link
 * com.mobybank.harness.infrastructure.persistence.UploadEntity}, {@link
 * com.mobybank.harness.infrastructure.persistence.SandboxRecordEntity}), a mapper that converts between the two, and a
 * Panache repository that does the queries. The classes named {@code Postgres...} implement the ports and are the only
 * ones the rest of the application sees.
 *
 * <p>Every repository method is its own short transaction. An agent turn or a move runs for minutes between loading a
 * session and saving it, so no transaction may span them; the version check in {@code persist} is what protects against
 * two writers.
 */
package com.mobybank.harness.infrastructure.persistence;
