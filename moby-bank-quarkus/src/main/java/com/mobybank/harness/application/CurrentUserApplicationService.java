package com.mobybank.harness.application;

import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Describes the signed-in analyst for the sidebar: the stored account of whoever is making the request. When there is
 * no stored account (the single demo user with sign-in switched off and no seeded users), the name and role come from
 * configuration ({@code harness.user.name}, {@code harness.user.role}).
 */
@ApplicationScoped
public class CurrentUserApplicationService {

    private final CurrentUser current;
    private final UserRepository users;
    private final String fallbackName;
    private final String fallbackRole;

    @Inject
    public CurrentUserApplicationService(
            CurrentUser current, UserRepository users,
            @ConfigProperty(name = "harness.user.name", defaultValue = "Hermione Granger") String fallbackName,
            @ConfigProperty(name = "harness.user.role", defaultValue = "Credit Research") String fallbackRole) {
        this.current = current;
        this.users = users;
        this.fallbackName = fallbackName;
        this.fallbackRole = fallbackRole;
    }

    public UserDTO me() {
        Optional<AppUser> stored = users.findById(current.id());
        return stored.map(AuthenticationApplicationService::toDto)
                .orElseGet(() -> new UserDTO(current.id().value(), fallbackName, fallbackRole, initials(fallbackName)));
    }

    static String initials(String fullName) {
        String initials = Arrays.stream(fullName.strip().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
        return initials.isEmpty() ? "?" : initials;
    }
}
