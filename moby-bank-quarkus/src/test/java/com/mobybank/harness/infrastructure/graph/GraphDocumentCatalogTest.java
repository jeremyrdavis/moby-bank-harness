package com.mobybank.harness.infrastructure.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentSourceException;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphDocumentCatalogTest {

    private FakeGraphServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        server = FakeGraphServer.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private GraphDocumentCatalog catalog() {
        return catalog(Optional.empty(), 1024 * 1024);
    }

    private GraphDocumentCatalog catalog(Optional<String> foldersRoot, long maxDownloadBytes) {
        GraphSettings settings = new GraphSettings(server.baseUrl(), "me/drive", foldersRoot,
                Optional.of(FakeGraphServer.GOOD_TOKEN), server.tokenUrl(), Optional.empty(), Optional.empty(),
                Duration.ofSeconds(5), maxDownloadBytes);
        return new GraphDocumentCatalog(settings, new GraphTokenProvider(settings, http, Clock.systemUTC()), http);
    }

    // --- folders -----------------------------------------------------------------------------------------------

    @Test
    void theLibraryIsTheDriveRootsFoldersAcrossAllPagesSkippingFiles() {
        List<Folder> folders = catalog().folders();

        assertEquals(List.of("Earnings 2026", "Client Financials"), folders.stream().map(Folder::name).toList());
        assertEquals(new FolderId("f-earn"), folders.get(0).id());
        assertEquals(24, folders.get(0).fileCount());
        assertEquals("Credit Research / Coverage / Earnings 2026", folders.get(0).path());
        assertEquals("Corporate Banking / Clients / Client Financials", folders.get(1).path());
        assertEquals(2, server.hitsOn("/v1.0/me/drive/root/children").size(), "both pages were requested");
    }

    @Test
    void theFolderRequestAsksForOrderedSlimPages() {
        catalog().folders();
        String query = server.hitsOn("/v1.0/me/drive/root/children").get(0).rawQuery();
        assertTrue(query.contains("$select=id,name,folder,parentReference"), query);
        assertTrue(query.contains("$orderby=name"), query);
        assertTrue(query.contains("$top=200"), query);
    }

    @Test
    void everyRequestCarriesTheBearerToken() {
        catalog().folders();
        assertTrue(server.hits.stream().allMatch(h -> ("Bearer " + FakeGraphServer.GOOD_TOKEN).equals(h.authorization())));
    }

    @Test
    void aFoldersRootScopesTheLibraryToThatFoldersChildren() {
        List<Folder> folders = catalog(Optional.of("/Credit Research/Coverage/"), 1024).folders();

        assertEquals(List.of("Earnings 2026"), folders.stream().map(Folder::name).toList());
        assertEquals("/v1.0/me/drive/root:/Credit Research/Coverage:/children", server.hits.get(0).path());
    }

    @Test
    void aFoldersRootThatDoesNotExistSaysWhichSettingIsWrong() {
        DocumentSourceException e = assertThrows(DocumentSourceException.class,
                () -> catalog(Optional.of("No/Such/Folder"), 1024).folders());
        assertTrue(e.getMessage().contains("harness.graph.folders-root"), e.getMessage());
        assertTrue(e.getMessage().contains("No/Such/Folder"), e.getMessage());
    }

    @Test
    void logicalPathsAreBuiltFromTheParentPathAndDecoded() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals("Credit Research / A+B / Name", GraphDocumentCatalog.logicalPath(json.readTree(
                "{\"name\":\"Name\",\"parentReference\":{\"path\":\"/drive/root:/Credit%20Research/A+B\"}}")));
        assertEquals("Name", GraphDocumentCatalog.logicalPath(json.readTree(
                "{\"name\":\"Name\",\"parentReference\":{\"path\":\"/drive/root:\"}}")));
        assertEquals("X / Name", GraphDocumentCatalog.logicalPath(json.readTree(
                "{\"name\":\"Name\",\"parentReference\":{\"path\":\"/drives/b!abc/root:/X\"}}")));
        assertEquals("Name", GraphDocumentCatalog.logicalPath(json.readTree("{\"name\":\"Name\"}")));
    }

    // --- files -------------------------------------------------------------------------------------------------

    @Test
    void aFoldersDocumentsAreItsFilesNotItsSubfolders() {
        List<CatalogFile> files = catalog().files(new FolderId("f-earn"));

        assertEquals(List.of("Fathom_Q2_2026_10-Q.pdf", "notes.docx"), files.stream().map(CatalogFile::name).toList());
        assertTrue(files.stream().allMatch(f -> f.folderId().equals(new FolderId("f-earn"))));
    }

    @Test
    void anUnknownFolderHasNoDocuments() {
        assertTrue(catalog().files(new FolderId("nope")).isEmpty());
    }

    @Test
    void folderIdsAreEncodedInTheRequestPath() {
        catalog().files(new FolderId("a b/c"));
        assertEquals("/v1.0/me/drive/items/a%20b%2Fc/children", server.hits.get(0).rawPath());
    }

    // --- fetching content --------------------------------------------------------------------------------------

    @Test
    void aDocumentIsFoundByExactNameAndDownloadedWithoutTheBearerToken() {
        Optional<byte[]> content = catalog().fetch("Fathom_Q2_2026_10-Q.pdf");

        assertEquals("content of i-10q", new String(content.orElseThrow(), StandardCharsets.UTF_8));
        List<FakeGraphServer.Hit> downloads = server.hitsOn("/download/");
        assertEquals(1, downloads.size());
        assertEquals(null, downloads.get(0).authorization(), "the pre-authenticated URL must not get the token");
    }

    @Test
    void theFileInsideTheFoldersRootIsPreferredWhenNamesCollide() {
        Optional<byte[]> inRoot = catalog(Optional.of("Credit Research/Coverage"), 1024).fetch("notes.docx");
        assertEquals("content of i-notes", new String(inRoot.orElseThrow()));

        Optional<byte[]> anywhere = catalog().fetch("notes.docx");
        assertEquals("content of i-notes-other", new String(anywhere.orElseThrow()), "without a root, the first hit wins");
    }

    @Test
    void searchMatchesNamesExactlyAndIgnoresFolders() {
        assertTrue(catalog().fetch("Archive").isEmpty(), "a folder is not a document");
        assertTrue(catalog().fetch("does-not-exist.pdf").isEmpty());
    }

    @Test
    void anApostropheInTheNameIsDoubledInTheSearch() {
        Optional<byte[]> content = catalog().fetch("Kestrel's Q2.pdf");
        assertEquals("content of i-kestrel", new String(content.orElseThrow()));
        assertTrue(server.hitsOn("/v1.0/me/drive/root/search").get(0).path().contains("q='Kestrel''s Q2.pdf')"));
    }

    @Test
    void aFileOverTheSizeLimitIsRefusedBeforeDownloading() {
        DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> catalog().fetch("huge.bin"));
        assertTrue(e.getMessage().contains("huge.bin") && e.getMessage().contains("limit"), e.getMessage());
        assertTrue(server.hitsOn("/download/").isEmpty());
    }

    @Test
    void aFileWithoutADownloadLinkIsAnError() {
        DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> catalog().fetch("nolink.pdf"));
        assertTrue(e.getMessage().contains("no download link"), e.getMessage());
    }

    @Test
    void aFailedDownloadIsAnError() {
        server.override(uri -> uri.getPath().startsWith("/download/"), exchange -> FakeGraphServer.json(exchange, 403, "{}"));
        DocumentSourceException e = assertThrows(DocumentSourceException.class,
                () -> catalog().fetch("Fathom_Q2_2026_10-Q.pdf"));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
    }

    // --- failures ----------------------------------------------------------------------------------------------

    @Test
    void aRejectedTokenSaysToCheckTheTokenAndPermissions() {
        GraphSettings settings = new GraphSettings(server.baseUrl(), "me/drive", Optional.empty(), Optional.of("wrong"),
                server.tokenUrl(), Optional.empty(), Optional.empty(), Duration.ofSeconds(5), 1024);
        GraphDocumentCatalog catalog = new GraphDocumentCatalog(settings,
                new GraphTokenProvider(settings, http, Clock.systemUTC()), http);

        DocumentSourceException e = assertThrows(DocumentSourceException.class, catalog::folders);

        assertTrue(e.getMessage().contains("HTTP 401"), e.getMessage());
        assertTrue(e.getMessage().contains("access token"), e.getMessage());
        assertTrue(e.getMessage().contains("Access token is empty"), "Graph's own message is included: " + e.getMessage());
    }

    @Test
    void throttlingAndServerErrorsAreReportedWithTheirStatus() {
        server.override(uri -> uri.getPath().endsWith("/root/children"), exchange -> FakeGraphServer.json(exchange, 429,
                "{\"error\":{\"message\":\"Too many requests\"}}"));
        DocumentSourceException throttled = assertThrows(DocumentSourceException.class, () -> catalog().folders());
        assertTrue(throttled.getMessage().contains("HTTP 429") && throttled.getMessage().contains("throttled"),
                throttled.getMessage());

        server.override(uri -> uri.getPath().contains("/items/f-earn/children"),
                exchange -> FakeGraphServer.bytes(exchange, 502, "<html>bad gateway</html>".getBytes(StandardCharsets.UTF_8)));
        DocumentSourceException broken = assertThrows(DocumentSourceException.class,
                () -> catalog().files(new FolderId("f-earn")));
        assertTrue(broken.getMessage().contains("HTTP 502"), broken.getMessage());
    }

    @Test
    void aBodyThatIsNotJsonIsAnError() {
        server.override(uri -> uri.getPath().endsWith("/root/children"),
                exchange -> FakeGraphServer.bytes(exchange, 200, "<html>login page</html>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(DocumentSourceException.class, () -> catalog().folders());
    }

    @Test
    void anUnreachableGraphIsReported() {
        GraphSettings settings = new GraphSettings("http://127.0.0.1:1/v1.0", "me/drive", Optional.empty(),
                Optional.of("t"), server.tokenUrl(), Optional.empty(), Optional.empty(), Duration.ofSeconds(2), 1024);
        GraphDocumentCatalog catalog = new GraphDocumentCatalog(settings,
                new GraphTokenProvider(settings, http, Clock.systemUTC()), http);

        DocumentSourceException e = assertThrows(DocumentSourceException.class, catalog::folders);
        assertTrue(e.getMessage().contains("Could not reach OneDrive"), e.getMessage());
    }

    // --- security ----------------------------------------------------------------------------------------------

    @Test
    void aPagingLinkToAnotherHostIsRefusedAndNeverReceivesTheToken() throws Exception {
        List<String> stolen = new CopyOnWriteArrayList<>();
        HttpServer elsewhere = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        elsewhere.createContext("/", exchange -> {
            stolen.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            FakeGraphServer.json(exchange, 200, "{\"value\":[]}");
        });
        elsewhere.start();
        try {
            String evil = "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/v1.0/steal";
            server.override(uri -> uri.getPath().equals("/v1.0/me/drive/root/children"), exchange ->
                    FakeGraphServer.json(exchange, 200, "{\"@odata.nextLink\":\"" + evil + "\",\"value\":[]}"));

            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> catalog().folders());

            assertTrue(e.getMessage().contains("another host"), e.getMessage());
            assertTrue(stolen.isEmpty(), "the other host must never be contacted: " + stolen);
        } finally {
            elsewhere.stop(0);
        }
    }

    @Test
    void aPagingLinkOnTheSameHostButADifferentPortIsAlsoRefused() {
        server.override(uri -> uri.getPath().equals("/v1.0/me/drive/root/children"), exchange ->
                FakeGraphServer.json(exchange, 200, "{\"@odata.nextLink\":\"http://127.0.0.1:1/v1.0/x\",\"value\":[]}"));
        assertThrows(DocumentSourceException.class, () -> catalog().folders());
        assertFalse(server.hits.stream().anyMatch(h -> h.path().equals("/v1.0/x")));
    }

    @Test
    void pagingStopsAfterAReasonableNumberOfPages() {
        server.override(uri -> uri.getPath().equals("/v1.0/me/drive/root/children"), exchange ->
                FakeGraphServer.json(exchange, 200, "{\"@odata.nextLink\":\"" + server.baseUrl()
                        + "/me/drive/root/children?loop=1\",\"value\":[]}"));

        assertTrue(catalog().folders().isEmpty());
        assertEquals(25, server.hitsOn("/v1.0/me/drive/root/children").size(), "a never-ending listing is cut off");
    }
}
