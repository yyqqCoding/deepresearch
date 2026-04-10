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

import com.alibaba.cloud.ai.example.deepresearch.model.dto.memory.MemoryScope;

/**
 * Service interface for managing long-term memory.
 *
 * @author deepresearch
 */
public interface LongTermMemoryService {

	/**
	 * Load the session context from long-term memory. Reads MEMORY.md + today's log +
	 * yesterday's log and returns them as a combined context string for injection into
	 * the system prompt.
	 * @param scope current memory scope
	 * @param query current user query used for relevant retrieval
	 * @return combined long-term memory context, or empty string if disabled/empty
	 */
	String loadRelevantContext(MemoryScope scope, String query);

	void flushMemory(MemoryScope scope, String userQuery, String assistantOutput);

}
