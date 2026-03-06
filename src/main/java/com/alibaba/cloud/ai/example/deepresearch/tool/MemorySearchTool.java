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
import com.alibaba.cloud.ai.example.deepresearch.memory.HybridMemorySearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool for hybrid semantic + keyword search over long-term memory. Uses the
 * HybridMemorySearchEngine which implements the full OpenClaw pipeline: Vector + BM25 →
 * Weighted Merge → Temporal Decay → MMR → Top-K.
 *
 * @author deepresearch
 */
public class MemorySearchTool {

	private static final Logger logger = LoggerFactory.getLogger(MemorySearchTool.class);

	private static final int MAX_SNIPPET_CHARS = 700;

	private final HybridMemorySearchEngine hybridEngine;

	private final LongTermMemoryProperties properties;

	public MemorySearchTool(HybridMemorySearchEngine hybridEngine, LongTermMemoryProperties properties) {
		this.hybridEngine = hybridEngine;
		this.properties = properties;
	}

	@Tool(description = "Search long-term memory for semantically related past research results, "
			+ "user preferences, and historical context. Uses hybrid vector + keyword search with "
			+ "temporal decay and diversity re-ranking. "
			+ "Use this when the current task might benefit from knowledge gained in previous research sessions.")
	public String memorySearch(
			@ToolParam(description = "The search query describing what past knowledge to recall") String query,
			@ToolParam(required = false,
					description = "Maximum number of results to return, defaults to 5") Integer maxResults) {

		int topK = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 10)
				: properties.getMemorySearch().getMaxResults();

		logger.info("Hybrid memory search invoked with query: '{}', topK: {}", query, topK);

		try {
			List<HybridMemorySearchEngine.ScoredDocument> results = hybridEngine.search(query, topK);

			if (results.isEmpty()) {
				logger.info("Hybrid memory search returned no results for query: '{}'", query);
				return "No relevant memories found.";
			}

			String formattedResults = results.stream()
				.map(this::formatScoredDocument)
				.collect(Collectors.joining("\n---\n"));

			logger.info("Hybrid memory search returned {} results for query: '{}'", results.size(), query);
			return formattedResults;
		}
		catch (Exception e) {
			logger.error("Hybrid memory search failed for query: '{}'", query, e);
			return "Memory search encountered an error: " + e.getMessage();
		}
	}

	private String formatScoredDocument(HybridMemorySearchEngine.ScoredDocument scoredDoc) {
		StringBuilder sb = new StringBuilder();

		// Source info with score
		Object source = scoredDoc.getDocument().getMetadata().get("source");
		Object sourceLabel = scoredDoc.getDocument().getMetadata().get("sourceLabel");
		Object date = scoredDoc.getDocument().getMetadata().get("date");

		if (sourceLabel != null) {
			sb.append("**Source**: ").append(sourceLabel);
		}
		else if (source != null) {
			sb.append("**Source**: ").append(source);
		}
		if (date != null) {
			sb.append(" (").append(date).append(")");
		}
		sb.append(String.format(" [score: %.3f]", scoredDoc.getScore()));
		sb.append("\n");

		// Content snippet (~700 chars as per OpenClaw)
		String content = scoredDoc.getDocument().getText();
		if (content.length() > MAX_SNIPPET_CHARS) {
			content = content.substring(0, MAX_SNIPPET_CHARS - 3) + "...";
		}
		sb.append(content);

		return sb.toString();
	}

}
