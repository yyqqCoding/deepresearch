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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete hybrid memory search engine implementing the OpenClaw pipeline:
 *
 * <pre>
 * Vector + BM25 → Weighted Merge → Temporal Decay → Sort → MMR → Top-K
 * </pre>
 *
 * Formulae (matching OpenClaw):
 * <ul>
 * <li>textScore = 1 / (1 + max(0, bm25Rank))</li>
 * <li>finalScore = vectorWeight × vectorScore + textWeight × textScore</li>
 * <li>decayedScore = score × e^(-λ × ageInDays), where λ = ln(2)/halfLifeDays</li>
 * <li>MMR: λ_mmr × relevance − (1−λ_mmr) × max(jaccard(d, selected))</li>
 * </ul>
 *
 * @author deepresearch
 */
public class HybridMemorySearchEngine {

	private static final Logger logger = LoggerFactory.getLogger(HybridMemorySearchEngine.class);

	private final VectorStore memoryVectorStore;

	private final BM25MemoryIndex bm25Index;

	private final LongTermMemoryProperties.MemorySearch config;

	public HybridMemorySearchEngine(VectorStore memoryVectorStore, BM25MemoryIndex bm25Index,
			LongTermMemoryProperties.MemorySearch config) {
		this.memoryVectorStore = memoryVectorStore;
		this.bm25Index = bm25Index;
		this.config = config;
	}

	/**
	 * Execute the full hybrid search pipeline.
	 * @param query the search query
	 * @param topK desired number of final results
	 * @return list of ScoredDocument with final scores and metadata
	 */
	public List<ScoredDocument> search(String query, int topK) {
		int candidatePool = topK * config.getCandidateMultiplier();

		// ──────────────────────────────────────────────
		// Step 1: Retrieve candidates from both sources
		// ──────────────────────────────────────────────

		// 1a. Vector retrieval
		Map<String, VectorCandidate> vectorCandidates = new HashMap<>();
		try {
			List<Document> vectorResults = memoryVectorStore
				.similaritySearch(SearchRequest.builder().query(query).topK(candidatePool).build());

			if (vectorResults != null) {
				for (int i = 0; i < vectorResults.size(); i++) {
					Document doc = vectorResults.get(i);
					// Spring AI returns similarity scores; normalize rank-based
					double vectorScore = 1.0 - ((double) i / vectorResults.size());
					vectorCandidates.put(doc.getId(), new VectorCandidate(doc, vectorScore));
				}
			}
		}
		catch (Exception e) {
			logger.error("Vector search failed for query: '{}'", query, e);
		}

		// 1b. BM25 retrieval
		Map<String, Integer> bm25Rankings = new HashMap<>();
		List<BM25MemoryIndex.BM25Result> bm25Results = bm25Index.search(query, candidatePool);
		for (BM25MemoryIndex.BM25Result result : bm25Results) {
			bm25Rankings.put(result.getDocId(), result.getRank());
		}

		// ──────────────────────────────────────────────
		// Step 2: Union candidates and compute weighted score
		// ──────────────────────────────────────────────

		Set<String> allIds = new HashSet<>();
		allIds.addAll(vectorCandidates.keySet());
		allIds.addAll(bm25Rankings.keySet());

		Map<String, ScoredDocument> mergedCandidates = new LinkedHashMap<>();
		double vectorWeight = config.getVectorWeight();
		double textWeight = config.getTextWeight();
		// Normalize weights to sum to 1.0
		double weightSum = vectorWeight + textWeight;
		if (weightSum > 0) {
			vectorWeight /= weightSum;
			textWeight /= weightSum;
		}

		for (String docId : allIds) {
			double vectorScore = 0.0;
			Document doc = null;

			if (vectorCandidates.containsKey(docId)) {
				VectorCandidate vc = vectorCandidates.get(docId);
				vectorScore = vc.score;
				doc = vc.document;
			}

			// textScore = 1 / (1 + max(0, bm25Rank))
			double textScore = 0.0;
			if (bm25Rankings.containsKey(docId)) {
				int rank = bm25Rankings.get(docId);
				textScore = 1.0 / (1.0 + Math.max(0, rank));
			}

			double finalScore = vectorWeight * vectorScore + textWeight * textScore;

			if (doc != null) {
				mergedCandidates.put(docId, new ScoredDocument(doc, finalScore));
			}
		}

		// ──────────────────────────────────────────────
		// Step 3: Temporal decay
		// ──────────────────────────────────────────────

		int halfLifeDays = config.getHalfLifeDays();
		double lambda = Math.log(2) / halfLifeDays;
		LocalDate today = LocalDate.now();

		for (ScoredDocument scoredDoc : mergedCandidates.values()) {
			// Check if document is durable (MEMORY.md or non-dated files)
			Object durableObj = scoredDoc.getDocument().getMetadata().get("durable");
			boolean isDurable = Boolean.TRUE.equals(durableObj) || "true".equals(String.valueOf(durableObj));

			if (isDurable) {
				// No decay for durable documents
				continue;
			}

			// Calculate age from metadata date
			Object dateObj = scoredDoc.getDocument().getMetadata().get("date");
			if (dateObj != null) {
				try {
					LocalDate docDate = LocalDate.parse(String.valueOf(dateObj), DateTimeFormatter.ISO_LOCAL_DATE);
					long ageInDays = ChronoUnit.DAYS.between(docDate, today);
					if (ageInDays > 0) {
						double decayFactor = Math.exp(-lambda * ageInDays);
						scoredDoc.setScore(scoredDoc.getScore() * decayFactor);
					}
				}
				catch (DateTimeParseException ignored) {
					// If date cannot be parsed, treat as durable (no decay)
				}
			}
		}

		// ──────────────────────────────────────────────
		// Step 4: Sort by decayed score
		// ──────────────────────────────────────────────

		List<ScoredDocument> sortedCandidates = new ArrayList<>(mergedCandidates.values());
		sortedCandidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

		// ──────────────────────────────────────────────
		// Step 5: MMR re-ranking for diversity
		// ──────────────────────────────────────────────

		double mmrLambda = config.getMmrLambda();
		List<ScoredDocument> mmrResults = mmrRerank(sortedCandidates, topK, mmrLambda);

		logger.info("Hybrid search: query='{}', vectorCandidates={}, bm25Candidates={}, merged={}, final={}", query,
				vectorCandidates.size(), bm25Rankings.size(), mergedCandidates.size(), mmrResults.size());

		return mmrResults;
	}

	/**
	 * MMR (Maximal Marginal Relevance) re-ranking. Iteratively selects documents that
	 * maximize: λ × relevance − (1−λ) × max_similarity_to_selected
	 *
	 * Similarity is measured using Jaccard text similarity on tokenized content.
	 */
	private List<ScoredDocument> mmrRerank(List<ScoredDocument> candidates, int topK, double lambda) {
		if (candidates.size() <= topK) {
			return candidates;
		}

		List<ScoredDocument> selected = new ArrayList<>();
		List<ScoredDocument> remaining = new ArrayList<>(candidates);

		while (selected.size() < topK && !remaining.isEmpty()) {
			ScoredDocument best = null;
			double bestMmrScore = Double.NEGATIVE_INFINITY;

			for (ScoredDocument candidate : remaining) {
				double relevance = candidate.getScore();
				double maxSim = 0.0;

				for (ScoredDocument sel : selected) {
					double sim = jaccardSimilarity(candidate.getDocument().getText(), sel.getDocument().getText());
					maxSim = Math.max(maxSim, sim);
				}

				double mmrScore = lambda * relevance - (1 - lambda) * maxSim;

				if (mmrScore > bestMmrScore) {
					bestMmrScore = mmrScore;
					best = candidate;
				}
			}

			if (best != null) {
				selected.add(best);
				remaining.remove(best);
			}
			else {
				break;
			}
		}

		return selected;
	}

	/**
	 * Jaccard similarity between two text strings based on word tokens.
	 */
	private double jaccardSimilarity(String text1, String text2) {
		if (text1 == null || text2 == null) {
			return 0.0;
		}

		Set<String> tokens1 = tokenize(text1);
		Set<String> tokens2 = tokenize(text2);

		if (tokens1.isEmpty() && tokens2.isEmpty()) {
			return 1.0;
		}

		Set<String> intersection = new HashSet<>(tokens1);
		intersection.retainAll(tokens2);

		Set<String> union = new HashSet<>(tokens1);
		union.addAll(tokens2);

		if (union.isEmpty()) {
			return 0.0;
		}

		return (double) intersection.size() / union.size();
	}

	/**
	 * Simple tokenizer: split on whitespace and punctuation, lowercase.
	 */
	private Set<String> tokenize(String text) {
		Set<String> tokens = new HashSet<>();
		// Split on whitespace, punctuation, and CJK boundaries
		for (String token : text.toLowerCase().split("[\\s\\p{Punct}]+")) {
			if (!token.isEmpty() && token.length() > 1) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	/**
	 * A document with a mutable score.
	 */
	public static class ScoredDocument {

		private final Document document;

		private double score;

		public ScoredDocument(Document document, double score) {
			this.document = document;
			this.score = score;
		}

		public Document getDocument() {
			return document;
		}

		public double getScore() {
			return score;
		}

		public void setScore(double score) {
			this.score = score;
		}

	}

	/**
	 * Internal holder for vector search candidates.
	 */
	private static class VectorCandidate {

		final Document document;

		final double score;

		VectorCandidate(Document document, double score) {
			this.document = document;
			this.score = score;
		}

	}

}
