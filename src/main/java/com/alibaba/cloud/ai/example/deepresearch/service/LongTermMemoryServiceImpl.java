/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.service;

import com.alibaba.cloud.ai.example.deepresearch.config.LongTermMemoryProperties;
import com.alibaba.cloud.ai.example.deepresearch.memory.MarkdownMemoryFileManager;
import com.alibaba.cloud.ai.example.deepresearch.util.JsonUtil;
import com.alibaba.cloud.ai.example.deepresearch.util.LlmJsonExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of LongTermMemoryService using Markdown file storage. Inspired by
 * OpenClaw's memory system: - MEMORY.md for curated, durable facts and user preferences -
 * memory/YYYY-MM-DD.md for daily research logs
 *
 * @author deepresearch
 */
@Service
@ConditionalOnProperty(name = "spring.ai.alibaba.deepresearch.long-term-memory.enabled", havingValue = "true")
public class LongTermMemoryServiceImpl implements LongTermMemoryService {

	private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryServiceImpl.class);

	private final MarkdownMemoryFileManager fileManager;

	private final LongTermMemoryProperties properties;

	private final ChatClient longTermMemoryAgent;

	private MemoryIndexService memoryIndexService;

	public LongTermMemoryServiceImpl(MarkdownMemoryFileManager fileManager, LongTermMemoryProperties properties,
			ChatClient longTermMemoryAgent) {
		this.fileManager = fileManager;
		this.properties = properties;
		this.longTermMemoryAgent = longTermMemoryAgent;
		logger.info("Long-term memory service initialized. Workspace: {}", properties.getWorkspacePath());
	}

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setMemoryIndexService(MemoryIndexService memoryIndexService) {
		this.memoryIndexService = memoryIndexService;
	}

	@Override
	public String loadSessionContext() {
		if (!properties.isEnabled()) {
			return "";
		}

		StringBuilder contextBuilder = new StringBuilder();

		// 1. Always load MEMORY.md (curated long-term knowledge)
		String memoryContent = fileManager.readMemoryFile();
		if (StringUtils.hasText(memoryContent)) {
			contextBuilder.append("# Long-Term Memory (User Preferences & Facts)\n\n");
			contextBuilder.append(memoryContent);
			contextBuilder.append("\n\n");
		}

		// 2. Only inject daily logs when memory-search is NOT enabled
		// (when search IS enabled, daily logs are available via memory_search tool)
		if (!properties.getMemorySearch().isEnabled()) {
			// Fallback to v1 behavior: inject today + yesterday full content
			String todayLog = fileManager.readTodayLog();
			if (StringUtils.hasText(todayLog)) {
				contextBuilder.append("# Today's Research Log (")
					.append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
					.append(")\n\n");
				contextBuilder.append(todayLog);
				contextBuilder.append("\n\n");
			}

			String yesterdayLog = fileManager.readYesterdayLog();
			if (StringUtils.hasText(yesterdayLog)) {
				contextBuilder.append("# Yesterday's Research Log (")
					.append(LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
					.append(")\n\n");
				contextBuilder.append(yesterdayLog);
				contextBuilder.append("\n\n");
			}
		}

		String context = contextBuilder.toString().trim();
		if (StringUtils.hasText(context)) {
			logger.info("Loaded long-term memory context, total length: {} chars", context.length());
		}
		else {
			logger.debug("No long-term memory context available.");
		}
		return context;
	}

	@Override
	public void flushMemory(String userQuery, String finalReport) {
		if (!properties.isEnabled() || !properties.isAutoFlush()) {
			logger.debug("Long-term memory flush is disabled.");
			return;
		}

		// Execute flush asynchronously to avoid blocking the report response
		CompletableFuture.runAsync(() -> {
			try {
				doFlushMemory(userQuery, finalReport);
			}
			catch (Exception e) {
				logger.error("Failed to flush long-term memory", e);
			}
		});
	}

	private void doFlushMemory(String userQuery, String finalReport) {
		logger.info("Starting long-term memory flush for query: {}",
				userQuery.length() > 80 ? userQuery.substring(0, 80) + "..." : userQuery);

		try {
			// 1. Load the flush prompt template
			String promptTemplate = loadFlushPromptTemplate();

			// 2. Read existing MEMORY.md content for context
			String existingMemory = fileManager.readMemoryFile();

			// 3. Build the prompt with actual data
			String systemPrompt = promptTemplate.replace("{{ existing_memory }}", existingMemory)
				.replace("{{ current_time }}", LocalDateTime.now().toString());

			String userPrompt = "User Query: " + userQuery + "\n\nFinal Report:\n" + finalReport;

			// 4. Call LLM to summarize and categorize
			ChatResponse response = longTermMemoryAgent.prompt()
				.messages(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)))
				.call()
				.chatResponse();

			if (response == null || response.getResult() == null) {
				logger.warn("LLM returned null response during memory flush");
				return;
			}

			String llmOutput = response.getResult().getOutput().getText();
			logger.debug("Memory flush LLM output: {}", llmOutput);

			// 5. Parse the LLM output and write to files
			parseAndWriteMemory(llmOutput, existingMemory);

			// 6. Trigger incremental indexing if memory search is enabled
			if (memoryIndexService != null) {
				try {
					memoryIndexService.indexFile(java.nio.file.Paths.get(properties.getMemoryFilePath()));
					memoryIndexService.indexFile(java.nio.file.Paths.get(properties.getMemoryDirectoryPath(),
							LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md"));
					logger.info("Incremental memory indexing triggered after flush.");
				}
				catch (Exception ie) {
					logger.error("Failed to trigger incremental memory indexing", ie);
				}
			}

			logger.info("Long-term memory flush completed successfully.");
		}
		catch (Exception e) {
			logger.error("Error during memory flush", e);
		}
	}

	private void parseAndWriteMemory(String llmOutput, String existingMemory) {
		try {
			String normalizedJson = LlmJsonExtractor.normalizeJsonObject(llmOutput);
			Map<String, Object> result = JsonUtil.fromJson(normalizedJson, Map.class);

			if (result == null) {
				logger.warn("Failed to parse memory flush output as JSON, attempting raw write");
				fileManager.appendTodayLog("## Session Notes\n\n" + llmOutput);
				return;
			}

			// Write curated facts to MEMORY.md
			List<String> curatedFacts = extractCuratedFacts(result.get("curatedFacts"));
			if (!curatedFacts.isEmpty()) {
				StringBuilder memoryBuilder = new StringBuilder();
				if (StringUtils.hasText(existingMemory)) {
					memoryBuilder.append(existingMemory.trim()).append("\n\n");
				}
				for (String fact : curatedFacts) {
					memoryBuilder.append("- ").append(fact).append("\n");
				}
				fileManager.writeMemoryFile(memoryBuilder.toString().trim());
				logger.info("Written {} curated facts to MEMORY.md", curatedFacts.size());
			}

			// Append daily log
			Object dailyLogObj = result.get("dailyLog");
			if (dailyLogObj instanceof String dailyLog) {
				if (StringUtils.hasText(dailyLog)) {
					String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
					fileManager.appendTodayLog("\n## " + timestamp + " Session\n\n" + dailyLog);
					logger.info("Appended daily log entry for today.");
				}
			}
		}
		catch (Exception e) {
			logger.error("Failed to parse and write memory flush output", e);
			// Fallback: write raw output as daily log
			fileManager.appendTodayLog("## Session Raw Notes\n\n" + llmOutput);
		}
	}

	private List<String> extractCuratedFacts(Object curatedFactsObj) {
		if (!(curatedFactsObj instanceof List<?> rawFacts)) {
			return List.of();
		}

		List<String> facts = new ArrayList<>();
		for (Object rawFact : rawFacts) {
			String fact = rawFact == null ? "" : String.valueOf(rawFact).trim();
			if (StringUtils.hasText(fact)) {
				facts.add(fact);
			}
		}
		return facts.stream().distinct().toList();
	}

	private String loadFlushPromptTemplate() throws IOException {
		ClassPathResource resource = new ClassPathResource("prompts/memory/long/longmemory-flush.md");
		return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
	}

}
