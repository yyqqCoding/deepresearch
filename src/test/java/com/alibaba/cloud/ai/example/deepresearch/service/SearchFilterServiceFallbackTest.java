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

import com.alibaba.cloud.ai.example.deepresearch.util.SearchBeanUtil;
import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.alibaba.cloud.ai.toolcalling.searches.SearchEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchFilterServiceFallbackTest {

	private static class TestSearchFilterService extends SearchFilterService {

		TestSearchFilterService(SearchBeanUtil searchBeanUtil) {
			super(searchBeanUtil);
		}

		@Override
		protected Map<String, Double> loadWebsiteWeight() {
			return Map.of();
		}

	}

	@Test
	@DisplayName("queryAndFilter should use first available engine when searchEnum is missing")
	void shouldFallbackToFirstAvailableSearchEngineWhenSearchEnumMissing() {
		SearchBeanUtil searchBeanUtil = mock(SearchBeanUtil.class);
		SearchService searchService = mock(SearchService.class, RETURNS_DEEP_STUBS);

		SearchService.SearchResult searchResult = new SearchService.SearchResult(
				List.of(new SearchService.SearchContent("title", "content", "https://example.com", null)));

		when(searchBeanUtil.getSearchService(null)).thenReturn(Optional.empty());
		when(searchBeanUtil.getFirstAvailableSearch()).thenReturn(Optional.of(SearchEnum.TAVILY));
		when(searchBeanUtil.getSearchService(SearchEnum.TAVILY)).thenReturn(Optional.of(searchService));
		when(searchService.query("travel").getSearchResult()).thenReturn(searchResult);

		SearchFilterService service = new TestSearchFilterService(searchBeanUtil);
		List<SearchFilterService.SearchContentWithWeight> response = service.queryAndFilter(true, null, "travel");

		assertFalse(response.isEmpty());
		assertEquals("title", response.get(0).content().title());
		verify(searchBeanUtil).getFirstAvailableSearch();
	}

}
