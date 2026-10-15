package com.mobybank.harness.interfaces.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What the analyst sees when a cloud sandbox is lost and when a move is refused, with the fakes that do both. */
@QuarkusTest
@TestProfile(FaultyFakesProfile.class)
class FaultyFakesTest {

    private static String createSession(String location) {
        return given().contentType(ContentType.JSON).body(Map.of("location", location))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    private static Response send(String id, String text) {
        return given().multiPart("text", text).post("/ui/conversations/" + id + "/messages");
    }

    @Test
    void theTurnAfterTheCloudSandboxWasLostShowsANoticeThatSurvivesAReload() {
        String id = createSession("cloud");

        send(id, "first").then().statusCode(200).body(not(containsString("class=\"notice\"")));
        send(id, "second").then().statusCode(200).body(containsString("class=\"notice\" role=\"note\""))
                .body(containsString("The cloud sandbox was no longer there"))
                .body(containsString("so a new cloud sandbox was started from the conversation history"));

        given().get("/api/sessions/" + id).then().body("messages", hasSize(4))
                .body("messages[1].notice", nullValue())
                .body("messages[3].notice", notNullValue())
                .body("messages[3].paragraphs", hasSize(1));
        given().get("/ui/conversations/" + id).then().body(containsString("The cloud sandbox was no longer there"));

        send(id, "third").then().body(not(containsString("class=\"notice\"")));
    }

    @Test
    void aLocalSessionNeverGetsTheCloudNotice() {
        String id = createSession("local");
        send(id, "one").then().statusCode(200);
        send(id, "two").then().statusCode(200).body(not(containsString("class=\"notice\"")));
    }

    @Test
    void aRefusedMoveBackToLocalLeavesTheSessionInTheCloudWithAnErrorToast() {
        String id = createSession("cloud");

        given().post("/ui/conversations/" + id + "/move?to=local").then().statusCode(200)
                .header("HX-Trigger", containsString("\"variant\":\"error\""))
                .header("HX-Trigger", containsString("Move failed"))
                .header("HX-Trigger", containsString("refused the move to local"));

        given().get("/api/sessions/" + id).then().body("location", equalTo("cloud")).body("status", equalTo("idle"));
    }

    @Test
    void movingToTheCloudStillWorks() {
        String id = createSession("local");
        given().post("/ui/conversations/" + id + "/move?to=cloud").then().statusCode(200)
                .header("HX-Trigger", containsString("Session moved to cloud"));
    }
}
