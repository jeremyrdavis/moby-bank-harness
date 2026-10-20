package com.mobybank.harness.application;

import com.mobybank.harness.domain.UserId;

/**
 * Who is making the request being handled. Use cases ask for it instead of taking the caller as a parameter. It only
 * works while a request is being handled: background work knows its session and never asks.
 */
public interface CurrentUser {

    UserId id();
}
