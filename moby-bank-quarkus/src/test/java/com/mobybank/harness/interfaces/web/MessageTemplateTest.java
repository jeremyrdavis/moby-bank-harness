package com.mobybank.harness.interfaces.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.MessageDTO;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The note an assistant message can carry about the sandbox it came from. */
@QuarkusTest
class MessageTemplateTest {

    private static String render(String notice) {
        MessageDTO message = new MessageDTO("m1", "assistant", "", List.of(), List.of(), List.of("Leverage rose."), null,
                notice, Instant.parse("2026-10-14T12:00:00Z"));
        return Templates.messages(List.of(message)).render();
    }

    @Test
    void aNoticeIsShownAsANoteAboveTheReply() {
        String html = render("The cloud sandbox was no longer there, so a new one was started.");

        assertTrue(html.contains("<div class=\"notice\" role=\"note\">"), html);
        assertTrue(html.contains("The cloud sandbox was no longer there, so a new one was started."), html);
        assertTrue(html.indexOf("class=\"notice\"") < html.indexOf("<p>Leverage rose.</p>"), "above the reply");
    }

    @Test
    void aMessageWithoutANoticeHasNoNote() {
        assertFalse(render(null).contains("class=\"notice\""));
    }

    @Test
    void aNoticeIsEscapedSoItCannotInjectMarkup() {
        String html = render("<script>alert(1)</script>");

        assertFalse(html.contains("<script>alert"), html);
        assertTrue(html.contains("&lt;script&gt;"), html);
    }
}
