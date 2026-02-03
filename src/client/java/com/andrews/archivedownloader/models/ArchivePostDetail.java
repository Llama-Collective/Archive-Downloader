package com.andrews.archivedownloader.models;

import java.util.List;

public record ArchivePostDetail(
	ArchivePostSummary summary,
	List<String> authors,
	List<String> images,
	List<ArchiveImageInfo> imageInfos,
	List<ArchiveAttachment> attachments,
	DiscordPostReference discordPost,
	List<ArchiveRecordSection> recordSections,
	long archivedAt,
	long updatedAt
) {
	public ArchivePostDetail {
		authors = authors != null ? authors : List.of();
		images = images != null ? images : List.of();
		imageInfos = imageInfos != null ? imageInfos : List.of();
		attachments = attachments != null ? attachments : List.of();
		recordSections = recordSections != null ? recordSections : List.of();
	}
}
