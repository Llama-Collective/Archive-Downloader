package com.andrews.archivedownloader.models;

public record ArchiveImageInfo(
    String url,
    String description,
    Integer width,
    Integer height
) {
}
