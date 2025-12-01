package com.alibaba.cloud.ai.example.deepresearch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author benym
 */
@Configuration
public class MemoryConfig {

	private static final Logger logger = LoggerFactory.getLogger(MemoryConfig.class);

	@Bean
	public MessageWindowChatMemory messageWindowChatMemory() {
		int maxMessages = 100;
		logger.info("Initializing InMemory MessageWindowChatMemory with max messages: {}", maxMessages);
		InMemoryChatMemoryRepository inMemoryChatMemoryRepository = new InMemoryChatMemoryRepository();
		return MessageWindowChatMemory.builder()
			.chatMemoryRepository(inMemoryChatMemoryRepository)
			.maxMessages(maxMessages)
			.build();
	}

}
