package com.andrews.archivedownloader.models;

import java.util.List;

public record ArchiveConfigJson(
    List<GlobalTag> globalTags
) {}
