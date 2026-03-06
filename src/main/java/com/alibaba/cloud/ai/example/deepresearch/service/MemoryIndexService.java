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
import com.alibaba.cloud.ai.example.deepresearch.memory.BM25MemoryIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service responsible for indexing memory Markdown files into both the memory VectorStore
 * and the BM25 index. Scans all .md files under the memory directory, chunks them, stores
 * embeddings, and maintains the Lucene BM25 index.
 *
 * @author deepresearch
 */
@Service
@ConditionalOnProperty(name = "spring.ai.alibaba.deepresearch.long-term-memory.memory-search.enabled",
		havingValue = "true")
public class MemoryIndexService {

	private static final Logger logger = LoggerFactory.getLogger(MemoryIndexService.class);

	private final VectorStore memoryVectorStore;

	private final LongTermMemoryProperties properties;

	private BM25MemoryIndex bm25Index;

	public MemoryIndexService(VectorStore memoryVectorStore, LongTermMemoryProperties properties) {
		this.memoryVectorStore = memoryVectorStore;
		this.properties = properties;
	}

	@Autowired(required = false)
	public void setBm25Index(BM25MemoryIndex bm25Index) {
		this.bm25Index = bm25Index;
	}

	/**
	 * Index all memory files on startup.
	 */
	@PostConstruct
	public void indexOnStartup() {
		try {
			rebuildIndex();
		}
		catch (Exception e) {
			logger.error("Failed to build memory index on startup", e);
		}
	}

	/**
	 * Rebuild the entire memory index by scanning all .md files.
	 */
	public void rebuildIndex() throws IOException {
		Path memoryDir = Paths.get(properties.getMemoryDirectoryPath());
		if (!Files.exists(memoryDir)) {
			logger.info("Memory directory does not exist: {}, skipping index build.", memoryDir);
			return;
		}

		// Reset BM25 index for full rebuild
		if (bm25Index != null) {
			bm25Index.resetIndex();
		}

		List<Document> allDocuments = new ArrayList<>();

		// Index MEMORY.md
		Path memoryFile = Paths.get(properties.getMemoryFilePath());
		if (Files.exists(memoryFile)) {
			allDocuments.addAll(chunkFile(memoryFile, "MEMORY"));
		}

		// Index all daily log files and other .md files
		try (Stream<Path> paths = Files.list(memoryDir)) {
			paths.filter(p -> p.toString().endsWith(".md"))
				.filter(p -> !p.getFileName().toString().equals("MEMORY.md"))
				.forEach(p -> {
					try {
						String dateStr = extractDateFromFilename(p);
						allDocuments.addAll(chunkFile(p, dateStr != null ? dateStr : p.getFileName().toString()));
					}
					catch (IOException e) {
						logger.error("Failed to chunk file: {}", p, e);
					}
				});
		}

		if (allDocuments.isEmpty()) {
			logger.info("No memory documents to index.");
			return;
		}

		// Add to vector store
		memoryVectorStore.add(allDocuments);

		// Add to BM25 index
		if (bm25Index != null) {
			for (Document doc : allDocuments) {
				String source = (String) doc.getMetadata().getOrDefault("source", "");
				String date = (String) doc.getMetadata().get("date");
				bm25Index.addDocument(doc.getId(), doc.getText(), source, date);
			}
			bm25Index.commit();
		}

		logger.info("Memory index built: {} documents (vector + BM25) from {}", allDocuments.size(), memoryDir);
	}

	/**
	 * Incrementally index a single file (called after flush).
	 */
	public void indexFile(Path filePath) {
		try {
			if (!Files.exists(filePath)) {
				return;
			}
			String label = filePath.getFileName().toString().replace(".md", "");
			List<Document> docs = chunkFile(filePath, label);
			if (!docs.isEmpty()) {
				// Add to vector store
				memoryVectorStore.add(docs);

				// Add to BM25 index
				if (bm25Index != null) {
					for (Document doc : docs) {
						String source = (String) doc.getMetadata().getOrDefault("source", "");
						String date = (String) doc.getMetadata().get("date");
						bm25Index.addDocument(doc.getId(), doc.getText(), source, date);
					}
					bm25Index.commit();
				}

				logger.info("Incrementally indexed {} chunks (vector + BM25) from {}", docs.size(), filePath);
			}
		}
		catch (IOException e) {
			logger.error("Failed to incrementally index file: {}", filePath, e);
		}
	}

	/**
	 * Chunk a Markdown file into documents with metadata.
	 */
	private List<Document> chunkFile(Path filePath, String sourceLabel) throws IOException {
		String content = Files.readString(filePath, StandardCharsets.UTF_8);
		if (!StringUtils.hasText(content)) {
			return List.of();
		}

		List<Document> documents = new ArrayList<>();
		String fileName = filePath.getFileName().toString();

		// Parse date from filename for metadata
		String dateStr = extractDateFromFilename(filePath);
		LocalDate fileDate = null;
		if (dateStr != null) {
			try {
				fileDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
			}
			catch (DateTimeParseException ignored) {
			}
		}

		// Split into chunks with overlap
		int maxChunkChars = properties.getMemorySearch().getChunkSize();
		int overlapChars = properties.getMemorySearch().getChunkOverlap();
		List<String> chunks = splitWithOverlap(content, maxChunkChars, overlapChars);

		for (int i = 0; i < chunks.size(); i++) {
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("source", fileName);
			metadata.put("sourceLabel", sourceLabel);
			metadata.put("chunkIndex", i);
			metadata.put("totalChunks", chunks.size());
			metadata.put("filePath", filePath.toString());
			if (fileDate != null) {
				metadata.put("date", fileDate.toString());
			}
			// Flag MEMORY.md and non-dated files as durable (no temporal decay)
			metadata.put("durable", "MEMORY.md".equals(fileName) || !fileName.matches("\\d{4}-\\d{2}-\\d{2}\\.md"));

			documents.add(new Document(chunks.get(i), metadata));
		}

		return documents;
	}

	/**
	 * Split text into chunks with character overlap.
	 */
	private List<String> splitWithOverlap(String text, int maxChunkSize, int overlap) {
		List<String> chunks = new ArrayList<>();

		if (text.length() <= maxChunkSize) {
			chunks.add(text);
			return chunks;
		}

		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + maxChunkSize, text.length());

			// Try to break at a natural boundary (newline)
			if (end < text.length()) {
				int lastNewline = text.lastIndexOf('\n', end);
				if (lastNewline > start + maxChunkSize / 2) {
					end = lastNewline + 1;
				}
			}

			chunks.add(text.substring(start, end).trim());

			// Move forward with overlap
			start = end - overlap;
			if (start >= text.length()) {
				break;
			}
		}

		return chunks;
	}

	/**
	 * Extract date string from a filename like "2026-03-05.md".
	 */
	private String extractDateFromFilename(Path filePath) {
		String name = filePath.getFileName().toString().replace(".md", "");
		if (name.matches("\\d{4}-\\d{2}-\\d{2}")) {
			return name;
		}
		return null;
	}

}
