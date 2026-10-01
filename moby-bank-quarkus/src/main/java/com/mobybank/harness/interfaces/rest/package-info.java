/**
 * The HTTP surface: JAX-RS resources, request bodies, and the exception mappers that turn failures into error
 * responses. It depends on the application layer only (the exception mapper also names the domain's exception types).
 * The contract is published as {@code openapi.yaml}, generated at build time.
 *
 * <ul>
 *   <li>{@link com.mobybank.harness.interfaces.rest.SessionsResource}: conversations, messages, uploads, moves and the
 *       live event stream.</li>
 *   <li>{@link com.mobybank.harness.interfaces.rest.FoldersResource}: the folder library.</li>
 *   <li>{@link com.mobybank.harness.interfaces.rest.MeResource}: the signed-in analyst.</li>
 *   <li>{@link com.mobybank.harness.interfaces.rest.ApiExceptionMappers}: 400 bad input, 404 unknown, 409 not now, 502 a
 *       downstream system failed.</li>
 * </ul>
 */
package com.mobybank.harness.interfaces.rest;
