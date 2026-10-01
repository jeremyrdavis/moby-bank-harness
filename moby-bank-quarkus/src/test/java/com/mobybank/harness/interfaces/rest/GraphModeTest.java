package com.mobybank.harness.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The whole app with harness.documents.mode=graph, talking to a stand-in Microsoft Graph over real HTTP. */
@QuarkusTest
@QuarkusTestResource(value = FakeGraphResource.class, restrictToAnnotatedClass = true)
class GraphModeTest {

    @Test
    void theFolderLibraryComesFromOneDriveAndNothingIsPreConnected() {
        given().get("/api/folders").then().statusCode(200)
                .body("$", hasSize(2))
                .body("name", equalTo(List.of("Earnings 2026", "Client Financials")))
                .body("find { it.id == 'f-earn' }.path", equalTo("Credit Research / Coverage / Earnings 2026"))
                .body("find { it.id == 'f-earn' }.fileCount", equalTo(24))
                .body("connected", equalTo(List.of(false, false)));
    }

    @Test
    void connectingAFolderListsItsOneDriveDocumentsForAttaching() {
        given().contentType(ContentType.JSON).body(Map.of("folderIds", List.of("f-earn")))
                .post("/api/folders/connect").then().statusCode(200)
                .body("find { it.id == 'f-earn' }.connected", equalTo(true));

        given().get("/api/folders/connected/files").then().statusCode(200)
                .body("name", hasItems("Fathom_Q2_2026_10-Q.pdf", "notes.docx"))
                .body("find { it.name == 'notes.docx' }.folderName", equalTo("Earnings 2026"));
    }

    @Test
    void theDemoConversationsAreStillThere() {
        given().get("/api/sessions").then().statusCode(200).body("title", hasItems("Fathom Industrial — Q2 2026 earnings"));
    }

    @Test
    void connectingAFolderThatIsNotInOneDriveIs404() {
        given().contentType(ContentType.JSON).body(Map.of("folderIds", List.of("f1")))
                .post("/api/folders/connect").then().statusCode(404).body("error", equalTo("not_found"));
    }

    @Test
    void whenOneDriveIsDownTheApiAnswers502WithAClearError() {
        AtomicBoolean down = new AtomicBoolean(true);
        FakeGraphResource.server.override(uri -> down.get() && uri.getPath().endsWith("/root/children"),
                exchange -> com.mobybank.harness.infrastructure.graph.FakeGraphServer.json(exchange, 503,
                        "{\"error\":{\"message\":\"Service unavailable\"}}"));
        try {
            given().get("/api/folders").then().statusCode(502)
                    .body("error", equalTo("document_source_failure"), "message", containsString("HTTP 503"));
        } finally {
            down.set(false);
        }
        given().get("/api/folders").then().statusCode(200);
    }
}
