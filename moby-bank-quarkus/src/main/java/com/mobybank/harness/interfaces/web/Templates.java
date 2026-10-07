package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.CatalogFileDTO;
import com.mobybank.harness.application.FileRefDTO;
import com.mobybank.harness.application.FolderDTO;
import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.SessionDTO;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import java.util.List;

/**
 * The fragments {@code index.html} swaps into itself. Each method is a template in {@code templates/}, and the build
 * checks that every expression in it exists on the parameter types.
 */
@CheckedTemplate
final class Templates {

    private Templates() {
    }

    /** Everything inside {@code <main id="main">}: header, thread and composer for one conversation. */
    static native TemplateInstance main(SessionDTO session);

    /** The sidebar's conversation history, grouped by recency. */
    static native TemplateInstance history(List<HistoryGroup> groups);

    /** Messages to append to the thread. */
    static native TemplateInstance messages(List<MessageDTO> messages);

    /** The reply to sending a message: the new messages, and removal of the empty-thread prompt. */
    static native TemplateInstance sent(List<MessageDTO> messages);

    /** The sidebar's connected OneDrive folders. */
    static native TemplateInstance folders(List<FolderDTO> folders);

    /** The dialog for connecting folders. */
    static native TemplateInstance connectPicker(List<FolderDTO> folders);

    /** The dialog for attaching files from connected folders. */
    static native TemplateInstance attachPicker(List<CatalogFileDTO> files);

    /** Chips for files chosen in the attach dialog, added to the composer. */
    static native TemplateInstance chips(List<FileRefDTO> files);
}
