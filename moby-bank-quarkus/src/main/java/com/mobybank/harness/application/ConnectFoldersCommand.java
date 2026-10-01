package com.mobybank.harness.application;

import java.util.List;

/** Asks to connect the folders with these ids. Folders that are already connected are ignored; an empty list is rejected. */
public record ConnectFoldersCommand(List<String> folderIds) {
}
