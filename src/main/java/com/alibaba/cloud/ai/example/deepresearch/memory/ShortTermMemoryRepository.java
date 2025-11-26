package com.alibaba.cloud.ai.example.deepresearch.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * @author benym
 */
public interface ShortTermMemoryRepository {

    List<Message> findMessages(String userId, String conversationId);

    Message findLatestMessage(String userId, String conversationId);

    void saveAll(String userId, String conversationId, List<Message> messages);

    int update(String userId, String conversationId, List<Message> messages);

    void deleteByConversationId(String conversationId);
}
