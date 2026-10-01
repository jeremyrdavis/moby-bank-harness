package com.mobybank.harness.application;

/** A document in a connected folder, with the folder details the attach dialog shows. */
public record CatalogFileDTO(String folderId, String folderName, String folderPath, String name) {
}
