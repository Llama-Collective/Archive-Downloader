package com.andrews.st2downloader.models;

public record GlobalTag(
    String name,
    String emoji,
    String colorWeb,
    Long colorMod,
    Boolean moderated
) {}
