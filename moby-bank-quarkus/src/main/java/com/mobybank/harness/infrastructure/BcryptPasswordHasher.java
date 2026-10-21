package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.PasswordHasher;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

/** Hashes passwords with bcrypt. */
@ApplicationScoped
public class BcryptPasswordHasher implements PasswordHasher {

    @Override
    public String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    @Override
    public boolean matches(String password, String hash) {
        try {
            return BcryptUtil.matches(password, hash);
        } catch (RuntimeException e) {
            return false; // a hash that is not a bcrypt hash never matches
        }
    }
}
