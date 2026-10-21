package com.mobybank.harness.infrastructure.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A small in-process stand-in for Microsoft Entra ID, for the tests of "Sign in with Microsoft": the discovery document,
 * the signing keys, the authorization endpoint (which signs the user in at once and sends them back with a code), and the
 * token endpoint for the authorization-code grant (checking the PKCE verifier and the client secret) and the
 * refresh-token grant. The ID token carries the claims Entra's v2 tokens carry ({@code tid}, {@code oid}, {@code name},
 * {@code preferred_username}, {@code email}); the access token is whatever {@link #accessToken} says, so it can be the
 * token the fake Graph accepts.
 */
public final class FakeEntraServer implements AutoCloseable {

    public static final String TENANT = "tenant-1";
    public static final String CLIENT_ID = "client-1";
    public static final String CLIENT_SECRET = "secret-1";

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Grant(String nonce, String codeChallenge, String redirectUri) {
    }

    public record Authorize(Map<String, String> query) {
    }

    private final HttpServer server;
    private final KeyPair keys;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    public final List<Authorize> authorizeRequests = new CopyOnWriteArrayList<>();
    public final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();

    /** The signed-in person: what the ID token says about them. */
    public volatile String oid = "oid-ada";
    public volatile String name = "Ada Lovelace";
    public volatile String email = "ada.lovelace@example.com";

    /** When set, the next sign-in is refused at Microsoft (as if the user pressed "No"). */
    public volatile boolean denyNext;

    /** What the token endpoint hands out. */
    public volatile String accessToken = FakeGraphServer.GOOD_TOKEN;
    public volatile String refreshToken = "refresh-1";

    private FakeEntraServer(HttpServer server, KeyPair keys) {
        this.server = server;
        this.keys = keys;
    }

    public static FakeEntraServer start() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeEntraServer fake = new FakeEntraServer(http, generator.generateKeyPair());
            http.createContext("/", fake::handle);
            http.start();
            return fake;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** What {@code QUARKUS_OIDC_AUTH_SERVER_URL} is set to. */
    public String authority() {
        return origin() + "/" + TENANT + "/v2.0";
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // --- endpoints ---------------------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/" + TENANT + "/v2.0/.well-known/openid-configuration")) {
                discovery(exchange);
            } else if (path.equals("/" + TENANT + "/discovery/v2.0/keys")) {
                jwks(exchange);
            } else if (path.equals("/" + TENANT + "/oauth2/v2.0/authorize")) {
                authorize(exchange);
            } else if (path.equals("/" + TENANT + "/oauth2/v2.0/token")) {
                token(exchange);
            } else {
                respond(exchange, 404, "{}");
            }
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            respond(exchange, 500, "{\"error\":\"" + e + "\"}");
        }
    }

    private void discovery(HttpExchange exchange) throws IOException {
        ObjectNode doc = JSON.createObjectNode();
        doc.put("issuer", authority());
        doc.put("authorization_endpoint", origin() + "/" + TENANT + "/oauth2/v2.0/authorize");
        doc.put("token_endpoint", origin() + "/" + TENANT + "/oauth2/v2.0/token");
        doc.put("jwks_uri", origin() + "/" + TENANT + "/discovery/v2.0/keys");
        doc.putArray("response_types_supported").add("code");
        doc.putArray("subject_types_supported").add("pairwise");
        doc.putArray("id_token_signing_alg_values_supported").add("RS256");
        doc.putArray("token_endpoint_auth_methods_supported").add("client_secret_post").add("client_secret_basic");
        doc.putArray("code_challenge_methods_supported").add("S256");
        respond(exchange, 200, doc.toString());
    }

    private void jwks(HttpExchange exchange) throws IOException {
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        ObjectNode jwk = JSON.createObjectNode();
        jwk.put("kty", "RSA").put("use", "sig").put("kid", "k1").put("alg", "RS256");
        jwk.put("n", unsigned(key.getModulus())).put("e", unsigned(key.getPublicExponent()));
        ObjectNode doc = JSON.createObjectNode();
        doc.putArray("keys").add(jwk);
        respond(exchange, 200, doc.toString());
    }

    private void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
        authorizeRequests.add(new Authorize(query));
        if (denyNext) {
            denyNext = false;
            String denied = query.get("redirect_uri") + "?error=access_denied&error_description="
                    + URLEncoder.encode("AADSTS65004: The user declined to consent", StandardCharsets.UTF_8)
                    + "&state=" + URLEncoder.encode(query.getOrDefault("state", ""), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", denied);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        String code = "code-" + grants.size();
        grants.put(code, new Grant(query.get("nonce"), query.get("code_challenge"), query.get("redirect_uri")));
        String location = query.get("redirect_uri") + (query.get("redirect_uri").contains("?") ? "&" : "?") + "code=" + code
                + "&state=" + URLEncoder.encode(query.getOrDefault("state", ""), StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException, java.security.GeneralSecurityException {
        Map<String, String> body = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokenRequests.add(body);
        if (!CLIENT_SECRET.equals(body.get("client_secret")) && !hasBasicSecret(exchange)) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        ObjectNode response = JSON.createObjectNode();
        response.put("token_type", "Bearer").put("expires_in", 3600).put("access_token", accessToken);
        response.put("scope", "openid profile email offline_access Files.Read");
        response.put("refresh_token", refreshToken);
        switch (body.getOrDefault("grant_type", "")) {
            case "authorization_code" -> {
                Grant grant = grants.remove(body.get("code"));
                if (grant == null) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
                if (grant.codeChallenge() != null && !grant.codeChallenge().equals(s256(body.get("code_verifier")))) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verifier does not match\"}");
                    return;
                }
                response.put("id_token", idToken(CLIENT_ID, grant.nonce()));
            }
            case "refresh_token" -> {
                if (!refreshToken.equals(body.get("refresh_token"))) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                    return;
                }
            }
            default -> {
                respond(exchange, 400, "{\"error\":\"unsupported_grant_type\"}");
                return;
            }
        }
        respond(exchange, 200, response.toString());
    }

    // --- tokens ------------------------------------------------------------------------------------------------

    private String idToken(String audience, String nonce) throws java.security.GeneralSecurityException {
        Instant now = Instant.now();
        ObjectNode header = JSON.createObjectNode();
        header.put("alg", "RS256").put("typ", "JWT").put("kid", "k1");
        ObjectNode claims = JSON.createObjectNode();
        claims.put("iss", authority()).put("aud", audience).put("sub", "pairwise-" + oid).put("oid", oid);
        claims.put("tid", TENANT).put("name", name).put("preferred_username", email).put("email", email);
        claims.put("iat", now.getEpochSecond()).put("nbf", now.getEpochSecond()).put("exp", now.plusSeconds(3600).getEpochSecond());
        if (nonce != null) {
            claims.put("nonce", nonce);
        }
        String signingInput = base64(header.toString()) + "." + base64(claims.toString());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String unsigned(BigInteger number) {
        byte[] bytes = number.toByteArray();
        int skip = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(bytes, skip, bytes.length));
    }

    private static String s256(String verifier) throws java.security.NoSuchAlgorithmException {
        if (verifier == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private static boolean hasBasicSecret(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        return decoded.endsWith(":" + CLIENT_SECRET) || decoded.endsWith(":" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8));
    }

    private static Map<String, String> form(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
