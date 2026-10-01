package com.mobybank.harness.application;

/** One entry of an agent trace. {@code kind} is {@code read} or {@code compute}. */
public record StepDTO(String kind, String label) {
}
