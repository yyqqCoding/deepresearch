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

package com.alibaba.cloud.ai.example.deepresearch.config;

import com.alibaba.cloud.ai.example.deepresearch.memory.BM25MemoryIndex;
import com.alibaba.cloud.ai.example.deepresearch.memory.HybridMemorySearchEngine;
import com.alibaba.cloud.ai.example.deepresearch.memory.MarkdownMemoryFileManager;
import com.alibaba.cloud.ai.example.deepresearch.tool.MemoryGetTool;
import com.alibaba.cloud.ai.example.deepresearch.tool.MemorySearchTool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for long-term memory tools and the hybrid search engine. Creates
 * HybridMemorySearchEngine, MemorySearchTool, and MemoryGetTool beans when memory search
 * is enabled.
 *
 * @author deepresearch
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.alibaba.deepresearch.long-term-memory.memory-search.enabled",
		havingValue = "true")
public class MemoryToolConfiguration {

	@Bean
	public HybridMemorySearchEngine hybridMemorySearchEngine(
			@Qualifier("memoryVectorStore") VectorStore memoryVectorStore, BM25MemoryIndex bm25MemoryIndex,
			LongTermMemoryProperties properties) {
		return new HybridMemorySearchEngine(memoryVectorStore, bm25MemoryIndex, properties.getMemorySearch());
	}

	@Bean
	public MemorySearchTool memorySearchTool(HybridMemorySearchEngine hybridEngine,
			LongTermMemoryProperties properties) {
		return new MemorySearchTool(hybridEngine, properties);
	}

	@Bean
	public MemoryGetTool memoryGetTool(MarkdownMemoryFileManager fileManager, LongTermMemoryProperties properties) {
		return new MemoryGetTool(fileManager, properties);
	}

}
