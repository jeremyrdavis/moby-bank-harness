package com.mobybank.harness.domain;

import java.util.List;
import java.util.Optional;

/** The document source analysts connect folders from (OneDrive in production). */
public interface DocumentCatalog {

    /**
     * Every folder that {@code user} can connect.
     *
     * @throws DocumentSourceException if the source cannot be reached
     * @throws SignInRequiredException if the source needs the user's own sign-in and they have not signed in
     */
    List<Folder> folders(UserId user);

    /**
     * The documents inside one folder as {@code user} sees it; empty if the folder is unknown.
     *
     * @throws DocumentSourceException if the source cannot be reached
     * @throws SignInRequiredException if the source needs the user's own sign-in and they have not signed in
     */
    List<CatalogFile> files(UserId user, FolderId folderId);

    /**
     * The content of a document, found by its file name among the documents {@code user} can open. Names are
     * assumed unique; if two folders hold the same name, the first folder's document is returned.
     *
     * @throws DocumentSourceException if the source cannot be reached or the file is too large to use
     * @throws SignInRequiredException if the source needs the user's own sign-in and they have not signed in
     */
    Optional<byte[]> fetch(UserId user, String fileName);
}
