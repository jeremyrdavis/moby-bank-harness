package com.mobybank.harness.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApiTest {

    @TestHTTPResource("/")
    URL base;

    // --- helpers -----------------------------------------------------------------------------------------------

    private static String createSession(String location) {
        return given().contentType(ContentType.JSON).body(Map.of("location", location))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> message(String text, Object... files) {
        return Map.of("text", text, "files", List.of(files));
    }

    private static Map<String, String> file(String name, String source) {
        return Map.of("name", name, "source", source);
    }

    /** Polls the session until it is idle again (the fake adapters finish at once in the test profile). */
    private static Response awaitIdle(String id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Response response = given().get("/api/sessions/" + id);
            if ("idle".equals(response.path("status"))) {
                return response;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("session " + id + " never became idle");
    }

    private URI sse(String id) throws Exception {
        return base.toURI().resolve("/api/sessions/" + id + "/events");
    }

    // --- user and history --------------------------------------------------------------------------------------

    @Test
    void meDescribesTheAnalyst() {
        given().get("/api/me").then().statusCode(200)
                .body("name", equalTo("Hermione Granger"), "role", equalTo("Credit Research"),
                        "initials", equalTo("HG"));
    }

    @Test
    void theSeededHistoryIsGroupedLikeThePrototype() {
        given().get("/api/sessions").then().statusCode(200)
                .body("title", hasItems("Fathom Industrial — Q2 2026 earnings", "Kestrel Foods — earnings release review",
                        "Segment revenue bridge FY25 → FY26"))
                .body("group", hasItems("Today", "Previous 7 days", "Earlier"))
                .body("find { it.title == 'Kestrel Foods — earnings release review' }.location", equalTo("cloud"));
    }

    @Test
    void aSeededSessionOpensWithItsMessagesStepsAndTable() {
        String id = given().get("/api/sessions").then().extract()
                .path("find { it.title == 'Fathom Industrial — Q2 2026 earnings' }.id");

        given().get("/api/sessions/" + id).then().statusCode(200)
                .body("messages", hasSize(4))
                .body("messages[0].role", equalTo("user"))
                .body("messages[0].files.name", hasItem("Fathom_Q2_2026_10-Q.pdf"))
                .body("messages[1].role", equalTo("assistant"))
                .body("messages[1].steps", hasSize(5))
                .body("messages[1].steps[0].kind", equalTo("read"))
                .body("messages[1].table.cols", hasItems("Metric", "Q2 2026"))
                .body("messages[3].table", nullValue())
                .body("files.name", hasItem("Industrials_Peer_Comps_Q2.xlsx"));
    }

    // --- create and open ---------------------------------------------------------------------------------------

    @Test
    void creatingASessionReturns201WithItsLocation() {
        given().contentType(ContentType.JSON).body(Map.of("location", "cloud"))
                .post("/api/sessions").then().statusCode(201)
                .header("Location", containsString("/api/sessions/"))
                .body("location", equalTo("cloud"), "status", equalTo("idle"), "title", equalTo("New conversation"),
                        "messages", empty());
    }

    @Test
    void anEmptyJsonBodyStartsALocalSession() {
        given().contentType(ContentType.JSON).body("{}").post("/api/sessions").then().statusCode(201)
                .body("location", equalTo("local"));
    }

    @Test
    void anUnknownLocationIsABadRequest() {
        given().contentType(ContentType.JSON).body(Map.of("location", "mars")).post("/api/sessions").then()
                .statusCode(400).body("error", equalTo("bad_request"), "message", containsString("mars"));
    }

    @Test
    void anUnknownSessionIs404AndAMalformedIdIs400() {
        given().get("/api/sessions/5f1c6e0e-0000-4000-8000-000000000000").then().statusCode(404)
                .body("error", equalTo("not_found"));
        given().get("/api/sessions/nope").then().statusCode(400).body("error", equalTo("bad_request"));
    }

    // --- messages ----------------------------------------------------------------------------------------------

    @Test
    void sendingAMessageReturns202AndTheAgentAnswersInTheBackground() throws Exception {
        String id = createSession("local");

        given().contentType(ContentType.JSON)
                .body(message("Summarize the filing", file("Fathom_Q2_2026_10-Q.pdf", "onedrive")))
                .post("/api/sessions/" + id + "/messages").then().statusCode(202)
                .body("role", equalTo("user"), "text", equalTo("Summarize the filing"),
                        "files[0].source", equalTo("onedrive"));

        awaitIdle(id).then()
                .body("title", equalTo("Summarize the filing"))
                .body("messages", hasSize(2))
                .body("messages[1].role", equalTo("assistant"))
                .body("messages[1].steps.kind", equalTo(List.of("read", "compute")))
                .body("messages[1].paragraphs[0]", containsString("Fathom_Q2_2026_10-Q.pdf"))
                .body("messages[1].table.rows", hasSize(3));
    }

    @Test
    void anEmptyMessageIsABadRequestAndLeavesTheSessionIdle() {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("text", "  ")).post("/api/sessions/" + id + "/messages")
                .then().statusCode(400).body("error", equalTo("bad_request"));
        given().get("/api/sessions/" + id).then().body("status", equalTo("idle"));
    }

    @Test
    void anUnknownFileSourceIsABadRequest() {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(message("hi", file("a.pdf", "dropbox")))
                .post("/api/sessions/" + id + "/messages").then().statusCode(400)
                .body("message", containsString("dropbox"));
    }

    @Test
    void messagingAnUnknownSessionIs404() {
        given().contentType(ContentType.JSON).body(message("hi"))
                .post("/api/sessions/5f1c6e0e-0000-4000-8000-000000000000/messages").then().statusCode(404);
    }

    // --- moves -------------------------------------------------------------------------------------------------

    @Test
    void movingReturns202InTheMovingStateThenSettlesAtTheTarget() throws Exception {
        String id = createSession("local");

        given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                .post("/api/sessions/" + id + "/move").then().statusCode(202)
                .body("status", equalTo("moving"), "moveTarget", equalTo("cloud"), "location", equalTo("local"));

        awaitIdle(id).then().body("location", equalTo("cloud"), "moveTarget", nullValue());
    }

    @Test
    void aCloudSessionMovesBackToLocal() throws Exception {
        String id = createSession("cloud");
        given().contentType(ContentType.JSON).body(Map.of("target", "local"))
                .post("/api/sessions/" + id + "/move").then().statusCode(202);
        awaitIdle(id).then().body("location", equalTo("local"));
    }

    @Test
    void movingToWhereTheSessionAlreadyRunsIsAConflict() {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body(Map.of("target", "local"))
                .post("/api/sessions/" + id + "/move").then().statusCode(409)
                .body("error", equalTo("conflict"), "message", containsString("already runs there"));
    }

    @Test
    void aMoveNeedsAKnownTarget() {
        String id = createSession("local");
        given().contentType(ContentType.JSON).body("{}").post("/api/sessions/" + id + "/move").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("target", "orbit")).post("/api/sessions/" + id + "/move")
                .then().statusCode(400).body("message", containsString("orbit"));
    }

    // --- uploads -----------------------------------------------------------------------------------------------

    @Test
    void anUploadIsStoredAndReturnedAsAReference() {
        String id = createSession("local");
        given().multiPart("file", "notes.docx", "hello".getBytes(), "application/octet-stream")
                .post("/api/sessions/" + id + "/uploads").then().statusCode(201)
                .body("name", equalTo("notes.docx"), "source", equalTo("upload"));
    }

    @Test
    void anUploadedFileNameCannotCarryAPath() {
        String id = createSession("local");
        given().multiPart("file", "../../etc/passwd", "x".getBytes(), "application/octet-stream")
                .post("/api/sessions/" + id + "/uploads").then().statusCode(201).body("name", equalTo("passwd"));
    }

    @Test
    void anUploadWithoutTheFilePartIsABadRequest() {
        String id = createSession("local");
        given().multiPart("other", "value").post("/api/sessions/" + id + "/uploads").then().statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void uploadingToAnUnknownSessionIs404() {
        given().multiPart("file", "a.txt", "x".getBytes(), "text/plain")
                .post("/api/sessions/5f1c6e0e-0000-4000-8000-000000000000/uploads").then().statusCode(404);
    }

    // --- folders -----------------------------------------------------------------------------------------------

    @Test
    void theLibraryListsSixFoldersWithTheFirstThreeConnected() {
        given().get("/api/folders").then().statusCode(200)
                .body("$", hasSize(6))
                .body("find { it.id == 'f1' }.connected", equalTo(true))
                .body("find { it.id == 'f1' }.fileCount", equalTo(24))
                .body("find { it.id == 'f6' }.connected", equalTo(false));
    }

    @Test
    void connectingAFolderMakesItsFilesAttachable() {
        given().contentType(ContentType.JSON).body(Map.of("folderIds", List.of("f4")))
                .post("/api/folders/connect").then().statusCode(200)
                .body("find { it.id == 'f4' }.connected", equalTo(true));

        given().get("/api/folders/connected/files").then().statusCode(200)
                .body("name", hasItems("Fathom_Q2_2026_10-Q.pdf", "Harborline_Covenant_Model_v3.xlsx"))
                .body("find { it.name == 'Harborline_Covenant_Model_v3.xlsx' }.folderName", equalTo("Covenant Models"))
                .body("find { it.name == 'Harborline_Covenant_Model_v3.xlsx' }.folderPath",
                        equalTo("Risk / Credit / Covenant Models"));
    }

    @Test
    void connectingAnUnknownFolderIs404AndNothingIsChanged() {
        given().contentType(ContentType.JSON).body(Map.of("folderIds", List.of("f6", "nope")))
                .post("/api/folders/connect").then().statusCode(404).body("error", equalTo("not_found"));
        given().get("/api/folders").then().body("find { it.id == 'f6' }.connected", equalTo(false));
    }

    @Test
    void connectingNothingIsABadRequest() {
        given().contentType(ContentType.JSON).body(Map.of("folderIds", List.of())).post("/api/folders/connect")
                .then().statusCode(400);
        given().contentType(ContentType.JSON).body("{}").post("/api/folders/connect").then().statusCode(400);
    }

    // --- live events -------------------------------------------------------------------------------------------

    @Test
    void theEventStreamFollowsAMessageFromThinkingToTheAnswer() throws Exception {
        String id = createSession("local");
        try (SseReader stream = new SseReader(sse(id))) {
            assertEquals("ready", stream.next(Duration.ofSeconds(5)).name());

            given().contentType(ContentType.JSON)
                    .body(message("Summarize", file("Fathom_Q2_2026_10-Q.pdf", "onedrive")))
                    .post("/api/sessions/" + id + "/messages").then().statusCode(202);

            List<SseReader.Event> seen = stream.readUntil(
                    e -> e.name().equals("message") && e.data().contains("\"role\":\"assistant\""),
                    Duration.ofSeconds(10));

            assertEquals(List.of("message", "thinking", "step", "step", "message"),
                    seen.stream().map(SseReader.Event::name).toList());
            assertTrue(seen.get(1).data().contains("Reading 1 file on local model"), seen.get(1).data());
            assertTrue(seen.get(2).data().contains("\"kind\":\"read\""), seen.get(2).data());
            assertTrue(seen.get(3).data().contains("\"kind\":\"compute\""), seen.get(3).data());
            assertTrue(seen.get(4).data().contains("\"sessionId\":\"" + id + "\""), seen.get(4).data());
        }
    }

    @Test
    void theEventStreamFollowsAMoveToTheCloud() throws Exception {
        String id = createSession("local");
        try (SseReader stream = new SseReader(sse(id))) {
            assertEquals("ready", stream.next(Duration.ofSeconds(5)).name());

            given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                    .post("/api/sessions/" + id + "/move").then().statusCode(202);

            List<SseReader.Event> seen = stream.readUntil(e -> e.name().equals("moved"), Duration.ofSeconds(10));

            assertEquals(List.of("move-progress", "move-progress", "moved"),
                    seen.stream().map(SseReader.Event::name).toList());
            assertTrue(seen.get(0).data().contains("\"stage\":\"packaging\""), seen.get(0).data());
            assertTrue(seen.get(1).data().contains("\"stage\":\"transferring\""), seen.get(1).data());
            assertTrue(seen.get(2).data().contains("\"location\":\"cloud\""), seen.get(2).data());
        }
    }

    @Test
    void theEventStreamAcceptsAnUppercaseSessionId() throws Exception {
        String id = createSession("local");
        try (SseReader stream = new SseReader(base.toURI().resolve("/api/sessions/" + id.toUpperCase() + "/events"))) {
            assertEquals("ready", stream.next(Duration.ofSeconds(5)).name());
            given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                    .post("/api/sessions/" + id + "/move").then().statusCode(202);
            assertEquals("moved", stream.readUntil(e -> e.name().equals("moved"), Duration.ofSeconds(10))
                    .get(2).name());
        }
    }

    @Test
    void theEventStreamOnlyCarriesItsOwnSession() throws Exception {
        String mine = createSession("local");
        String other = createSession("local");
        try (SseReader stream = new SseReader(sse(mine))) {
            assertEquals("ready", stream.next(Duration.ofSeconds(5)).name());
            given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                    .post("/api/sessions/" + other + "/move").then().statusCode(202);
            given().contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                    .post("/api/sessions/" + mine + "/move").then().statusCode(202);

            List<SseReader.Event> seen = stream.readUntil(e -> e.name().equals("moved"), Duration.ofSeconds(10));
            assertTrue(seen.stream().allMatch(e -> e.data().contains(mine)), seen.toString());
        }
    }

    @Test
    void theEventStreamOfAnUnknownSessionIs404() {
        given().accept("text/event-stream").get("/api/sessions/5f1c6e0e-0000-4000-8000-000000000000/events")
                .then().statusCode(404);
    }

    // --- cross-origin access for the prototype UI --------------------------------------------------------------

    @Test
    void thePrototypeOriginMayCallTheApi() {
        given().header("Origin", "http://localhost:4173").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .options("/api/sessions").then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:4173")
                .header("Access-Control-Allow-Methods", containsString("POST"));

        given().header("Origin", "http://localhost:4173").get("/api/me").then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:4173");
    }

    @Test
    void anotherOriginIsNotAllowed() {
        given().header("Origin", "http://evil.example").header("Access-Control-Request-Method", "POST")
                .options("/api/sessions").then()
                .header("Access-Control-Allow-Origin", nullValue())
                .statusCode(not(equalTo(200)));
    }

    @Test
    void responsesAreJson() {
        given().get("/api/sessions").then().contentType(ContentType.JSON);
        given().get("/api/me").then().header("Content-Type", containsString("application/json"))
                .body("id", notNullValue());
    }
}
