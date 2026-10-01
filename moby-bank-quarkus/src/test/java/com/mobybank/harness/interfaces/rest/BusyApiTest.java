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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** With slow fakes, a session stays busy long enough to check the 409 responses. */
@QuarkusTest
@TestProfile(SlowFakesProfile.class)
class BusyApiTest {

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
    void whileTheAgentIsWorkingNothingElseIsAccepted() throws Exception {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("text", "first"))
                .post("/api/sessions/" + id + "/messages").then().statusCode(202);

        given().get("/api/sessions/" + id).then().body("status", equalTo("running"));
        given().contentType(ContentType.JSON).body(Map.of("text", "second"))
                .post("/api/sessions/" + id + "/messages").then().statusCode(409)
                .body("error", equalTo("conflict"), "message", containsString("RUNNING"));
        given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                .post("/api/sessions/" + id + "/move").then().statusCode(409)
                .body("error", equalTo("conflict"));

        awaitIdle(id).then().body("messages", hasSize(2), "location", equalTo("local"));
    }

    @Test
    void whileASessionMovesNothingElseIsAccepted() throws Exception {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                .post("/api/sessions/" + id + "/move").then().statusCode(202);

        given().get("/api/sessions/" + id).then()
                .body("status", equalTo("moving"), "moveTarget", equalTo("cloud"), "location", equalTo("local"));
        given().contentType(ContentType.JSON).body(Map.of("text", "hello"))
                .post("/api/sessions/" + id + "/messages").then().statusCode(409)
                .body("message", containsString("MOVING"));
        given().contentType(ContentType.JSON).body(Map.of("target", "local"))
                .post("/api/sessions/" + id + "/move").then().statusCode(409);

        awaitIdle(id).then().body("location", equalTo("cloud"), "moveTarget", nullValue(), "messages",
                equalTo(List.of()));
    }
}
