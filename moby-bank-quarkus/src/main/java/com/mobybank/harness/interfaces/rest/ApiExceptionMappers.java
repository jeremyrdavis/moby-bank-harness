package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.ResourceNotFoundException;
import com.mobybank.harness.domain.DomainException;
import com.mobybank.harness.domain.InvalidMoveException;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SessionBusyException;
import com.mobybank.harness.domain.StaleAggregateException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Turns application and domain failures into HTTP errors with an {@link ApiError} body:
 * 400 bad input, 404 unknown session or folder, 409 a rule says not now (busy, invalid move, stale update),
 * 502 the sandbox failed.
 */
public class ApiExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMappers.class);

    @ServerExceptionMapper
    public RestResponse<ApiError> notFound(ResourceNotFoundException e) {
        return RestResponse.status(RestResponse.Status.NOT_FOUND, new ApiError("not_found", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> badInput(IllegalArgumentException e) {
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
        LOG.warn("Sandbox failure reached the API", e);
        return RestResponse.status(RestResponse.Status.BAD_GATEWAY, new ApiError("sandbox_failure", e.getMessage()));
    }

    @ServerExceptionMapper
    public RestResponse<ApiError> otherDomainFailure(DomainException e) {
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, new ApiError("bad_request", e.getMessage()));
    }

    private static RestResponse<ApiError> conflict(DomainException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, new ApiError("conflict", e.getMessage()));
    }
}
