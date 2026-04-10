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

package com.alibaba.cloud.ai.example.deepresearch.agents;

import com.alibaba.cloud.ai.example.deepresearch.config.ObservationProperties;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Allen Hu
 * @since 0.1.0
 */
@Configuration
@EnableConfigurationProperties({ ObservationProperties.class })
@ConditionalOnProperty(prefix = ObservationProperties.PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class ObservationConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ObservationConfiguration.class);

	private static final int TOOL_RESULT_LOG_LIMIT = 256;

	@Bean
	public ObservationHandler<ToolCallingObservationContext> toolCallingObservationContextObservationHandler() {
		return new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return context instanceof ToolCallingObservationContext;
			}

			@Override
			public void onStart(ToolCallingObservationContext context) {
				ToolDefinition toolDefinition = context.getToolDefinition();
				logger.info("🔨ToolCalling start: {} - {}", toolDefinition.name(), context.getToolCallArguments());
			}

			@Override
			public void onStop(ToolCallingObservationContext context) {
				ToolDefinition toolDefinition = context.getToolDefinition();
				logger.info("✅ToolCalling done: {} - {}", toolDefinition.name(),
						summarizeToolCallResult(context.getToolCallResult()));
			}
		};
	}

	static String summarizeToolCallResult(String toolCallResult) {
		if (toolCallResult == null) {
			return "<null>";
		}
		String sanitized = sanitizeForLog(toolCallResult);
		if (sanitized.length() <= TOOL_RESULT_LOG_LIMIT) {
			return sanitized;
		}
		return sanitized.substring(0, TOOL_RESULT_LOG_LIMIT) + "...(truncated, length=" + sanitized.length() + ")";
	}

	private static String sanitizeForLog(String value) {
		StringBuilder sanitized = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t') {
				sanitized.append('?');
			}
			else {
				sanitized.append(ch);
			}
		}
		return sanitized.toString();
	}

}
