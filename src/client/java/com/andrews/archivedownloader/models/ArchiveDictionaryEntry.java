package com.andrews.archivedownloader.models;

import java.util.List;

public record ArchiveDictionaryEntry(
    String id,
    List<String> terms,
    String summary,
    String definitionMarkdown,
    List<ArchiveDictionaryReference> references,
    List<String> referencedByCodes,
    List<ArchiveDictionaryReferencedPost> referencedByPosts,
    String threadURL,
    String statusURL,
    long updatedAt
) {
    public ArchiveDictionaryEntry {
        terms = terms != null ? terms : List.of();
        references = references != null ? references : List.of();
        referencedByCodes = referencedByCodes != null ? referencedByCodes : List.of();
        referencedByPosts = referencedByPosts != null ? referencedByPosts : List.of();
        summary = summary != null ? summary : "";
        definitionMarkdown = definitionMarkdown != null ? definitionMarkdown : "";
        threadURL = threadURL != null ? threadURL : "";
        statusURL = statusURL != null ? statusURL : "";
    }
}
