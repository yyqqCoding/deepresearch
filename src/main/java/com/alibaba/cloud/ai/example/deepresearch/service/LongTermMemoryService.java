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

/**
 * Service interface for managing long-term memory. Provides read and write operations for
 * persistent memory using OpenClaw-style Markdown files.
 *
 * @author deepresearch
 */
public interface LongTermMemoryService {

	/**
	 * Load the session context from long-term memory. Reads MEMORY.md + today's log +
	 * yesterday's log and returns them as a combined context string for injection into
	 * the system prompt.
	 * @return combined long-term memory context, or empty string if disabled/empty
	 */
	String loadSessionContext();

	/**
	 * Flush the current session's knowledge into long-term memory files. Uses LLM to
	 * summarize and categorize the session content into: - Curated facts → MEMORY.md -
	 * Daily research log → YYYY-MM-DD.md
	 * @param userQuery the original user query
	 * @param finalReport the generated report content
	 */
	void flushMemory(String userQuery, String finalReport);

}
