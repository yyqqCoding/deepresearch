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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory BM25 full-text index using Apache Lucene with SmartChineseAnalyzer. Provides
 * keyword-based retrieval to complement vector semantic search, following OpenClaw's
 * hybrid search pattern (FTS5 BM25 equivalent).
 *
 * @author deepresearch
 */
public class BM25MemoryIndex {

	private static final Logger logger = LoggerFactory.getLogger(BM25MemoryIndex.class);

	private static final String FIELD_CONTENT = "content";

	private static final String FIELD_DOC_ID = "docId";

	private static final String FIELD_SOURCE = "source";

	private static final String FIELD_DATE = "date";

	private final Analyzer analyzer;

	private Directory directory;

	private IndexWriter writer;

	public BM25MemoryIndex() {
		this.analyzer = new SmartChineseAnalyzer();
		resetIndex();
	}

	/**
	 * Clear and recreate the index.
	 */
	public synchronized void resetIndex() {
		try {
			if (writer != null) {
				writer.close();
			}
			if (directory != null) {
				directory.close();
			}
			directory = new ByteBuffersDirectory();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setSimilarity(new BM25Similarity());
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			writer = new IndexWriter(directory, config);
			writer.commit();
			logger.info("BM25 memory index reset.");
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to reset BM25 index", e);
		}
	}

	/**
	 * Add a document to the BM25 index.
	 * @param docId unique document identifier (matching VectorStore document id)
	 * @param content the text content to index
	 * @param source the source file name
	 * @param date the date string (may be null for non-dated files)
	 */
	public synchronized void addDocument(String docId, String content, String source, String date) {
		try {
			Document doc = new Document();
			doc.add(new TextField(FIELD_CONTENT, content, Field.Store.NO));
			doc.add(new StringField(FIELD_DOC_ID, docId, Field.Store.YES));
			doc.add(new StringField(FIELD_SOURCE, source != null ? source : "", Field.Store.YES));
			doc.add(new StringField(FIELD_DATE, date != null ? date : "", Field.Store.YES));
			writer.addDocument(doc);
		}
		catch (IOException e) {
			logger.error("Failed to add document to BM25 index: {}", docId, e);
		}
	}

	/**
	 * Commit pending changes to the index.
	 */
	public synchronized void commit() {
		try {
			writer.commit();
		}
		catch (IOException e) {
			logger.error("Failed to commit BM25 index", e);
		}
	}

	/**
	 * Search the BM25 index and return ranked results.
	 * @param queryText the search query
	 * @param topK maximum number of results
	 * @return list of (docId, bm25Rank) pairs, rank 0 = best match
	 */
	public List<BM25Result> search(String queryText, int topK) {
		List<BM25Result> results = new ArrayList<>();
		try {
			DirectoryReader reader = DirectoryReader.open(directory);
			IndexSearcher searcher = new IndexSearcher(reader);
			searcher.setSimilarity(new BM25Similarity());

			// Escape special Lucene query characters to avoid ParseException
			String escaped = QueryParser.escape(queryText);
			QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
			Query query = parser.parse(escaped);

			TopDocs topDocs = searcher.search(query, topK);
			int rank = 0;
			for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
				Document doc = searcher.storedFields().document(scoreDoc.doc);
				results.add(new BM25Result(doc.get(FIELD_DOC_ID), rank, scoreDoc.score));
				rank++;
			}
			reader.close();
		}
		catch (Exception e) {
			logger.error("BM25 search failed for query: '{}'", queryText, e);
		}
		return results;
	}

	/**
	 * Get the number of documents in the index.
	 */
	public int getDocumentCount() {
		try {
			DirectoryReader reader = DirectoryReader.open(directory);
			int count = reader.numDocs();
			reader.close();
			return count;
		}
		catch (IOException e) {
			return 0;
		}
	}

	/**
	 * BM25 search result.
	 */
	public static class BM25Result {

		private final String docId;

		private final int rank;

		private final float rawScore;

		public BM25Result(String docId, int rank, float rawScore) {
			this.docId = docId;
			this.rank = rank;
			this.rawScore = rawScore;
		}

		public String getDocId() {
			return docId;
		}

		public int getRank() {
			return rank;
		}

		public float getRawScore() {
			return rawScore;
		}

	}

}
