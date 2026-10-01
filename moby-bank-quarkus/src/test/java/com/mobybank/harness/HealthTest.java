package com.mobybank.harness;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthTest {

    @Test
    void applicationIsUp() {
        given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void openApiDocumentIsServed() {
        given().when().get("/q/openapi").then().statusCode(200);
    }
}
