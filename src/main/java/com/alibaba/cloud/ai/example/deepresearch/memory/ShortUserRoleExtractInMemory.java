package com.alibaba.cloud.ai.example.deepresearch.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author benym
 */
@Component
public class ShortUserRoleExtractInMemory implements ShortTermMemoryRepository {

	/**
	 * 存储用户查询记忆
	 */
	Map<String, List<UserMessage>> userQueryMemory = new ConcurrentHashMap<>();

	/**
	 * 存储用户角色提取轨迹
	 */
	Map<String, List<Message>> shortTermMemoryTrack = new ConcurrentHashMap<>();

	/**
	 * 存储用户角色提取结果
	 */
	Map<String, Message> shortTermMemory = new ConcurrentHashMap<>();

	private String buildKey(String userId, String conversationId) {
		return userId + ":" + conversationId;
	}

	@Override
	public List<String> getRecentUserQueries(String conversationId, int limit) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.isTrue(limit > 0, "limit must be greater than 0");
		List<UserMessage> messages = userQueryMemory.get(conversationId);
		if (messages == null || messages.isEmpty()) {
			return List.of();
		}
		List<UserMessage> sortedMessages = new ArrayList<>(messages);
		sortedMessages.sort(Comparator.comparing(this::resolveCreateTime).reversed());
		return sortedMessages.stream().limit(limit).map(UserMessage::getText).collect(Collectors.toList());
	}

	@Override
	public void saveUserQuery(String conversationId, List<UserMessage> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		userQueryMemory.put(conversationId, messages);
	}

	@Override
	public List<Message> findMessageTrack(String userId, String conversationId) {
		Assert.hasText(userId, "userId cannot be null or empty");
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		List<Message> messages = shortTermMemoryTrack.get(buildKey(userId, conversationId));
		return messages != null ? messages : List.of();
	}

	@Override
	public Message findLatestExtractMessage(String userId, String conversationId) {
		Assert.hasText(userId, "userId cannot be null or empty");
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return shortTermMemory.get(buildKey(userId, conversationId));
	}

	@Override
	public void saveOrUpdate(String userId, String conversationId, List<Message> messages) {
		Assert.hasText(userId, "userId cannot be null or empty");
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		shortTermMemoryTrack.put(buildKey(userId, conversationId), messages);
		shortTermMemory.put(buildKey(userId, conversationId), messages.get(0));
	}

	@Override
	public void deleteBy(String userId, String conversationId) {
		Assert.hasText(userId, "userId cannot be null or empty");
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		shortTermMemoryTrack.remove(buildKey(userId, conversationId));
		shortTermMemory.remove(buildKey(userId, conversationId));
	}

	private LocalDateTime resolveCreateTime(UserMessage message) {
		Map<String, Object> metadata = message.getMetadata();
		Object value = metadata.get("create_time");
		return value instanceof LocalDateTime ? (LocalDateTime) value : LocalDateTime.MIN;
	}

}
