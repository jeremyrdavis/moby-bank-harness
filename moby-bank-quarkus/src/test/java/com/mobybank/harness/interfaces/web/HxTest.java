package com.mobybank.harness.interfaces.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HxTest {

    @Test
    void theTriggerHeaderIsJsonWithTheEventsAndToastThePageListensFor() throws Exception {
        String header = Hx.triggers().event(Hx.CLOSE_DIALOG).event(Hx.FOLDERS_CHANGED)
                .toast("move-cloud", "success", "Session moved", "All done").header();

        JsonNode json = new ObjectMapper().readTree(header);
        assertTrue(json.has("close-dialog"));
        assertTrue(json.has("folders-changed"));
        assertEquals("move-cloud", json.at("/toast/id").asText());
        assertEquals("success", json.at("/toast/variant").asText());
        assertEquals("Session moved", json.at("/toast/title").asText());
    }

    @Test
    void headersStayAsciiEvenWhenATextHasAnEllipsisOrAccent() throws Exception {
        String header = Hx.triggers().toast(null, null, "Moving…", "Café").header();

        assertTrue(header.chars().allMatch(c -> c < 128), "header must be ASCII: " + header);
        assertEquals("Moving…", new ObjectMapper().readTree(header).at("/toast/title").asText());
    }

    @Test
    void aToastWithoutAnIdOrVariantOmitsThem() throws Exception {
        JsonNode toast = new ObjectMapper().readTree(Hx.triggers().toast(null, null, "t", "d").header()).get("toast");

        assertTrue(!toast.has("id") && !toast.has("variant"));
    }
}
