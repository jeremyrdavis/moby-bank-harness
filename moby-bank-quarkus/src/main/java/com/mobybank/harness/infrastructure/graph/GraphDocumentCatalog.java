package com.mobybank.harness.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.DocumentSourceException;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads documents from OneDrive through Microsoft Graph. The library is the sub-folders of a configured folder (or of
 * the drive root); a folder's documents are its children that are files; a document is fetched by searching the
 * drive for its exact name, then downloading through the short-lived pre-authenticated URL Graph returns, which is
 * requested without the bearer token.
 *
 * <p>The bearer token is only ever sent to the configured Graph host: a paging link pointing anywhere else is
 * refused.
 */
public final class GraphDocumentCatalog implements DocumentCatalog {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 25;
    private static final String DOWNLOAD_URL = "@microsoft.graph.downloadUrl";

    private final GraphSettings settings;
    private final GraphTokenProvider tokens;
    private final HttpClient http;
    private final URI graphOrigin;

    public GraphDocumentCatalog(GraphSettings settings, GraphTokenProvider tokens, HttpClient http) {
        this.settings = settings;
        this.tokens = tokens;
        this.http = http;
        this.graphOrigin = URI.create(settings.baseUrl());
        Log.debugf("GraphDocumentCatalog: %s", this.graphOrigin);
    }

    @Override
    public List<Folder> folders() {
        String start = settings.foldersRoot().filter(path -> !path.isBlank())
                .map(path -> settings.driveUrl() + "/root:/" + encodePath(path) + ":/children")
                .orElse(settings.driveUrl() + "/root/children");
        List<JsonNode> items;
        try {
            items = pages(start + "?$select=id,name,folder,parentReference&$orderby=name&$top=" + PAGE_SIZE);
        } catch (NotFound e) {
            throw new DocumentSourceException("The folder '" + settings.foldersRoot().orElse("") + "' set in "
                    + "harness.graph.folders-root was not found in OneDrive");
        }
        List<Folder> folders = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.has("folder")) {
                folders.add(new Folder(new FolderId(item.path("id").asText()), item.path("name").asText(),
                        logicalPath(item), item.path("folder").path("childCount").asInt(0)));
            }
        }
        return List.copyOf(folders);
    }

    @Override
    public List<CatalogFile> files(FolderId folderId) {
        String url = settings.driveUrl() + "/items/" + encodeSegment(folderId.value())
                + "/children?$select=id,name,file&$orderby=name&$top=" + PAGE_SIZE;
        try {
            return pages(url).stream()
                    .filter(item -> item.has("file"))
                    .map(item -> new CatalogFile(folderId, item.path("name").asText()))
                    .toList();
        } catch (NotFound e) {
            return List.of();
        }
    }

    @Override
    public Optional<byte[]> fetch(String fileName) {
        Optional<JsonNode> hit = search(fileName);
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        String id = hit.get().path("id").asText();
        JsonNode item;
        try {
            item = get(URI.create(settings.driveUrl() + "/items/" + encodeSegment(id)
                    + "?$select=id,name,size," + DOWNLOAD_URL));
        } catch (NotFound e) {
            return Optional.empty();
        }
        long size = item.path("size").asLong(0);
        if (size > settings.maxDownloadBytes()) {
            throw new DocumentSourceException(fileName + " is " + size + " bytes, over the limit of "
                    + settings.maxDownloadBytes());
        }
        String downloadUrl = item.path(DOWNLOAD_URL).asText("");
        if (downloadUrl.isBlank()) {
            throw new DocumentSourceException("OneDrive gave no download link for " + fileName);
        }
        return Optional.of(download(URI.create(downloadUrl), fileName));
    }

    // --- search ------------------------------------------------------------------------------------------------

    /** The file with exactly this name, preferring one inside the configured folders root. */
    private Optional<JsonNode> search(String fileName) {
        // Graph wants the quotes raw and a quote inside the name doubled; only the name itself is percent-encoded.
        String url = settings.driveUrl() + "/root/search(q='" + encodeSegment(fileName.replace("'", "''"))
                + "')?$select=id,name,file,parentReference&$top=" + PAGE_SIZE;
        List<JsonNode> matches = pages(url).stream()
                .filter(item -> item.has("file") && fileName.equals(item.path("name").asText()))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        String root = settings.foldersRoot().filter(path -> !path.isBlank()).map(GraphDocumentCatalog::normalize).orElse("");
        return matches.stream()
                .filter(item -> !root.isEmpty() && normalize(parentPath(item)).startsWith(root))
                .findFirst()
                .or(() -> Optional.of(matches.get(0)));
    }

    // --- HTTP --------------------------------------------------------------------------------------------------

    private List<JsonNode> pages(String firstUrl) {
        List<JsonNode> items = new ArrayList<>();
        URI next = URI.create(firstUrl);
        for (int page = 0; next != null && page < MAX_PAGES; page++) {
            JsonNode body = get(next);
            body.path("value").forEach(items::add);
            next = body.hasNonNull("@odata.nextLink") ? sameHostOrFail(body.get("@odata.nextLink").asText()) : null;
        }
        return items;
    }

    private URI sameHostOrFail(String link) {
        URI uri = URI.create(link);
        boolean sameOrigin = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase(graphOrigin.getScheme())
                && uri.getHost() != null && uri.getHost().equalsIgnoreCase(graphOrigin.getHost())
                && effectivePort(uri) == effectivePort(graphOrigin);
        if (!sameOrigin) {
            throw new DocumentSourceException("Refusing to follow a paging link to another host: " + uri.getHost());
        }
        return uri;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private JsonNode get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(settings.requestTimeout())
                .header("Authorization", "Bearer " + tokens.token())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(), "OneDrive");
        checkStatus(response.statusCode(), response.body());
        try {
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new DocumentSourceException("OneDrive answered with something that is not JSON", e);
        }
    }

    private byte[] download(URI uri, String fileName) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.requestTimeout()).GET().build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray(), "the OneDrive download");
        if (response.statusCode() / 100 != 2) {
            throw new DocumentSourceException("Downloading " + fileName + " failed (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler, String what) {
        try {
            return http.send(request, handler);
        } catch (IOException e) {
            throw new DocumentSourceException("Could not reach " + what + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentSourceException("Interrupted while talking to " + what, e);
        }
    }

    private void checkStatus(int status, String body) {
        if (status / 100 == 2) {
            return;
        }
        if (status == 404) {
            throw new NotFound();
        }
        String detail = "";
        try {
            detail = JSON.readTree(body.isBlank() ? "{}" : body).path("error").path("message").asText("");
        } catch (IOException e) {
            // not JSON; report the status alone
        }
        String hint = status == 401 || status == 403
                ? " (check the access token or app permissions)"
                : status == 429 ? " (throttled; try again shortly)" : "";
        throw new DocumentSourceException("OneDrive answered HTTP " + status + hint
                + (detail.isBlank() ? "" : ": " + detail));
    }

    /** A 404 from Graph; callers decide whether that means "empty" or an error. */
    private static final class NotFound extends DocumentSourceException {
        NotFound() {
            super("OneDrive item not found");
        }
    }

    // --- paths -------------------------------------------------------------------------------------------------

    /** "Credit Research / Coverage / Earnings 2026", built from the item's parent path and name. */
    static String logicalPath(JsonNode item) {
        String parent = normalize(parentPath(item));
        String name = item.path("name").asText();
        String full = parent.isEmpty() ? name : parent + "/" + name;
        return String.join(" / ", full.split("/"));
    }

    /** Graph reports "/drive/root:/A/B", sometimes percent-encoded; this returns "A/B". */
    private static String parentPath(JsonNode item) {
        String path = item.path("parentReference").path("path").asText("");
        int marker = path.indexOf("root:");
        String relative = marker >= 0 ? path.substring(marker + "root:".length()) : path;
        return URLDecoder.decode(relative.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String normalize(String path) {
        String trimmed = path.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Encodes a folder path for use after {@code root:/}, keeping the slashes between segments. */
    private static String encodePath(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : normalize(path).split("/")) {
            segments.add(encodeSegment(segment));
        }
        return String.join("/", segments);
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
