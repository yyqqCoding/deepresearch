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
import com.alibaba.cloud.ai.example.deepresearch.memory.CachedEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the memory-dedicated VectorStore and BM25 index. Creates an
 * independent SimpleVectorStore bean backed by a CachedEmbeddingModel, plus a Lucene BM25
 * index for keyword search. Both are isolated from the RAG VectorStore.
 *
 * @author deepresearch
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.alibaba.deepresearch.long-term-memory.memory-search.enabled",
		havingValue = "true")
public class MemoryVectorStoreConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(MemoryVectorStoreConfiguration.class);

	@Bean(name = "memoryVectorStore")
	public VectorStore memoryVectorStore(EmbeddingModel embeddingModel, LongTermMemoryProperties properties) {
		int cacheSize = properties.getMemorySearch().getEmbeddingCacheSize();
		logger.info("Creating CachedEmbeddingModel with LRU cache size: {}", cacheSize);
		CachedEmbeddingModel cachedEmbeddingModel = new CachedEmbeddingModel(embeddingModel, cacheSize);

		logger.info("Initializing memory-dedicated SimpleVectorStore with CachedEmbeddingModel.");
		return SimpleVectorStore.builder(cachedEmbeddingModel).build();
	}

	@Bean
	public BM25MemoryIndex bm25MemoryIndex() {
		logger.info("Initializing BM25 memory index with SmartChineseAnalyzer.");
		return new BM25MemoryIndex();
	}

}
