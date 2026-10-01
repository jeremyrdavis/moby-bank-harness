package com.mobybank.harness.infrastructure.sbx;

/** What a finished command produced. {@code stdout} and {@code stderr} keep at most the last 64 KiB. */
public record CommandResult(int exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return exitCode == 0;
    }

    /** The end of stderr (or stdout if stderr is empty), trimmed, for error messages. */
    public String tail() {
        String text = stderr.isBlank() ? stdout : stderr;
        String trimmed = text.strip();
        return trimmed.length() <= 500 ? trimmed : "…" + trimmed.substring(trimmed.length() - 500);
    }
}
