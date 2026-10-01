package com.mobybank.harness.application;

/** The signed-in analyst as the sidebar shows them: an id, a display name, a role, and the initials for the avatar. */
public record UserDTO(String id, String name, String role, String initials) {
}
