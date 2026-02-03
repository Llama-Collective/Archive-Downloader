package com.andrews.archivedownloader.models;

public record ArchivePostSummary(
	String id,
	String title,
	String channelName,
	String channelCode,
	String channelCategory,
	String channelPath,
	String entryPath,
	String code,
	String[] tags,
	String[] authors,
	long archivedAt,
	long updatedAt
) {
	public ArchivePostSummary {
		tags = tags != null ? tags : new String[0];
		authors = authors != null ? authors : new String[0];
	}
}
