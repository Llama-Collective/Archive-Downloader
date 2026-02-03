package com.andrews.archivedownloader.models;

import java.util.List;

public record DiscordPostReference(
	String forumId,
	String threadId,
	List<String> continuingMessageIds,
	String threadURL,
	String attachmentMessageId,
	String uploadMessageId
) {}
