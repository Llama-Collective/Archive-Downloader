package com.andrews.archivedownloader.models;

public record ArchiveDictionaryReferencedPost(
    String id,
    String title,
    String code,
    String channelCode,
    String channelName,
    String channelPath,
    long updatedAt,
    long archivedAt
) {
    public ArchiveDictionaryReferencedPost {
        id = id != null ? id : "";
        title = title != null ? title : "";
        code = code != null ? code : "";
        channelCode = channelCode != null ? channelCode : "";
        channelName = channelName != null ? channelName : "";
        channelPath = channelPath != null ? channelPath : "";
    }
}
