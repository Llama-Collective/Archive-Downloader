package com.andrews.archivedownloader.models;

import java.util.List;

public record ArchiveReference(
    String type,
    List<String> matches,
    String id,
    String term,
    String url,
    String name,
    String code,
    String server,
    String serverName,
    String serverJoinURL,
    String channel,
    String message,
    String channelID,
    String channelName,
    String channelURL,
    UserRef user
) {
    public ArchiveReference {
        matches = matches != null ? matches : List.of();
    }

    public record UserRef(
        String username,
        String displayName
    ) {
    }
}
