package com.alibaba.cloud.ai.example.deepresearch.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author benym
 */
@Component
public class ShortUserRoleExtractInMemory implements ShortTermMemoryRepository {

    @Override
    public List<Message> findMessages(String userId, String conversationId) {
        return List.of();
    }

    @Override
    public Message findLatestMessage(String userId, String conversationId) {
        return null;
    }

    @Override
    public void saveAll(String userId, String conversationId, List<Message> messages) {

    }

    @Override
    public int update(String userId, String conversationId, List<Message> messages) {
        return 0;
    }

    @Override
    public void deleteByConversationId(String conversationId) {

    }
}
