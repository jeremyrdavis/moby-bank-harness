package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** A stand-in for OneDrive that serves the prototype's folder library, so the app runs with no Microsoft account. */
public final class FakeDocumentCatalog implements DocumentCatalog {

    @Override
    public List<Folder> folders() {
        return DemoData.folders();
    }

    @Override
    public List<CatalogFile> files(FolderId folderId) {
        return DemoData.files(folderId);
    }

    /** Every listed document "exists" and contains a one-line placeholder. */
    @Override
    public Optional<byte[]> fetch(String fileName) {
        boolean known = DemoData.folders().stream()
                .anyMatch(folder -> DemoData.files(folder.id()).stream().anyMatch(file -> file.name().equals(fileName)));
        return known
                ? Optional.of(("Demo content of " + fileName + "\n").getBytes(StandardCharsets.UTF_8))
                : Optional.empty();
    }
}
