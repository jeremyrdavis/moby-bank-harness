package com.mobybank.harness.application;

import com.mobybank.harness.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Who is calling: the user the authentication filter found for this request. With {@code harness.auth.mode=none}
 * everybody is the demo user, in or out of a request.
 */
@ApplicationScoped
public class RequestCurrentUser implements CurrentUser {

    private final Instance<RequestIdentity> identity;
    private final boolean signInRequired;

    @Inject
    public RequestCurrentUser(Instance<RequestIdentity> identity,
                              @ConfigProperty(name = "harness.auth.mode", defaultValue = "login") String mode) {
        this.identity = identity;
        this.signInRequired = !"none".equalsIgnoreCase(mode == null ? "" : mode.strip());
    }

    @Override
    public UserId id() {
        if (!signInRequired) {
            return UserId.DEMO;
        }
        String user = identity.get().userId();
        if (user == null) {
            throw new IllegalStateException("no signed-in user for this request");
        }
        return new UserId(user);
    }
}
