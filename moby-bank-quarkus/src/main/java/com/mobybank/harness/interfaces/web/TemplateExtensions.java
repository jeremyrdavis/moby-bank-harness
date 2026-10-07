package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.MessageDTO;
import io.quarkus.qute.TemplateExtension;

/** Small view helpers the templates use. */
@TemplateExtension
final class TemplateExtensions {

    private TemplateExtensions() {
    }

    /** "Read 2 files, ran 1 calculation": the one-line summary of an agent message's steps. */
    static String stepSummary(MessageDTO message) {
        long reads = message.steps().stream().filter(step -> "read".equals(step.kind())).count();
        long computes = message.steps().stream().filter(step -> "compute".equals(step.kind())).count();
        StringBuilder summary = new StringBuilder();
        if (reads > 0) {
            summary.append("Read ").append(reads).append(reads > 1 ? " files" : " file");
        }
        if (computes > 0) {
            summary.append(summary.isEmpty() ? "Ran " : ", ran ").append(computes)
                    .append(computes > 1 ? " calculations" : " calculation");
        }
        return summary.toString();
    }
}
