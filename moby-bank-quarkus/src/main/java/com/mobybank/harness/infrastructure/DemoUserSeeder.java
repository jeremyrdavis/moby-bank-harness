package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.PasswordHasher;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Creates the demo analysts at startup so the sign-in page works at once: Hermione Granger, who owns the demo
 * conversations, Rana Kapoor, who starts with none, and Jeremy Davis, the developer's own account. Each is added only if
 * no account has its email yet, so it also works on a database that already has users and never changes an account that
 * exists. Turn it off with {@code harness.auth.seed-users=false}. Hermione and Rana get the password
 * {@code harness.auth.seed-password}, Jeremy gets {@code harness.auth.seed-developer-password}. Both are public in this
 * repository: change them, or turn seeding off, anywhere that is not a demo.
 */
@ApplicationScoped
public class DemoUserSeeder {

    static final String DEFAULT_PASSWORD = "moby-demo";
    static final String DEFAULT_DEVELOPER_PASSWORD = "myawesomepassword";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final boolean enabled;
    private final String password;
    private final String developerPassword;

    @Inject
    public DemoUserSeeder(UserRepository users, PasswordHasher hasher,
                          @ConfigProperty(name = "harness.auth.seed-users", defaultValue = "true") boolean enabled,
                          @ConfigProperty(name = "harness.auth.seed-password", defaultValue = DEFAULT_PASSWORD) String password,
                          @ConfigProperty(name = "harness.auth.seed-developer-password",
                                  defaultValue = DEFAULT_DEVELOPER_PASSWORD) String developerPassword) {
        this.users = users;
        this.hasher = hasher;
        this.enabled = enabled;
        this.password = password;
        this.developerPassword = developerPassword;
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            Log.debug("Demo users are not seeded (harness.auth.seed-users=false)");
            return;
        }
        seed();
        boolean publicPassword = DEFAULT_PASSWORD.equals(password) || DEFAULT_DEVELOPER_PASSWORD.equals(developerPassword);
        if (publicPassword && LaunchMode.current() == LaunchMode.NORMAL) {
            Log.warn("The demo users use passwords that are in the repository. Change harness.auth.seed-password and "
                    + "harness.auth.seed-developer-password, or set harness.auth.seed-users=false.");
        }
    }

    void seed() {
        String shared = hasher.hash(password);
        ensure(UserId.DEMO, "hermione.granger@mobybank.com", "Hermione Granger", "Credit Research", shared);
        ensure(new UserId("analyst-rana-kapoor"), "rana.kapoor@mobybank.com", "Rana Kapoor", "Credit Research", shared);
        ensure(new UserId("developer-jeremy-davis"), "jeremy.davis@docker.com", "Jeremy Davis", "Developer",
                hasher.hash(developerPassword));
    }

    private void ensure(UserId id, String email, String name, String role, String passwordHash) {
        if (users.findByEmail(email).isPresent()) {
            Log.debugf("The demo user %s exists already", id.value());
            return;
        }
        users.persist(AppUser.withPassword(id, email, name, role, passwordHash));
        Log.infof("Seeded the demo user %s", email);
    }
}
