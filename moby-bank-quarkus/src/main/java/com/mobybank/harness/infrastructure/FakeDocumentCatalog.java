package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * A stand-in for OneDrive that serves the demo folder library to every user alike, so the app runs with no Microsoft
 * account. The files have real contents (the repository's {@code data/onedrive/}), so a real agent can work from them.
 */
public final class FakeDocumentCatalog implements DocumentCatalog {

    @Override
    public List<Folder> folders(UserId user) {
        return DemoData.folders();
    }

    @Override
    public List<CatalogFile> files(UserId user, FolderId folderId) {
        return DemoData.files(folderId);
    }

    /**
     * The file's real contents from the demo data on the classpath ({@code demo-data/onedrive/}, built from the
     * repository's {@code data/onedrive/}); empty for a name that is not in the library.
     */
    @Override
    public Optional<byte[]> fetch(UserId user, String fileName) {
        Optional<String> path = DemoData.resourcePath(fileName);
        if (path.isEmpty()) {
            Log.debug("Fake catalog: fetch of an unknown document");
            return Optional.empty();
        }
        try (InputStream in = resource(path.get())) {
            if (in == null) {
                throw new IllegalStateException("The demo file " + path.get() + " is missing from the classpath; "
                        + "it is copied from data/onedrive by the build (run ./mvnw process-resources)");
            }
            byte[] content = in.readAllBytes();
            Log.debugf("Fake catalog: fetched a demo document, %d bytes", content.length);
            return Optional.of(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream in = loader == null ? null : loader.getResourceAsStream(path);
        return in != null ? in : FakeDocumentCatalog.class.getClassLoader().getResourceAsStream(path);
    }
}
