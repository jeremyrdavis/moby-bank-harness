package com.mobybank.harness.application;

/** A folder in the library. {@code fileCount} is how many items it holds and {@code connected} says whether the analyst has connected it. */
public record FolderDTO(String id, String name, String path, int fileCount, boolean connected) {
}
