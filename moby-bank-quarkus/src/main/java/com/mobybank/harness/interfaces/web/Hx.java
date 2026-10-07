package com.mobybank.harness.interfaces.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/** Helpers for answering htmx: render a fragment, and raise the events and toasts the page listens for. */
final class Hx {

    static final String CONVERSATIONS_CHANGED = "conversations-changed";
    static final String FOLDERS_CHANGED = "folders-changed";
    static final String CLOSE_DIALOG = "close-dialog";

    /** Header values must be ASCII, so non-ASCII characters (such as the ellipsis in a toast) are escaped. */
    private static final JsonMapper JSON = JsonMapper.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    private Hx() {
    }

    /** A 200 response carrying the rendered fragment. */
    static Response.ResponseBuilder html(TemplateInstance fragment) {
        return Response.ok(fragment.render(), MediaType.TEXT_HTML + ";charset=UTF-8");
    }

    /** A 200 response with nothing to swap; the headers do the work. */
    static Response.ResponseBuilder empty() {
        return Response.ok("", MediaType.TEXT_HTML + ";charset=UTF-8");
    }

    /** Builds an {@code HX-Trigger} header value from named events, optionally with a toast. */
    static Triggers triggers() {
        return new Triggers();
    }

    static final class Triggers {
        private final Map<String, Object> events = new LinkedHashMap<>();

        Triggers event(String name) {
            events.put(name, Map.of());
            return this;
        }

        /** The page shows this as a toast. Reusing an {@code id} updates that toast in place. */
        Triggers toast(String id, String variant, String title, String description) {
            Map<String, Object> toast = new LinkedHashMap<>();
            if (id != null) {
                toast.put("id", id);
            }
            if (variant != null) {
                toast.put("variant", variant);
            }
            toast.put("title", title);
            toast.put("description", description);
            events.put("toast", toast);
            return this;
        }

        String header() {
            try {
                return JSON.writeValueAsString(events);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        Response.ResponseBuilder on(Response.ResponseBuilder response) {
            return response.header("HX-Trigger", header());
        }
    }
}
