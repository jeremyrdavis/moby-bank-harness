package com.mobybank.harness.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * With harness.sandbox.mode=sbx the app must start even though sbx is not installed, and a turn or move that needs
 * it must fail cleanly: the analyst is told why and the session is usable again.
 */
@QuarkusTest
@TestProfile(SbxModeProfile.class)
class SbxModeTest {

    private static String createSession(String location) {
        return given().contentType(ContentType.JSON).body(Map.of("location", location))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    private static Response awaitIdle(String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Response response = given().get("/api/sessions/" + id);
            if ("idle".equals(response.path("status"))) {
                return response;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("session " + id + " never became idle");
    }

    @Test
    void theAppStartsAndServesEverythingThatNeedsNoSandbox() {
        given().get("/api/me").then().statusCode(200).body("initials", equalTo("HG"));
        given().get("/api/sessions").then().statusCode(200);
        given().get("/api/folders").then().statusCode(200).body("$", hasSize(6));
    }

    @Test
    void aTurnThatCannotReachSbxExplainsWhyAndFreesTheSession() throws Exception {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("text", "Summarize the filing"))
                .post("/api/sessions/" + id + "/messages").then().statusCode(202);

        awaitIdle(id).then()
                .body("messages", hasSize(2))
                .body("messages[1].role", equalTo("assistant"))
                .body("messages[1].paragraphs[0]", containsString("could not complete this request"))
                .body("messages[1].paragraphs[0]", containsString("sbx-binary-that-is-not-installed"));

        // the session is usable again
        given().contentType(ContentType.JSON).body(Map.of("text", "try again"))
                .post("/api/sessions/" + id + "/messages").then().statusCode(202);
        awaitIdle(id).then().body("messages", hasSize(4));
    }

    @Test
    void aMoveThatCannotReachSbxLeavesTheSessionWhereItWas() throws Exception {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                .post("/api/sessions/" + id + "/move").then().statusCode(202);

        awaitIdle(id).then().body("location", equalTo("local"), "moveTarget", nullValue());
    }
}
