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

package com.alibaba.cloud.ai.example.deepresearch.memory;

import com.alibaba.cloud.ai.example.deepresearch.config.LongTermMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages reading and writing of Markdown-based memory files. Inspired by OpenClaw's
 * memory file system: - MEMORY.md: curated long-term facts and user preferences -
 * memory/YYYY-MM-DD.md: daily append-only logs
 *
 * Thread-safe via ReentrantReadWriteLock.
 *
 * @author deepresearch
 */
@Component
public class MarkdownMemoryFileManager {

	private static final Logger logger = LoggerFactory.getLogger(MarkdownMemoryFileManager.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final LongTermMemoryProperties properties;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public MarkdownMemoryFileManager(LongTermMemoryProperties properties) {
		this.properties = properties;
	}

	/**
	 * Read the entire content of MEMORY.md.
	 * @return file content, or empty string if file does not exist
	 */
	public String readMemoryFile() {
		lock.readLock().lock();
		try {
			Path path = Paths.get(properties.getMemoryFilePath());
			if (!Files.exists(path)) {
				logger.debug("MEMORY.md does not exist at: {}", path);
				return "";
			}
			String content = Files.readString(path, StandardCharsets.UTF_8);
			logger.debug("Read MEMORY.md, length: {} chars", content.length());
			return content;
		}
		catch (IOException e) {
			logger.error("Failed to read MEMORY.md", e);
			return "";
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Overwrite the entire content of MEMORY.md.
	 * @param content the new content to write
	 */
	public void writeMemoryFile(String content) {
		lock.writeLock().lock();
		try {
			Path path = Paths.get(properties.getMemoryFilePath());
			ensureDirectoryExists(path.getParent());
			Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			logger.info("Updated MEMORY.md, length: {} chars", content.length());
		}
		catch (IOException e) {
			logger.error("Failed to write MEMORY.md", e);
			throw new RuntimeException("Failed to write MEMORY.md", e);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Read the daily log file for a specific date.
	 * @param date the date to read
	 * @return file content, or empty string if file does not exist
	 */
	public String readDailyLog(LocalDate date) {
		lock.readLock().lock();
		try {
			Path path = getDailyLogPath(date);
			if (!Files.exists(path)) {
				logger.debug("Daily log does not exist for date: {}", date);
				return "";
			}
			String content = Files.readString(path, StandardCharsets.UTF_8);
			logger.debug("Read daily log for {}, length: {} chars", date, content.length());
			return content;
		}
		catch (IOException e) {
			logger.error("Failed to read daily log for date: {}", date, e);
			return "";
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Append content to the daily log file for a specific date. Creates the file if it
	 * does not exist.
	 * @param date the date to append to
	 * @param content the content to append
	 */
	public void appendDailyLog(LocalDate date, String content) {
		lock.writeLock().lock();
		try {
			Path path = getDailyLogPath(date);
			ensureDirectoryExists(path.getParent());
			Files.writeString(path, content + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
			logger.info("Appended to daily log for {}, added {} chars", date, content.length());
		}
		catch (IOException e) {
			logger.error("Failed to append daily log for date: {}", date, e);
			throw new RuntimeException("Failed to append daily log", e);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Read today's daily log.
	 */
	public String readTodayLog() {
		return readDailyLog(LocalDate.now());
	}

	/**
	 * Read yesterday's daily log.
	 */
	public String readYesterdayLog() {
		return readDailyLog(LocalDate.now().minusDays(1));
	}

	/**
	 * Append content to today's daily log.
	 */
	public void appendTodayLog(String content) {
		appendDailyLog(LocalDate.now(), content);
	}

	private Path getDailyLogPath(LocalDate date) {
		String filename = date.format(DATE_FORMATTER) + ".md";
		return Paths.get(properties.getMemoryDirectoryPath(), filename);
	}

	private void ensureDirectoryExists(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
			logger.info("Created memory directory: {}", dir);
		}
	}

}
