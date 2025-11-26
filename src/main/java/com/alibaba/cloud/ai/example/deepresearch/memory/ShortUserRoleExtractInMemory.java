package com.alibaba.cloud.ai.example.deepresearch.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author benym
 */
@Component
public class ShortUserRoleExtractInMemory implements ShortTermMemoryRepository {

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
    public List<Message> findMessageTrack(String userId, String conversationId) {
        Assert.hasText(userId, "userId cannot be null or empty");
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        List<Message> messages = shortTermMemoryTrack.get(buildKey(userId, conversationId));
        return messages != null ? messages : List.of();
    }

    @Override
    public Message findLatestMessage(String userId, String conversationId) {
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
}
