package com.andrews.archivedownloader.models;

import java.util.List;
import java.util.Map;

public record ArchiveSearchResult(
    List<ArchivePostSummary> posts,
    int totalPages,
    int totalItems,
    Map<String, Integer> channelCounts,
    Map<String, Integer> tagCounts,
    Map<String, Double> semanticScores
) {
    public ArchiveSearchResult(
        List<ArchivePostSummary> posts,
        int totalPages,
        int totalItems,
        Map<String, Integer> channelCounts,
        Map<String, Integer> tagCounts
    ) {
        this(posts, totalPages, totalItems, channelCounts, tagCounts, Map.of());
    }
}
