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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectionProcessorTest {

	@Test
	@DisplayName("max-attempts=1 should evaluate retry result before force pass")
	void shouldEvaluateRetryBeforeForcePass() {
		ChatClient reflectionAgent = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		when(reflectionAgent.prompt(anyString())
			.user(anyString())
			.call()
			.chatResponse()
			.getResult()
			.getOutput()
			.getText())
			.thenReturn("{\"passed\":false,\"feedback\":\"first fail\"}",
					"{\"passed\":false,\"feedback\":\"second fail\"}");

		ReflectionProcessor processor = new ReflectionProcessor(reflectionAgent, 1);
		Plan.Step step = new Plan.Step();
		step.setTitle("t1");
		step.setDescription("d1");
		step.setExecutionRes("r1");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING + "researcher_0");

		ReflectionProcessor.ReflectionHandleResult first = processor.handleReflection(step, "researcher_0",
				"researcher");
		assertFalse(first.shouldContinueProcessing());
		assertEquals(StateUtil.EXECUTION_STATUS_WAITING_PROCESSING + "researcher_0", step.getExecutionStatus());
		assertEquals(1, step.getReflectionHistory().size());

		step.setExecutionRes("r2");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING + "researcher_0");
		ReflectionProcessor.ReflectionHandleResult second = processor.handleReflection(step, "researcher_0",
				"researcher");
		assertFalse(second.shouldContinueProcessing());
		assertEquals(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0", step.getExecutionStatus());
		assertEquals(2, step.getReflectionHistory().size());
	}

	@Test
	@DisplayName("reflection json wrapped in markdown fence should be parsed")
	void shouldParseFencedReflectionJson() {
		ChatClient reflectionAgent = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		when(reflectionAgent.prompt(anyString())
			.user(anyString())
			.call()
			.chatResponse()
			.getResult()
			.getOutput()
			.getText())
			.thenReturn("""
					```json
					{"passed":true,"feedback":"ok"}
					```
					""");

		ReflectionProcessor processor = new ReflectionProcessor(reflectionAgent, 1);
		Plan.Step step = new Plan.Step();
		step.setTitle("t2");
		step.setDescription("d2");
		step.setExecutionRes("r2");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING + "researcher_1");

		ReflectionProcessor.ReflectionHandleResult result = processor.handleReflection(step, "researcher_1",
				"researcher");

		assertFalse(result.shouldContinueProcessing());
		assertEquals(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_1", step.getExecutionStatus());
		assertEquals(1, step.getReflectionHistory().size());
	}

}
