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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Long-term memory configuration properties. Controls the OpenClaw-style Markdown
 * file-based persistent memory system.
 *
 * @author deepresearch
 */
@ConfigurationProperties(prefix = LongTermMemoryProperties.PREFIX)
public class LongTermMemoryProperties {

	public static final String PREFIX = DeepResearchProperties.PREFIX + ".long-term-memory";

	/**
	 * Whether long-term memory is enabled.
	 */
	private boolean enabled = false;

	/**
	 * Long-term memory provider. Supported values: local, mem0.
	 */
	private String provider = "local";

	/**
	 * Workspace root path for memory files. Defaults to ~/.deepresearch/workspace. Memory
	 * files will be stored under {workspacePath}/memory/.
	 */
	private String workspacePath = System.getProperty("user.home") + "/.deepresearch/workspace";

	/**
	 * Whether to automatically flush memory after report generation in ReporterNode.
	 */
	private boolean autoFlush = true;

	/**
	 * Memory search (vector + semantic) configuration.
	 */
	private MemorySearch memorySearch = new MemorySearch();

	/**
	 * Mem0 provider configuration.
	 */
	private Mem0 mem0 = new Mem0();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(String workspacePath) {
		this.workspacePath = workspacePath;
	}

	public boolean isAutoFlush() {
		return autoFlush;
	}

	public void setAutoFlush(boolean autoFlush) {
		this.autoFlush = autoFlush;
	}

	public MemorySearch getMemorySearch() {
		return memorySearch;
	}

	public void setMemorySearch(MemorySearch memorySearch) {
		this.memorySearch = memorySearch;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Mem0 getMem0() {
		return mem0;
	}

	public void setMem0(Mem0 mem0) {
		this.mem0 = mem0;
	}

	/**
	 * Get the daily logs directory path: {workspacePath}/memory/
	 */
	public String getMemoryDirectoryPath() {
		return workspacePath + "/memory";
	}

	/**
	 * Get the MEMORY.md file path: {workspacePath}/MEMORY.md
	 */
	public String getMemoryFilePath() {
		return workspacePath + "/MEMORY.md";
	}

	/**
	 * Memory search configuration.
	 */
	public static class MemorySearch {

		/**
		 * Whether memory search (tool-based semantic retrieval) is enabled.
		 */
		private boolean enabled = false;

		/**
		 * Maximum number of results returned by memory_search.
		 */
		private int maxResults = 5;

		/**
		 * Characters per chunk (~4 chars per token → ~400 tokens).
		 */
		private int chunkSize = 1600;

		/**
		 * Overlap characters between chunks (~80 tokens).
		 */
		private int chunkOverlap = 320;

		/**
		 * Weight for vector (semantic) score in hybrid merge. Default 0.7.
		 */
		private double vectorWeight = 0.7;

		/**
		 * Weight for BM25 (keyword) score in hybrid merge. Default 0.3.
		 */
		private double textWeight = 0.3;

		/**
		 * Half-life in days for temporal decay. Score halves every N days. Default 30.
		 */
		private int halfLifeDays = 30;

		/**
		 * MMR lambda for diversity vs relevance tradeoff. 0=max diversity, 1=max
		 * relevance. Default 0.7.
		 */
		private double mmrLambda = 0.7;

		/**
		 * Candidate multiplier: retrieve topK * multiplier candidates before
		 * post-processing. Default 4.
		 */
		private int candidateMultiplier = 4;

		/**
		 * Maximum entries in the embedding LRU cache. Default 50000.
		 */
		private int embeddingCacheSize = 50000;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getMaxResults() {
			return maxResults;
		}

		public void setMaxResults(int maxResults) {
			this.maxResults = maxResults;
		}

		public int getChunkSize() {
			return chunkSize;
		}

		public void setChunkSize(int chunkSize) {
			this.chunkSize = chunkSize;
		}

		public int getChunkOverlap() {
			return chunkOverlap;
		}

		public void setChunkOverlap(int chunkOverlap) {
			this.chunkOverlap = chunkOverlap;
		}

		public double getVectorWeight() {
			return vectorWeight;
		}

		public void setVectorWeight(double vectorWeight) {
			this.vectorWeight = vectorWeight;
		}

		public double getTextWeight() {
			return textWeight;
		}

		public void setTextWeight(double textWeight) {
			this.textWeight = textWeight;
		}

		public int getHalfLifeDays() {
			return halfLifeDays;
		}

		public void setHalfLifeDays(int halfLifeDays) {
			this.halfLifeDays = halfLifeDays;
		}

		public double getMmrLambda() {
			return mmrLambda;
		}

		public void setMmrLambda(double mmrLambda) {
			this.mmrLambda = mmrLambda;
		}

		public int getCandidateMultiplier() {
			return candidateMultiplier;
		}

		public void setCandidateMultiplier(int candidateMultiplier) {
			this.candidateMultiplier = candidateMultiplier;
		}

		public int getEmbeddingCacheSize() {
			return embeddingCacheSize;
		}

		public void setEmbeddingCacheSize(int embeddingCacheSize) {
			this.embeddingCacheSize = embeddingCacheSize;
		}

	}

	public static class Mem0 {

		private String baseUrl = "https://api.mem0.ai";

		private String apiKey;

		private String orgId;

		private String projectId;

		private String userId = "liyongqing";

		private String appId = "deepresearch";

		private int topK = 5;

		private boolean inferOnWrite = true;

		private boolean asyncMode = true;

		private String customInstructions;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getOrgId() {
			return orgId;
		}

		public void setOrgId(String orgId) {
			this.orgId = orgId;
		}

		public String getProjectId() {
			return projectId;
		}

		public void setProjectId(String projectId) {
			this.projectId = projectId;
		}

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}

		public String getAppId() {
			return appId;
		}

		public void setAppId(String appId) {
			this.appId = appId;
		}

		public int getTopK() {
			return topK;
		}

		public void setTopK(int topK) {
			this.topK = topK;
		}

		public boolean isInferOnWrite() {
			return inferOnWrite;
		}

		public void setInferOnWrite(boolean inferOnWrite) {
			this.inferOnWrite = inferOnWrite;
		}

		public boolean isAsyncMode() {
			return asyncMode;
		}

		public void setAsyncMode(boolean asyncMode) {
			this.asyncMode = asyncMode;
		}

		public String getCustomInstructions() {
			return customInstructions;
		}

		public void setCustomInstructions(String customInstructions) {
			this.customInstructions = customInstructions;
		}

	}

}
