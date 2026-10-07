/**
 * The HTML endpoints behind {@code index.html}, the htmx page served at {@code /}. Each endpoint answers with a
 * fragment of HTML (rendered from the Qute templates in {@code src/main/resources/templates}) that the page swaps
 * into itself. The page's paths start with {@code /api}; its {@code api-base} setting maps them to {@code /ui}, so the
 * JSON API in {@code interfaces.rest} and its OpenAPI contract stay separate.
 *
 * <p>Like the REST layer, this one depends on the application layer only (see {@code WebLayeringTest}).
 */
package com.mobybank.harness.interfaces.web;
