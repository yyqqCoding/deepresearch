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

import org.springframework.util.StringUtils;

/**
 * Extract JSON object text from LLM responses, including markdown fenced blocks and
 * surrounding noise.
 *
 * @author deepresearch
 */
public final class LlmJsonExtractor {

	private LlmJsonExtractor() {
	}

	public static String normalizeJsonObject(String text) {
		if (!StringUtils.hasText(text)) {
			return text;
		}

		String trimmed = text.trim();
		String unfenced = stripMarkdownFence(trimmed);
		String candidate = StringUtils.hasText(unfenced) ? unfenced.trim() : trimmed;

		String jsonObject = extractFirstJsonObject(candidate);
		if (StringUtils.hasText(jsonObject)) {
			return jsonObject;
		}
		return candidate;
	}

	static String stripMarkdownFence(String text) {
		if (!StringUtils.hasText(text)) {
			return text;
		}

		String trimmed = text.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}

		int firstLineEnd = trimmed.indexOf('\n');
		if (firstLineEnd < 0) {
			return trimmed.replace("```", "").trim();
		}

		String body = trimmed.substring(firstLineEnd + 1);
		int lastFence = body.lastIndexOf("```");
		if (lastFence >= 0) {
			body = body.substring(0, lastFence);
		}
		return body.trim();
	}

	static String extractFirstJsonObject(String text) {
		if (!StringUtils.hasText(text)) {
			return null;
		}

		int start = text.indexOf('{');
		if (start < 0) {
			return null;
		}

		int depth = 0;
		boolean inString = false;
		boolean escaped = false;

		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);

			if (inString) {
				if (escaped) {
					escaped = false;
				}
				else if (c == '\\') {
					escaped = true;
				}
				else if (c == '"') {
					inString = false;
				}
				continue;
			}

			if (c == '"') {
				inString = true;
				continue;
			}

			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return text.substring(start, i + 1);
				}
			}
		}

		return null;
	}

}
