package com.mobybank.harness.interfaces.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The HTML endpoints behind index.html, over real HTTP with the fake adapters (which finish at once in tests). */
@QuarkusTest
class WebTest {

    private static String createSession() {
        return given().contentType(ContentType.JSON).body(Map.of("location", "local"))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    private static Response send(String id, String text) {
        return given().multiPart("text", text).post("/ui/conversations/" + id + "/messages");
    }

    // --- the page and its assets --------------------------------------------------------------------------------

    @Test
    void theRootServesTheHtmxPageWhichPointsItsCallsAtUi() {
        given().get("/").then().statusCode(200).contentType(containsString("text/html"))
                .body(containsString("<meta name=\"api-base\" content=\"/ui\">"))
                .body(containsString("id=\"main\""));
    }

    @Test
    void htmxAndTheFontComeFromTheClasspathNotACdn() {
        String page = given().get("/").asString();
        assertTrue(!page.contains("unpkg.com") && !page.contains("googleapis"), "the page must not use a CDN");

        given().get("/webjars/htmx.org/2.0.11/dist/htmx.min.js").then().statusCode(200);
        given().get("/_static/at/fontsource/manrope/5.3.0/400.css").then().statusCode(200)
                .body(containsString("font-family: 'Manrope'"));
    }

    @Test
    void theHtmlEndpointsAreNotPartOfTheOpenApiContract() {
        given().get("/q/openapi").then().statusCode(200).body(not(containsString("/ui/")));
    }

    // --- history and the open conversation ----------------------------------------------------------------------

    @Test
    void theHistoryGroupsConversationsAndMarksTheOpenOne() {
        String id = createSession();

        given().cookie("moby-current", id).get("/ui/conversations").then().statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("class=\"eyebrow\">Today<"))
                .body(containsString("hx-get=\"/api/conversations/" + id + "\""))
                .body(not(containsString("<html")));
    }

    @Test
    void exactlyOneConversationIsMarkedCurrent() {
        String html = given().get("/ui/conversations").asString();
        long current = html.split("aria-current=\"true\"", -1).length - 1;
        assertTrue(current == 1, "expected one current conversation, found " + current);
    }

    @Test
    void openingAConversationReturnsItsPaneRemembersItAndRefreshesTheHistory() {
        String id = createSession();

        given().get("/ui/conversations/" + id).then().statusCode(200)
                .body(containsString("hx-post=\"/api/conversations/" + id + "/messages\""))
                .body(containsString("What should we analyze?"))
                .body(containsString("data-move-cloud"))
                .body(containsString("id=\"thinking\""))
                .body(not(containsString("<html")))
                .cookie("moby-current", id)
                .header("HX-Trigger", containsString("conversations-changed"));
    }

    @Test
    void aSeededConversationShowsItsMessagesStepsAndTable() {
        String id = given().get("/api/sessions").then().extract().path("find { it.title != 'New conversation' }.id");
        String pane = given().get("/ui/conversations/" + id).asString();

        assertTrue(pane.contains("class=\"msg-user\"") && pane.contains("class=\"msg-agent\""), pane);
        assertTrue(pane.contains("details class=\"steps\""), "the agent's steps should be shown");
    }

    @Test
    void currentFallsBackWhenTheCookieIsStaleOrMalformed() {
        given().cookie("moby-current", "not-a-session").get("/ui/conversations/current").then().statusCode(200)
                .body(containsString("id=\"composer\""));
        given().cookie("moby-current", "00000000-0000-0000-0000-000000000000").get("/ui/conversations/current")
                .then().statusCode(200).body(containsString("id=\"composer\""));
    }

    @Test
    void currentOpensTheConversationTheCookieNames() {
        String id = createSession();

        given().cookie("moby-current", id).get("/ui/conversations/current").then().statusCode(200)
                .body(containsString("/api/conversations/" + id + "/messages"));
    }

    @Test
    void anUnknownConversationIsNotFound() {
        given().get("/ui/conversations/00000000-0000-0000-0000-000000000000").then().statusCode(404);
    }

    @Test
    void newConversationAlwaysCreatesASession() {
        int before = given().get("/api/sessions").path("size()");

        String first = given().post("/ui/conversations").then().statusCode(200)
                .header("HX-Trigger", containsString("conversations-changed"))
                .body(containsString("What should we analyze?")).extract().cookie("moby-current");
        String second = given().post("/ui/conversations").then().statusCode(200).extract().cookie("moby-current");

        assertTrue(first != null && second != null && !first.equals(second), "each click makes a new session");
        given().get("/api/sessions").then().body("size()", equalTo(before + 2));
    }

    // --- sending a message --------------------------------------------------------------------------------------

    @Test
    void sendingWaitsForTheReplyAndReturnsBothMessages() {
        String id = createSession();

        send(id, "Summarise the Q2 filing").then().statusCode(200).contentType(containsString("text/html"))
                .body(containsString("class=\"bubble\">Summarise the Q2 filing<"))
                .body(containsString("class=\"msg-agent\""))
                .header("HX-Trigger", containsString("conversations-changed"));
    }

    @Test
    void theReplyIsAlsoInTheStoredConversation() {
        String id = createSession();
        send(id, "Hello").then().statusCode(200);

        given().get("/api/sessions/" + id).then().body("messages", hasSize(2))
                .body("messages[1].role", equalTo("assistant")).body("status", equalTo("idle"));
    }

    @Test
    void sendingRemovesTheEmptyThreadPrompt() {
        send(createSession(), "Hello").then().body(containsString("id=\"empty-state\" hx-swap-oob=\"delete\""));
    }

    @Test
    void textIsEscapedSoAMessageCannotInjectMarkup() {
        String body = send(createSession(), "<script>alert(1)</script>").asString();

        assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), body);
        assertTrue(!body.contains("<script>alert"), "message text must not be output as markup");
    }

    @Test
    void anUploadedFileIsShownAsAChipAndTheAgentAnswersWithATable() {
        String id = createSession();

        Response response = given()
                .multiPart("text", "Check this")
                .multiPart("files", "Fathom_Q2.pdf", "pdf bytes".getBytes(StandardCharsets.UTF_8), "application/pdf")
                .post("/ui/conversations/" + id + "/messages");

        response.then().statusCode(200)
                .body(containsString("<span>Fathom_Q2.pdf</span><span class=\"muted\">Upload</span>"))
                .body(containsString("<table>"));
        given().get("/api/sessions/" + id).then().body("files.name", org.hamcrest.Matchers.hasItem("Fathom_Q2.pdf"));
    }

    @Test
    void aOneDriveFileIsAttachedByName() {
        String id = createSession();

        given().multiPart("text", "From the cloud drive").multiPart("onedrive", "Fathom_Q2_2026_10-Q.pdf")
                .post("/ui/conversations/" + id + "/messages").then().statusCode(200)
                .body(containsString("<span>Fathom_Q2_2026_10-Q.pdf</span><span class=\"muted\">OneDrive</span>"));
    }

    @Test
    void anEmptyFilePartFromTheBrowserIsIgnored() {
        String id = createSession();

        given().multiPart("text", "No file chosen").multiPart("files", "", new byte[0], "application/octet-stream")
                .post("/ui/conversations/" + id + "/messages").then().statusCode(200)
                .body(not(containsString("class=\"chips\"")));
    }

    @Test
    void anEmptyMessageIsRejected() {
        given().multiPart("text", "").post("/ui/conversations/" + createSession() + "/messages").then().statusCode(400);
    }

    @Test
    void sendingToAnUnknownConversationIsNotFound() {
        send("00000000-0000-0000-0000-000000000000", "Hello").then().statusCode(404);
    }

    // --- moving -------------------------------------------------------------------------------------------------

    @Test
    void movingWaitsForTheMoveThenReturnsThePaneAndASuccessToastThatReplacesTheLoadingOne() {
        String id = createSession();

        Response response = given().post("/ui/conversations/" + id + "/move?to=cloud");

        response.then().statusCode(200).body(containsString("Running in cloud")).body(containsString("dot cloud"))
                .body(not(containsString("data-move-cloud")))
                .header("HX-Trigger", containsString("\"id\":\"move-cloud\""))
                .header("HX-Trigger", containsString("Session moved to cloud"))
                .header("HX-Trigger", containsString("conversations-changed"));
        given().get("/api/sessions/" + id).then().body("location", equalTo("cloud")).body("status", equalTo("idle"));
    }

    @Test
    void movingToWhereTheSessionAlreadyIsAnswersWithAToastAndSwapsNothing() {
        String id = createSession();

        given().post("/ui/conversations/" + id + "/move?to=local").then().statusCode(200)
                .header("HX-Reswap", equalTo("none"))
                .header("HX-Trigger", containsString("\"id\":\"move-cloud\""))
                .header("HX-Trigger", containsString("Could not move the session"));
    }

    @Test
    void aSessionThatMovedBackShowsTheMoveButtonAgain() {
        String id = createSession();
        given().post("/ui/conversations/" + id + "/move?to=cloud").then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("target", "local"))
                .post("/api/sessions/" + id + "/move").then().statusCode(202);
        for (int i = 0; i < 100 && !"idle".equals(given().get("/api/sessions/" + id).path("status")); i++) {
            sleep(50);
        }

        given().get("/ui/conversations/" + id).then().body(containsString("data-move-cloud"));
    }

    // --- OneDrive -----------------------------------------------------------------------------------------------

    @Test
    void theSidebarListsConnectedFoldersWithTheirFileCounts() {
        given().get("/ui/onedrive/folders").then().statusCode(200)
                .body(containsString("OneDrive folders"))
                .body(containsString("hx-get=\"/api/onedrive/picker?mode=connect\""))
                .body(containsString("class=\"count\""))
                .body(not(containsString("<html")));
    }

    @Test
    void theConnectDialogListsEveryFolderAndDisablesTheConnectedOnes() {
        String dialog = given().get("/ui/onedrive/picker?mode=connect").then().statusCode(200).extract().asString();

        assertTrue(dialog.contains("hx-post=\"/api/onedrive/connect\""), dialog);
        assertTrue(dialog.contains("name=\"folder\""), dialog);
        assertTrue(dialog.contains("checked disabled"), "connected folders are checked and disabled");
    }

    @Test
    void theAttachDialogListsFilesFromConnectedFolders() {
        given().get("/ui/onedrive/picker?mode=attach").then().statusCode(200)
                .body(containsString("hx-post=\"/api/onedrive/attach\""))
                .body(containsString("name=\"file\" value=\"Fathom_Q2_2026_10-Q.pdf\""));
    }

    @Test
    void anUnknownPickerModeIsRejected() {
        given().get("/ui/onedrive/picker?mode=other").then().statusCode(400);
        given().get("/ui/onedrive/picker").then().statusCode(400);
    }

    @Test
    void connectingAFolderClosesTheDialogRefreshesTheSidebarAndShowsTheFolder() {
        given().contentType(ContentType.URLENC).formParam("folder", "f4").post("/ui/onedrive/connect").then()
                .statusCode(200)
                .header("HX-Trigger", containsString("close-dialog"))
                .header("HX-Trigger", containsString("folders-changed"))
                .header("HX-Trigger", containsString("Folders connected"));

        given().get("/ui/onedrive/folders").then().body(containsString("Covenant Models"));
    }

    @Test
    void connectingNothingJustClosesTheDialog() {
        given().contentType(ContentType.URLENC).post("/ui/onedrive/connect").then().statusCode(200)
                .header("HX-Trigger", containsString("close-dialog"))
                .header("HX-Trigger", not(containsString("folders-changed")));
    }

    @Test
    void connectingAnUnknownFolderIsNotFound() {
        given().contentType(ContentType.URLENC).formParam("folder", "nope").post("/ui/onedrive/connect").then()
                .statusCode(404);
    }

    @Test
    void attachingFilesReturnsChipsWithHiddenInputsAndClosesTheDialog() {
        given().contentType(ContentType.URLENC).formParam("file", "Fathom_Q2_2026_10-Q.pdf")
                .post("/ui/onedrive/attach").then().statusCode(200)
                .header("HX-Trigger", containsString("close-dialog"))
                .body(containsString("<input type=\"hidden\" name=\"onedrive\" value=\"Fathom_Q2_2026_10-Q.pdf\">"))
                .body(containsString("<button type=\"button\" aria-label=\"Remove\">"));
    }

    @Test
    void onlyFilesInConnectedFoldersCanBeAttached() {
        String body = given().contentType(ContentType.URLENC).formParam("file", "Not_In_Any_Folder.pdf")
                .formParam("file", "Fathom_Q2_2026_10-Q.pdf").post("/ui/onedrive/attach").asString();

        assertTrue(body.contains("Fathom_Q2_2026_10-Q.pdf") && !body.contains("Not_In_Any_Folder.pdf"), body);
    }

    @Test
    void attachingNothingJustClosesTheDialog() {
        given().contentType(ContentType.URLENC).post("/ui/onedrive/attach").then().statusCode(200)
                .header("HX-Trigger", containsString("close-dialog")).body(equalTo(""));
    }

    // --- the JSON API is unaffected -----------------------------------------------------------------------------

    @Test
    void theJsonApiStillAnswersJson() {
        given().get("/api/sessions").then().statusCode(200).contentType(containsString("json"))
                .body("[0].id", notNullValue());
        given().get("/api/sessions/00000000-0000-0000-0000-000000000000").then().statusCode(404)
                .contentType(containsString("json"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
