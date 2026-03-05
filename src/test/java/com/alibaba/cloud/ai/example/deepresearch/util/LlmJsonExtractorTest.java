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

package com.alibaba.cloud.ai.example.deepresearch.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmJsonExtractorTest {

	@Test
	@DisplayName("should extract JSON from markdown fenced response")
	void shouldExtractFromFencedBlock() {
		String input = """
				```json
				{"passed":false,"feedback":"needs update"}
				```
				""";

		String normalized = LlmJsonExtractor.normalizeJsonObject(input);

		assertEquals("{\"passed\":false,\"feedback\":\"needs update\"}", normalized);
	}

	@Test
	@DisplayName("should extract first json object from mixed response text")
	void shouldExtractFromMixedText() {
		String input = "Result: {\"curatedFacts\":[\"a\"],\"dailyLog\":\"x\"} trailing text";

		String normalized = LlmJsonExtractor.normalizeJsonObject(input);

		assertEquals("{\"curatedFacts\":[\"a\"],\"dailyLog\":\"x\"}", normalized);
	}

	@Test
	@DisplayName("should keep nested braces inside JSON string")
	void shouldHandleBracesInsideString() {
		String input = "{\"feedback\":\"contains {brace} text\",\"passed\":true}";

		String normalized = LlmJsonExtractor.normalizeJsonObject(input);

		assertEquals(input, normalized);
	}

}
