package com.mobybank.harness.infrastructure.graph;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A small in-process stand-in for Microsoft Graph, the token endpoint and the pre-authenticated download host, so the
 * real HTTP code can be tested without a Microsoft account. It serves a fixed drive:
 *
 * <pre>
 * Credit Research/Coverage/Earnings 2026   (f-earn, 24 items)   Fathom_Q2_2026_10-Q.pdf, notes.docx
 * Corporate Banking/Clients/Client Financials (f-clients, 58)   Harborline_Q2.xlsx
 * readme.txt                                                    a file in the drive root
 * </pre>
 *
 * The root listing is split across two pages to exercise paging.
 */
public final class FakeGraphServer implements AutoCloseable {

    public record Hit(String method, String path, String rawPath, String rawQuery, String authorization) {
    }

    public interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    private record Rule(Predicate<URI> matches, Responder responder) {
    }

    public static final String GOOD_TOKEN = "good-token";

    private final HttpServer server;
    private final List<Rule> overrides = new CopyOnWriteArrayList<>();
    public final List<Hit> hits = new CopyOnWriteArrayList<>();
    public final List<String> tokenRequestBodies = new CopyOnWriteArrayList<>();

    private FakeGraphServer(HttpServer server) {
        this.server = server;
    }

    public static FakeGraphServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeGraphServer fake = new FakeGraphServer(server);
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String baseUrl() {
        return origin() + "/v1.0";
    }

    public String tokenUrl() {
        return origin() + "/token";
    }

    /** Answers matching requests with this responder instead of the fixed drive. */
    public void override(Predicate<URI> matches, Responder responder) {
        overrides.add(new Rule(matches, responder));
    }

    public List<Hit> hitsOn(String pathPrefix) {
        return hits.stream().filter(h -> h.path().startsWith(pathPrefix)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // --- routing -----------------------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        hits.add(new Hit(exchange.getRequestMethod(), uri.getPath(), uri.getRawPath(), uri.getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization")));
        try {
            for (Rule override : overrides) {
                if (override.matches().test(uri)) {
                    override.responder().respond(exchange);
                    return;
                }
            }
            route(exchange, uri);
        } catch (RuntimeException e) {
            json(exchange, 500, "{\"error\":{\"message\":\"" + e + "\"}}");
        }
    }

    private void route(HttpExchange exchange, URI uri) throws IOException {
        String path = uri.getPath();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();

        if (path.equals("/token")) {
            tokenRequestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(exchange, 200, "{\"access_token\":\"" + GOOD_TOKEN + "\",\"expires_in\":3600}");
            return;
        }
        if (path.startsWith("/download/")) {
            bytes(exchange, 200, ("content of " + path.substring("/download/".length())).getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!path.startsWith("/v1.0/")) {
            json(exchange, 404, "{\"error\":{\"message\":\"unknown\"}}");
            return;
        }
        if (!("Bearer " + GOOD_TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            json(exchange, 401, "{\"error\":{\"code\":\"InvalidAuthenticationToken\",\"message\":\"Access token is empty.\"}}");
            return;
        }
        String drive = "/v1.0/me/drive";
        if (path.equals(drive + "/root/children")) {
            json(exchange, 200, query.contains("page=2") ? rootPage2() : rootPage1());
        } else if (path.equals(drive + "/root:/Credit Research/Coverage:/children")) {
            json(exchange, 200, "{\"value\":[" + folderItem("f-earn", "Earnings 2026", 24, "/drive/root:/Credit%20Research/Coverage") + "]}");
        } else if (path.equals(drive + "/items/f-earn/children")) {
            json(exchange, 200, "{\"value\":[" + fileItem("i-10q", "Fathom_Q2_2026_10-Q.pdf", "/drive/root:/Credit%20Research/Coverage/Earnings%202026")
                    + "," + fileItem("i-notes", "notes.docx", "/drive/root:/Credit%20Research/Coverage/Earnings%202026")
                    + "," + folderItem("f-sub", "Archive", 3, "/drive/root:/Credit%20Research/Coverage/Earnings%202026") + "]}");
        } else if (path.equals(drive + "/items/f-clients/children")) {
            json(exchange, 200, "{\"value\":[" + fileItem("i-harbor", "Harborline_Q2.xlsx", "/drive/root:/Corporate%20Banking/Clients/Client%20Financials") + "]}");
        } else if (path.startsWith(drive + "/root/search(q='")) {
            json(exchange, 200, search(path));
        } else if (path.startsWith(drive + "/items/")) {
            item(exchange, path.substring((drive + "/items/").length()));
        } else {
            json(exchange, 404, "{\"error\":{\"code\":\"itemNotFound\",\"message\":\"not found\"}}");
        }
    }

    private String rootPage1() {
        return "{\"@odata.nextLink\":\"" + baseUrl() + "/me/drive/root/children?page=2&$top=200\",\"value\":["
                + folderItem("f-earn", "Earnings 2026", 24, "/drive/root:/Credit%20Research/Coverage") + ","
                + fileItem("i-readme", "readme.txt", "/drive/root:") + "]}";
    }

    private String rootPage2() {
        return "{\"value\":[" + folderItem("f-clients", "Client Financials", 58, "/drive/root:/Corporate Banking/Clients") + "]}";
    }

    private String search(String path) {
        String q = path.substring(path.indexOf("q='") + 3, path.lastIndexOf("')")).replace("''", "'");
        return "{\"value\":[" + switch (q) {
            case "Fathom_Q2_2026_10-Q.pdf" -> fileItem("i-10q", q, "/drive/root:/Credit%20Research/Coverage/Earnings%202026");
            case "notes.docx" -> fileItem("i-notes-other", q, "/drive/root:/Personal") + ","
                    + fileItem("i-notes", q, "/drive/root:/Credit%20Research/Coverage/Earnings%202026");
            case "Harborline_Q2.xlsx" -> fileItem("i-harbor", q, "/drive/root:/Corporate%20Banking/Clients/Client%20Financials");
            case "Kestrel's Q2.pdf" -> fileItem("i-kestrel", q, "/drive/root:/Credit%20Research/Coverage/Earnings%202026");
            case "huge.bin" -> fileItem("i-huge", q, "/drive/root:/Credit%20Research");
            case "nolink.pdf" -> fileItem("i-nolink", q, "/drive/root:/Credit%20Research");
            case "Archive" -> folderItem("f-sub", "Archive", 3, "/drive/root:/Credit%20Research/Coverage/Earnings%202026");
            default -> "";
        } + "]}";
    }

    private void item(HttpExchange exchange, String id) throws IOException {
        String downloadUrl = "\"@microsoft.graph.downloadUrl\":\"" + origin() + "/download/" + id + "\"";
        switch (id) {
            case "i-huge" -> json(exchange, 200, "{\"id\":\"i-huge\",\"name\":\"huge.bin\",\"size\":999999999999," + downloadUrl + "}");
            case "i-nolink" -> json(exchange, 200, "{\"id\":\"i-nolink\",\"name\":\"nolink.pdf\",\"size\":10}");
            case "i-10q", "i-notes", "i-notes-other", "i-harbor", "i-kestrel" ->
                    json(exchange, 200, "{\"id\":\"" + id + "\",\"name\":\"x\",\"size\":20," + downloadUrl + "}");
            default -> json(exchange, 404, "{\"error\":{\"code\":\"itemNotFound\",\"message\":\"not found\"}}");
        }
    }

    // --- JSON and HTTP helpers ---------------------------------------------------------------------------------

    private static String folderItem(String id, String name, int childCount, String parentPath) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"folder\":{\"childCount\":" + childCount
                + "},\"parentReference\":{\"path\":\"" + parentPath + "\"}}";
    }

    private static String fileItem(String id, String name, String parentPath) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name.replace("\"", "\\\"") + "\",\"file\":{\"mimeType\":\"application/octet-stream\"},"
                + "\"parentReference\":{\"path\":\"" + parentPath + "\"}}";
    }

    public static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        bytes(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    public static void bytes(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
