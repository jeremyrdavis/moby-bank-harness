package com.mobybank.harness.application;

import com.mobybank.harness.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Describes the signed-in analyst for the sidebar. Authentication is out of scope, so the identity comes from
 * configuration ({@code harness.user.name}, {@code harness.user.role}).
 */
@ApplicationScoped
public class CurrentUserApplicationService {

    private final String name;
    private final String role;

    @Inject
    public CurrentUserApplicationService(
            @ConfigProperty(name = "harness.user.name", defaultValue = "Hermione Granger") String name,
            @ConfigProperty(name = "harness.user.role", defaultValue = "Credit Research") String role) {
        this.name = name;
        this.role = role;
    }

    public UserDTO me() {
        return new UserDTO(UserId.DEMO.value(), name, role, initials(name));
    }

    private static String initials(String fullName) {
        String initials = Arrays.stream(fullName.strip().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
        return initials.isEmpty() ? "?" : initials;
    }
}
