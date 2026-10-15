package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.Location;

/** Which direction the fake transfer refuses ({@code harness.fake.move-failure}), to see a failed move in the page. */
public enum FakeMoveFailure {
    NONE, TO_CLOUD, TO_LOCAL;

    /** Reads {@code none}, {@code to-cloud} or {@code to-local}; anything else names the property and stops the app. */
    public static FakeMoveFailure parse(String text) {
        return switch (text.strip().toLowerCase()) {
            case "none", "" -> NONE;
            case "to-cloud" -> TO_CLOUD;
            case "to-local" -> TO_LOCAL;
            default -> throw new IllegalStateException(
                    "Unsupported harness.fake.move-failure=" + text + " (supported: none, to-cloud, to-local)");
        };
    }

    boolean refuses(Location destination) {
        return (this == TO_CLOUD && destination == Location.CLOUD) || (this == TO_LOCAL && destination == Location.LOCAL);
    }
}
