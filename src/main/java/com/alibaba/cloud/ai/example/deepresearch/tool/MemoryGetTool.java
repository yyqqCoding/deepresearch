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

package com.alibaba.cloud.ai.example.deepresearch.tool;

import com.alibaba.cloud.ai.example.deepresearch.config.LongTermMemoryProperties;
import com.alibaba.cloud.ai.example.deepresearch.memory.MarkdownMemoryFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Tool for targeted reading of specific memory files. Equivalent to OpenClaw's memory_get
 * tool.
 *
 * Allows the agent to read specific memory files by date or by name, optionally from a
 * starting line.
 *
 * @author deepresearch
 */
public class MemoryGetTool {

	private static final Logger logger = LoggerFactory.getLogger(MemoryGetTool.class);

	private final MarkdownMemoryFileManager fileManager;

	private final LongTermMemoryProperties properties;

	public MemoryGetTool(MarkdownMemoryFileManager fileManager, LongTermMemoryProperties properties) {
		this.fileManager = fileManager;
		this.properties = properties;
	}

	@Tool(description = "Read the content of a specific memory file. "
			+ "Use 'MEMORY' to read the main memory file, or a date like '2026-03-05' to read a daily log.")
	public String memoryGet(
			@ToolParam(description = "File identifier: 'MEMORY' for MEMORY.md, "
					+ "or a date string 'YYYY-MM-DD' for a daily log file") String fileId,
			@ToolParam(required = false,
					description = "Starting line number (1-based), defaults to reading from beginning") Integer startLine,
			@ToolParam(required = false, description = "Number of lines to read, defaults to all") Integer lineCount) {

		logger.info("Memory get invoked for fileId: '{}', startLine: {}, lineCount: {}", fileId, startLine, lineCount);

		try {
			String content;

			if ("MEMORY".equalsIgnoreCase(fileId) || "MEMORY.md".equalsIgnoreCase(fileId)) {
				content = fileManager.readMemoryFile();
			}
			else {
				// Try to parse as a date
				try {
					LocalDate date = LocalDate.parse(fileId, DateTimeFormatter.ISO_LOCAL_DATE);
					content = fileManager.readDailyLog(date);
				}
				catch (DateTimeParseException e) {
					// Try as a relative filename in the memory directory
					Path filePath = Paths.get(properties.getMemoryDirectoryPath(), fileId);
					if (!filePath.toString().endsWith(".md")) {
						filePath = Paths.get(properties.getMemoryDirectoryPath(), fileId + ".md");
					}
					// Security check: ensure path is within memory directory
					if (!filePath.normalize().startsWith(Paths.get(properties.getMemoryDirectoryPath()).normalize())) {
						return "Error: Access denied. Can only read files within the memory directory.";
					}
					if (!java.nio.file.Files.exists(filePath)) {
						return "File not found: " + fileId;
					}
					content = java.nio.file.Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
				}
			}

			if (!StringUtils.hasText(content)) {
				return "File is empty or does not exist: " + fileId;
			}

			// Apply line range if specified
			if (startLine != null && startLine > 0) {
				String[] lines = content.split("\n");
				int start = Math.min(startLine - 1, lines.length);
				int count = (lineCount != null && lineCount > 0) ? lineCount : lines.length - start;
				int end = Math.min(start + count, lines.length);

				StringBuilder result = new StringBuilder();
				for (int i = start; i < end; i++) {
					result.append(lines[i]).append("\n");
				}
				content = result.toString();
			}

			logger.info("Memory get returned {} chars for fileId: '{}'", content.length(), fileId);
			return content;
		}
		catch (Exception e) {
			logger.error("Memory get failed for fileId: '{}'", fileId, e);
			return "Error reading memory file: " + e.getMessage();
		}
	}

}
