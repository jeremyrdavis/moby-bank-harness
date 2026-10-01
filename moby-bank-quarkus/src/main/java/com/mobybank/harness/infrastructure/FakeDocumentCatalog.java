package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import java.util.List;

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
}
