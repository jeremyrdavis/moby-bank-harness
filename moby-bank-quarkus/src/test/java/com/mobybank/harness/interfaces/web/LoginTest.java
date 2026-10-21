package com.mobybank.harness.interfaces.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Signing in, over real HTTP, with the demo users the app seeds (password: moby-demo). */
@QuarkusTest
@TestProfile(LoginModeProfile.class)
class LoginTest {

    private static final String PASSWORD = "moby-demo";
    private static final String HERMIONE = "hermione.granger@mobybank.com";
    private static final String RANA = "rana.kapoor@mobybank.com";

    private static Response signIn(String email, String password) {
        return given().redirects().follow(false).contentType(ContentType.URLENC)
                .formParam("email", email).formParam("password", password).post("/auth/login");
    }

    /** Signs in and returns the value of the session cookie. */
    private static String cookieOf(String email) {
        Response response = signIn(email, PASSWORD);
        assertEquals(303, response.statusCode());
        return response.cookie(AuthFilter.COOKIE);
    }

    private static String createSession(String cookie) {
        return given().cookie(AuthFilter.COOKIE, cookie).contentType(ContentType.JSON).body(Map.of("location", "local"))
                .post("/api/sessions").then().statusCode(201).extract().path("id");
    }

    // --- nobody signed in --------------------------------------------------------------------------------------

    @Test
    void theSignInPageIsPublic() {
        given().get("/login.html").then().statusCode(200).contentType(containsString("text/html"))
                .body(containsString("Sign in to Analyst Agent"))
                .body(containsString("action=\"/auth/login\""))
                .body(containsString("name=\"email\"")).body(containsString("name=\"password\""));
    }

    @Test
    void thePageSendsAnonymousVisitorsToTheSignInPage() {
        given().redirects().follow(false).get("/").then().statusCode(303).header("Location", containsString("/login.html"));
        given().redirects().follow(false).get("/index.html").then().statusCode(303)
                .header("Location", containsString("/login.html"));
    }

    @Test
    void theApiAnswers401WithoutASession() {
        given().get("/api/sessions").then().statusCode(401).body("error", equalTo("unauthorized"));
        given().get("/api/me").then().statusCode(401);
        given().get("/api/folders").then().statusCode(401);
        given().get("/api/sessions/00000000-0000-0000-0000-000000000000/events").then().statusCode(401);
    }

    @Test
    void thePageFragmentsAnswer401WithARedirectToTheSignInPage() {
        given().get("/ui/conversations").then().statusCode(401).header("HX-Redirect", "/login.html");
        given().get("/ui/user").then().statusCode(401).header("HX-Redirect", "/login.html");
    }

    @Test
    void aMadeUpSessionCookieIsNotASession() {
        given().cookie(AuthFilter.COOKIE, "not-a-real-token").get("/api/me").then().statusCode(401);
    }

    @Test
    void theSignInPageOffersMicrosoftOnlyWhenItIsSetUp() {
        given().get("/auth/options").then().statusCode(200).body("microsoft", equalTo(false))
                .body("password", equalTo(true));
        given().redirects().follow(false).get("/auth/microsoft").then().statusCode(303)
                .header("Location", containsString("/login.html?error=unavailable"));
    }

    // --- signing in with a password ---------------------------------------------------------------------------

    @Test
    void theSeededAnalystCanSignInAndGetsAHardenedSessionCookie() {
        Response response = signIn(HERMIONE, PASSWORD);

        response.then().statusCode(303).header("Location", containsString("/"));
        String setCookie = response.header("Set-Cookie");
        assertTrue(setCookie.startsWith(AuthFilter.COOKIE + "="), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.toLowerCase().contains("samesite=lax"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
    }

    @Test
    void theDeveloperAccountSignsInWithItsOwnPassword() {
        Response ok = signIn("jeremy.davis@docker.com", "myawesomepassword");

        ok.then().statusCode(303).header("Location", containsString("/"));
        String cookie = ok.cookie(AuthFilter.COOKIE);
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/me").then().statusCode(200)
                .body("name", equalTo("Jeremy Davis")).body("initials", equalTo("JD")).body("role", equalTo("Developer"));
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/sessions").then().statusCode(200).body("$", hasSize(0));
        signIn("jeremy.davis@docker.com", PASSWORD).then().header("Location", containsString("error=credentials"));
    }

    @Test
    void theEmailMayBeTypedInAnyCase() {
        assertEquals(303, signIn("  Hermione.Granger@MobyBank.com ", PASSWORD).statusCode());
    }

    @Test
    void aSignedInAnalystReachesThePageTheApiAndTheirCard() {
        String cookie = cookieOf(HERMIONE);

        given().cookie(AuthFilter.COOKIE, cookie).get("/").then().statusCode(200)
                .body(containsString("<meta name=\"api-base\" content=\"/ui\">"));
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/me").then().statusCode(200)
                .body("name", equalTo("Hermione Granger")).body("initials", equalTo("HG"))
                .body("role", equalTo("Credit Research"));
        given().cookie(AuthFilter.COOKIE, cookie).get("/ui/user").then().statusCode(200)
                .body(containsString("Hermione Granger")).body(containsString("action=\"/auth/logout\""));
    }

    @Test
    void aWrongPasswordGoesBackToTheSignInPageWithoutASession() {
        Response response = signIn(HERMIONE, "wrong");

        response.then().statusCode(303).header("Location", containsString("/login.html?error=credentials"));
        assertEquals(null, response.cookie(AuthFilter.COOKIE));
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAsAWrongPassword() {
        signIn("nobody@mobybank.com", PASSWORD).then().statusCode(303)
                .header("Location", containsString("/login.html?error=credentials"));
    }

    @Test
    void tooManyFailuresLockTheEmailOut() {
        String email = "lockout.test@mobybank.com";
        for (int i = 0; i < 5; i++) {
            signIn(email, "wrong").then().header("Location", containsString("error=credentials"));
        }

        signIn(email, "wrong").then().statusCode(303).header("Location", containsString("error=locked"));
    }

    @Test
    void signingOutEndsTheSession() {
        String cookie = cookieOf(HERMIONE);
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/me").then().statusCode(200);

        Response logout = given().redirects().follow(false).cookie(AuthFilter.COOKIE, cookie).post("/auth/logout");

        logout.then().statusCode(303).header("Location", containsString("/login.html"));
        assertTrue(logout.header("Set-Cookie").contains("Max-Age=0"), logout.header("Set-Cookie"));
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/me").then().statusCode(401);
    }

    // --- who owns what -----------------------------------------------------------------------------------------

    @Test
    void eachAnalystSeesOnlyTheirOwnConversations() {
        String hermione = cookieOf(HERMIONE);
        String rana = cookieOf(RANA);

        List<String> hermionesTitles = given().cookie(AuthFilter.COOKIE, hermione).get("/api/sessions").then()
                .statusCode(200).extract().path("title");
        assertTrue(hermionesTitles.contains("Fathom Industrial — Q2 2026 earnings"), hermionesTitles.toString());
        given().cookie(AuthFilter.COOKIE, rana).get("/api/sessions").then().statusCode(200).body("$", hasSize(0));

        String ranas = createSession(rana);
        given().cookie(AuthFilter.COOKIE, rana).get("/api/sessions").then().body("id", hasItem(ranas));
        given().cookie(AuthFilter.COOKIE, hermione).get("/api/sessions").then().body("id", not(hasItem(ranas)));
    }

    @Test
    void anotherAnalystsConversationIsNotFoundForEveryRoute() {
        String ranas = createSession(cookieOf(RANA));
        String hermione = cookieOf(HERMIONE);

        given().cookie(AuthFilter.COOKIE, hermione).get("/api/sessions/" + ranas).then().statusCode(404);
        given().cookie(AuthFilter.COOKIE, hermione).get("/api/sessions/" + ranas + "/events").then().statusCode(404);
        given().cookie(AuthFilter.COOKIE, hermione).contentType(ContentType.JSON).body(Map.of("text", "hi"))
                .post("/api/sessions/" + ranas + "/messages").then().statusCode(404);
        given().cookie(AuthFilter.COOKIE, hermione).contentType(ContentType.JSON).body(Map.of("target", "cloud"))
                .post("/api/sessions/" + ranas + "/move").then().statusCode(404);
        given().cookie(AuthFilter.COOKIE, hermione).header("HX-Request", "true").get("/ui/conversations/" + ranas)
                .then().statusCode(404);
        given().cookie(AuthFilter.COOKIE, hermione).header("HX-Request", "true")
                .post("/ui/conversations/" + ranas + "/run?at=cloud").then().statusCode(404);
    }

    @Test
    void theConnectedFoldersBelongToTheAnalystToo() {
        given().cookie(AuthFilter.COOKIE, cookieOf(HERMIONE)).get("/api/folders").then().statusCode(200)
                .body("findAll { it.connected }.id", hasItem("f1"));
        given().cookie(AuthFilter.COOKIE, cookieOf(RANA)).get("/api/folders").then().statusCode(200)
                .body("findAll { it.connected }", hasSize(0));
    }

    // --- requests that change something ------------------------------------------------------------------------

    @Test
    void aFormPostFromAnotherSiteIsRefusedEvenWithTheCookie() {
        String cookie = cookieOf(HERMIONE);
        String own = createSession(cookie);

        // the JSON endpoints do not take a form at all
        given().cookie(AuthFilter.COOKIE, cookie).contentType(ContentType.URLENC).formParam("location", "cloud")
                .post("/api/sessions").then().statusCode(415);
        // the page's endpoints take forms, so they insist on the header only the page sends
        given().cookie(AuthFilter.COOKIE, cookie).contentType(ContentType.URLENC).post("/ui/conversations")
                .then().statusCode(403);
        given().cookie(AuthFilter.COOKIE, cookie).multiPart("text", "hello")
                .post("/ui/conversations/" + own + "/messages").then().statusCode(403);
        given().cookie(AuthFilter.COOKIE, cookie).get("/api/sessions/" + own).then().statusCode(200)
                .body("messages", hasSize(0));
    }

    @Test
    void thePageItselfMayPost() {
        given().cookie(AuthFilter.COOKIE, cookieOf(HERMIONE)).header("HX-Request", "true")
                .post("/ui/conversations").then().statusCode(200);
    }
}
