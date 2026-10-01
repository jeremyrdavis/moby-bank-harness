package com.mobybank.harness.application;

/**
 * Runs long work (an agent turn, a session move) off the caller's thread. Kept as a port so tests can run the work
 * deterministically.
 */
public interface BackgroundRunner {

    void run(Runnable task);
}
