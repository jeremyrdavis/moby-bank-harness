package com.mobybank.harness.infrastructure;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects the messages a class logs, at every level, while it is open. Tests use it to prove what a log line does
 * <em>not</em> contain (a secret, a prompt).
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final boolean previousUseParentHandlers;
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            messages.add(new java.util.logging.SimpleFormatter().formatMessage(record));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private LogCapture(Class<?> source) {
        this.logger = Logger.getLogger(source.getName());
        this.previousLevel = logger.getLevel();
        this.previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    /** Starts capturing what {@code source} logs. Close it (try-with-resources) to restore the logger. */
    public static LogCapture of(Class<?> source) {
        return new LogCapture(source);
    }

    /** The formatted messages logged so far. */
    public List<String> messages() {
        return List.copyOf(messages);
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(previousUseParentHandlers);
        logger.setLevel(previousLevel);
    }
}
