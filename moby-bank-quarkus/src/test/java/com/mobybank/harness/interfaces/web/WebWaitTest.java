package com.mobybank.harness.interfaces.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import com.mobybank.harness.application.MoveSessionCommand;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionEvent.MoveFailed;
import com.mobybank.harness.application.SessionEventStream;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What the page gets when the agent or the move takes longer than {@code harness.ui.wait-timeout}. */
@QuarkusTest
@TestProfile(ShortWaitProfile.class)
class WebWaitTest {

    @Inject
    SessionApplicationService sessions;
    @Inject
    SessionEventStream events;
    @Inject
    SessionWaiter waiter;

    private static String createSession() {
        return given().contentType(ContentType.JSON).body(Map.of("location", "local"))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    private static void awaitIdle(String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if ("idle".equals(given().get("/api/sessions/" + id).path("status"))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("session " + id + " never became idle");
    }

    @Test
    void aSlowReplyGivesTheUserMessageAndAToastInsteadOfHanging() throws Exception {
        String id = createSession();

        given().multiPart("text", "Take your time").post("/ui/conversations/" + id + "/messages").then()
                .statusCode(200)
                .body(containsString("Take your time"))
                .body(not(containsString("msg-agent")))
                .header("HX-Trigger", containsString("The agent is still working"));

        awaitIdle(id);
        given().get("/api/sessions/" + id).then().body("messages.role", org.hamcrest.Matchers.hasItems("user", "assistant"));
    }

    @Test
    void whileTheAgentIsStillWorkingAnotherMessageIsRefused() throws Exception {
        String id = createSession();
        given().multiPart("text", "First").post("/ui/conversations/" + id + "/messages").then().statusCode(200);

        given().multiPart("text", "Second").post("/ui/conversations/" + id + "/messages").then().statusCode(409);

        awaitIdle(id);
    }

    @Test
    void aSlowMoveGivesAToastAndTheOpenedPaneShowsItBusy() throws Exception {
        String id = createSession();

        given().post("/ui/conversations/" + id + "/move?to=cloud").then().statusCode(200)
                .header("HX-Reswap", equalTo("none"))
                .header("HX-Trigger", containsString("The move is still running"));

        given().get("/ui/conversations/" + id).then().body(containsString("Moving the session"))
                .body(containsString("disabled"));
        awaitIdle(id);
    }

    @Test
    void aLateFailureFromThePreviousMoveDoesNotEndAMoveThatIsStillRunning() throws Exception {
        String id = createSession();

        var outcome = waiter.awaitMove(id, "cloud", () -> {
            events.publish(new MoveFailed(id, "the previous move failed")); // arriving late
            sessions.move(new MoveSessionCommand(id, "cloud"));
        });

        assertTrue(outcome.isEmpty(), "the move is still running, so there is no outcome yet: " + outcome);
        awaitIdle(id);
    }
}
