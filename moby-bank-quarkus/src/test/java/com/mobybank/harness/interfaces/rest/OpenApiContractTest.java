package com.mobybank.harness.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the API contract other backend implementations are built against: the operations that must exist, and the
 * openapi.yaml written at build time.
 */
@QuarkusTest
class OpenApiContractTest {

    private static final List<String> PATHS = List.of(
            "/api/me",
            "/api/sessions",
            "/api/sessions/{id}",
            "/api/sessions/{id}/messages",
            "/api/sessions/{id}/uploads",
            "/api/sessions/{id}/move",
            "/api/sessions/{id}/events",
            "/api/folders",
            "/api/folders/connect",
            "/api/folders/connected/files");

    @Test
    void everyEndpointIsDocumented() {
        given().queryParam("format", "json").get("/q/openapi").then().statusCode(200)
                .body("paths.keySet().findAll { it.startsWith('/api') }", containsInAnyOrder(PATHS.toArray()));
    }

    @Test
    void operationsUseTheExpectedMethods() {
        given().queryParam("format", "json").get("/q/openapi").then().statusCode(200)
                .body("paths.'/api/sessions'.keySet()", containsInAnyOrder("get", "post"))
                .body("paths.'/api/sessions/{id}'.keySet()", containsInAnyOrder("get"))
                .body("paths.'/api/sessions/{id}/messages'.keySet()", containsInAnyOrder("post"))
                .body("paths.'/api/sessions/{id}/move'.keySet()", containsInAnyOrder("post"))
                .body("paths.'/api/sessions/{id}/uploads'.keySet()", containsInAnyOrder("post"))
                .body("paths.'/api/sessions/{id}/events'.keySet()", containsInAnyOrder("get"))
                .body("paths.'/api/folders/connect'.keySet()", containsInAnyOrder("post"));
    }

    @Test
    void theContractSpellsOutTheAllowedValues() {
        given().queryParam("format", "json").get("/q/openapi").then().statusCode(200)
                .body("components.schemas.SessionDTO.properties.location.enum", hasItems("local", "cloud"))
                .body("components.schemas.SessionDTO.properties.status.enum", hasItems("idle", "running", "moving"))
                .body("components.schemas.MessageDTO.properties.role.enum", hasItems("user", "assistant"))
                .body("components.schemas.StepDTO.properties.kind.enum", hasItems("read", "compute"))
                .body("components.schemas.FileRefDTO.properties.source.enum", hasItems("upload", "onedrive"))
                .body("components.schemas.MoveSessionRequest.properties.target.enum", hasItems("local", "cloud"));
    }

    @Test
    void theBuildWritesOpenapiYamlWithEveryPath() throws IOException {
        Path file = Path.of("openapi.yaml");
        assertTrue(Files.exists(file), "openapi.yaml should be written at build time (module root)");
        String yaml = Files.readString(file);
        for (String path : PATHS) {
            assertTrue(yaml.contains("  " + path + ":"), "openapi.yaml is missing " + path);
        }
    }
}
