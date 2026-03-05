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

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.ReflectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Reflection utility class providing quality assessment and state management
 * functionality
 *
 * @author sixiyida
 * @since 2025/7/10
 */
public class ReflectionProcessor {

	private static final Logger logger = LoggerFactory.getLogger(ReflectionProcessor.class);

	private final ChatClient reflectionAgent;

	private final int maxReflectionAttempts;

	private final BeanOutputConverter<ReflectionResult> converter;

	public ReflectionProcessor(ChatClient reflectionAgent, int maxReflectionAttempts) {
		this.reflectionAgent = reflectionAgent;
		this.maxReflectionAttempts = maxReflectionAttempts;
		this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<ReflectionResult>() {
		});
	}

	/**
	 * Check and handle reflection logic
	 * @param step execution step
	 * @param nodeName node name
	 * @param nodeType node type (researcher/coder)
	 * @return ReflectionHandleResult containing whether to continue execution
	 */
	public ReflectionHandleResult handleReflection(Plan.Step step, String nodeName, String nodeType) {
		String currentStatus = step.getExecutionStatus();

		if (currentStatus != null && currentStatus.startsWith(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING)) {
			return performReflection(step, nodeName, nodeType);
		}

		if (currentStatus != null && currentStatus.startsWith(StateUtil.EXECUTION_STATUS_WAITING_PROCESSING)) {
			step.setExecutionStatus(StateUtil.EXECUTION_STATUS_PROCESSING_PREFIX + nodeName);
			step.setExecutionRes("");
			logger.info("Step {} is ready for reprocessing", step.getTitle());
			return ReflectionHandleResult.continueProcessing();
		}

		return ReflectionHandleResult.continueProcessing();
	}

	/**
	 * Perform reflection evaluation
	 */
	private ReflectionHandleResult performReflection(Plan.Step step, String nodeName, String nodeType) {
		try {
			int previousAttemptCount = getReflectionAttemptCount(step);

			boolean qualityGood = evaluateStepQuality(step, nodeType);

			if (qualityGood) {
				step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + nodeName);
				logger.info("Step {} reflection passed, quality is acceptable", step.getTitle());
				return ReflectionHandleResult.skipProcessing();
			}

			// max-attempts means max reprocessing count after initial completion.
			if (previousAttemptCount >= maxReflectionAttempts) {
				logger.warn("Step {} has exhausted reflection retries (max {}), forcing pass", step.getTitle(),
						maxReflectionAttempts);
				step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + nodeName);
				return ReflectionHandleResult.skipProcessing();
			}

			step.setExecutionStatus(StateUtil.EXECUTION_STATUS_WAITING_PROCESSING + nodeName);
			logger.info("Step {} reflection failed, marked for reprocessing (attempt {})", step.getTitle(),
					previousAttemptCount + 1);
			return ReflectionHandleResult.skipProcessing();

		}
		catch (Exception e) {
			logger.error("Reflection process failed, defaulting to pass: {}", e.getMessage());
			step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + nodeName);
			return ReflectionHandleResult.skipProcessing();
		}
	}

	/**
	 * Evaluate step quality
	 */
	private boolean evaluateStepQuality(Plan.Step step, String nodeType) {
		String evaluationPrompt = buildEvaluationPrompt(step, nodeType);

		try {
			var response = reflectionAgent.prompt(converter.getFormat()).user(evaluationPrompt).call().chatResponse();

			String responseText = response != null && response.getResult() != null && response.getResult().getOutput() != null
					? response.getResult().getOutput().getText() : "";
			if (!StringUtils.hasText(responseText)) {
				throw new IllegalStateException("Reflection model returned empty response");
			}

			String normalizedResponse = LlmJsonExtractor.normalizeJsonObject(responseText);
			ReflectionResult reflectionResult = converter.convert(normalizedResponse);
			if (reflectionResult == null) {
				throw new IllegalStateException("Unable to parse reflection response");
			}
			if (!StringUtils.hasText(reflectionResult.getFeedback())) {
				reflectionResult.setFeedback("无具体反馈");
			}

			// Add execution result to reflection record
			reflectionResult.setExecutionResult(step.getExecutionRes());
			step.addReflectionRecord(reflectionResult);

			logger.debug("Step {} quality evaluation result: passed={}, feedback={}", step.getTitle(),
					reflectionResult.isPassed(), reflectionResult.getFeedback());

			return reflectionResult.isPassed();

		}
		catch (Exception e) {
			logger.error("Quality evaluation failed, defaulting to pass: {}", e.getMessage());
			// Create a default reflection record
			ReflectionResult defaultResult = new ReflectionResult(true,
					"Evaluation failed, system default pass: " + e.getMessage(), step.getExecutionRes());
			step.addReflectionRecord(defaultResult);
			return true;
		}
	}

	/**
	 * Build evaluation prompt
	 */
	private String buildEvaluationPrompt(Plan.Step step, String nodeType) {
		String taskTypeDescription = switch (nodeType) {
			case "researcher" -> "research task";
			case "coder" -> "coding task";
			default -> "task";
		};

		return String.format("""
				Please evaluate the completion quality of the following %s.
				Current date: %s

				Evaluation constraints:
				1. Evaluate strictly based on the task and the provided completion result.
				2. If an external fact or URL cannot be verified from the text alone, label it as "need verification" rather than asserting it is false.
				3. Do not fail solely on assumptions about calendar-year feasibility unless there is explicit contradiction in the result itself.
				4. Mark as failed only when core requirements are missing, major content is fabricated from internal evidence, or conclusions are clearly inconsistent.

				**Task Title:** %s

				**Task Description:** %s

				**Completion Result:**
				%s
				""", taskTypeDescription, LocalDate.now(), step.getTitle(), step.getDescription(), step.getExecutionRes());
	}

	/**
	 * Get reflection attempt count
	 */
	private int getReflectionAttemptCount(Plan.Step step) {
		if (step.getReflectionHistory() != null) {
			return step.getReflectionHistory().size();
		}

		// Compatible with old status string parsing
		String status = step.getExecutionStatus();
		if (status != null && status.contains("_attempt_")) {
			try {
				String[] parts = status.split("_attempt_");
				if (parts.length > 1) {
					return Integer.parseInt(parts[1].split("_")[0]);
				}
			}
			catch (NumberFormatException e) {
				logger.debug("Failed to parse reflection attempt count: {}", status);
			}
		}
		return 0;
	}

	/**
	 * Reflection handle result class
	 */
	public static class ReflectionHandleResult {

		private final boolean shouldContinueProcessing;

		private ReflectionHandleResult(boolean shouldContinueProcessing) {
			this.shouldContinueProcessing = shouldContinueProcessing;
		}

		public static ReflectionHandleResult continueProcessing() {
			return new ReflectionHandleResult(true);
		}

		public static ReflectionHandleResult skipProcessing() {
			return new ReflectionHandleResult(false);
		}

		public boolean shouldContinueProcessing() {
			return shouldContinueProcessing;
		}

	}

}
