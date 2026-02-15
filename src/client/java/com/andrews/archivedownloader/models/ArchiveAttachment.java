package com.andrews.archivedownloader.models;

public record ArchiveAttachment(
	String name,
	String downloadUrl,
	String contentType,
	boolean canDownload,
	String sizeText,
	String description,
	String hash,
	LitematicInfo litematic,
	SchematicInfo schematic,
	WdlInfo wdl,
	YoutubeInfo youtube
) {
	public boolean isDownloadable() {
		return canDownload && downloadUrl != null && !downloadUrl.isEmpty();
	}

	public record LitematicInfo(String version, String size, String error) {}

	public record SchematicInfo(String version, String size, String error) {}

	public record WdlInfo(String version, String error) {}

	public record YoutubeInfo(String title, String authorName, String authorUrl) {}
}
