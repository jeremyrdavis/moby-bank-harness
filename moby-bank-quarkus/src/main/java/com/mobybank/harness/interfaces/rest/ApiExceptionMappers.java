package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.ResourceNotFoundException;
import com.mobybank.harness.domain.DocumentSourceException;
import com.mobybank.harness.domain.DomainException;
import com.mobybank.harness.domain.InvalidMoveException;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SignInRequiredException;
import com.mobybank.harness.domain.SessionBusyException;
import com.mobybank.harness.domain.StaleAggregateException;
import io.quarkus.logging.Log;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Turns application and domain failures into HTTP errors with an {@link ApiError} body:
 * 400 bad input, 404 unknown session or folder, 409 a rule says not now (busy, invalid move, stale update),
 * 502 the sandbox or the document source failed.
 */
public class ApiExceptionMappers {


    @ServerExceptionMapper
    public RestResponse<ApiError> notFound(ResourceNotFoundException e) {
        Log.debugf("404: %s", e.getMessage());
        return RestResponse.status(RestResponse.Status.NOT_FOUND, new ApiError("not_found", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> badInput(IllegalArgumentException e) {
        Log.debugf("400: %s", e.getMessage());
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, new ApiError("bad_request", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> busy(SessionBusyException e) {
        return conflict(e);
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> invalidMove(InvalidMoveException e) {
        return conflict(e);
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> stale(StaleAggregateException e) {
        return conflict(e);
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> sandboxFailure(SandboxFailureException e) {
        Log.warn("Sandbox failure reached the API", e);
        return RestResponse.status(RestResponse.Status.BAD_GATEWAY, new ApiError("sandbox_failure", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> documentSourceFailure(DocumentSourceException e) {
        Log.warn("Document source failure reached the API", e);
        return RestResponse.status(RestResponse.Status.BAD_GATEWAY, new ApiError("document_source_failure", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> signInRequired(SignInRequiredException e) {
        Log.debugf("401: %s", e.getMessage());
        return RestResponse.status(RestResponse.Status.UNAUTHORIZED, new ApiError("sign_in_required", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> otherDomainFailure(DomainException e) {
        Log.debugf("400 (%s): %s", e.getClass().getSimpleName(), e.getMessage());
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, new ApiError("bad_request", e.getMessage()));
    }

    private static RestResponse<ApiError> conflict(DomainException e) {
        Log.debugf("409 (%s): %s", e.getClass().getSimpleName(), e.getMessage());
        return RestResponse.status(RestResponse.Status.CONFLICT, new ApiError("conflict", e.getMessage()));
    }
}
