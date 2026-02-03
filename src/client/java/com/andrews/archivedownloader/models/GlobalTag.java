package com.andrews.archivedownloader.models;

public record GlobalTag(
    String name,
    String emoji,
    String colorWeb,
    Long colorMod,
    Boolean moderated
) {}
