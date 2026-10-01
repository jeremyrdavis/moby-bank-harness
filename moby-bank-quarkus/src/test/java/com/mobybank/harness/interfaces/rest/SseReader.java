package com.mobybank.harness.interfaces.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** A minimal server-sent-events client for tests: reads a stream on a thread and queues each event. */
final class SseReader implements AutoCloseable {

    /** One parsed event; {@code name} is null for comment-only blocks. */
    record Event(String name, String data) {
    }

    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private final Thread reader;
    private volatile int status = -1;

    SseReader(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();
        reader = Thread.ofVirtual().unstarted(() -> {
            try {
                HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
                status = response.statusCode();
                String name = null;
                StringBuilder data = new StringBuilder();
                for (String line : (Iterable<String>) response.body()::iterator) {
                    if (line.isEmpty()) {
                        if (name != null) {
                            events.add(new Event(name, data.toString()));
                        }
                        name = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        name = line.substring(6).strip();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).strip());
                    }
                }
            } catch (Exception e) {
                // closed by the test
            }
        });
        reader.start();
    }

    Event next(Duration timeout) throws InterruptedException {
        Event event = events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (event == null) {
            throw new AssertionError("no event within " + timeout + " (HTTP status " + status + ")");
        }
        return event;
    }

    /** Reads events until one matches, and returns everything read, the matching event last. */
    List<Event> readUntil(java.util.function.Predicate<Event> match, Duration timeout) throws InterruptedException {
        List<Event> seen = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw new AssertionError("expected event never arrived; saw " + seen);
            }
            Event event = next(Duration.ofNanos(left));
            seen.add(event);
            if (match.test(event)) {
                return seen;
            }
        }
    }

    int status() {
        return status;
    }

    @Override
    public void close() {
        client.shutdownNow();
        reader.interrupt();
    }
}
