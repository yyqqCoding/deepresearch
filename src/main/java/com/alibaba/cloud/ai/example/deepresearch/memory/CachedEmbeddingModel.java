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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * LRU-cached wrapper around an EmbeddingModel. Caches embedding vectors keyed by the
 * SHA-256 hash of input text. Avoids redundant embedding API calls for previously seen
 * content, matching OpenClaw's embedding cache concept (default 50,000 entries).
 *
 * @author deepresearch
 */
public class CachedEmbeddingModel implements EmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(CachedEmbeddingModel.class);

	private final EmbeddingModel delegate;

	private final Cache<String, float[]> cache;

	private long hits = 0;

	private long misses = 0;

	public CachedEmbeddingModel(EmbeddingModel delegate, int maxEntries) {
		this.delegate = delegate;
		this.cache = Caffeine.newBuilder().maximumSize(maxEntries).build();
		logger.info("CachedEmbeddingModel initialized with maxEntries={}", maxEntries);
	}

	@Override
	public float[] embed(String text) {
		String key = sha256(text);
		float[] cached = cache.getIfPresent(key);
		if (cached != null) {
			hits++;
			return cached;
		}
		misses++;
		float[] vector = delegate.embed(text);
		cache.put(key, vector);
		if ((hits + misses) % 100 == 0) {
			logger.debug("Embedding cache stats: hits={}, misses={}, size={}", hits, misses, cache.estimatedSize());
		}
		return vector;
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		return delegate.call(request);
	}

	@Override
	public int dimensions() {
		return delegate.dimensions();
	}

	public long getCacheHits() {
		return hits;
	}

	public long getCacheMisses() {
		return misses;
	}

	public long getCacheSize() {
		return cache.estimatedSize();
	}

	private static String sha256(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

}
