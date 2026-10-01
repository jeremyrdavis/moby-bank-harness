/**
 * The OneDrive adapter, which reads documents through Microsoft Graph. Used when {@code harness.documents.mode=graph}.
 *
 * <p>{@link com.mobybank.harness.infrastructure.graph.GraphDocumentCatalog} implements the domain's
 * {@link com.mobybank.harness.domain.DocumentCatalog}; {@link com.mobybank.harness.infrastructure.graph.GraphTokenProvider}
 * supplies the bearer token, either configured directly or obtained with the OAuth client-credentials flow.
 */
package com.mobybank.harness.infrastructure.graph;
