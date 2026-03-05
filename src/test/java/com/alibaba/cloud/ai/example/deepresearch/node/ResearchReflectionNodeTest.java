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

package com.alibaba.cloud.ai.example.deepresearch.node;

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.ReflectionProcessor;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchReflectionNodeTest {

	@Test
	@DisplayName("reflection pass should continue to professional kb decision")
	void shouldRouteToProfessionalKbWhenReflectionPasses() {
		Plan.Step step = new Plan.Step();
		step.setTitle("collect city profile");
		step.setExecutionRes("result");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0");

		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		OverAllState state = new OverAllState(new HashMap<>(Map.of("current_plan", plan)));

		ReflectionProcessor processor = mock(ReflectionProcessor.class);
		when(processor.evaluateCompletedStep(any(Plan.Step.class), eq("researcher")))
			.thenReturn(ReflectionProcessor.ReflectionDecision.PASS);

		ResearchReflectionNode node = new ResearchReflectionNode(processor);
		Map<String, Object> updated = node.apply(state);

		assertEquals("professional_kb_decision", updated.get("research_reflection_next_node"));
		assertEquals(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "reflected_researcher_0", step.getExecutionStatus());
	}

	@Test
	@DisplayName("reflection retry should send step back to parallel executor")
	void shouldRouteBackToParallelExecutorWhenReflectionFails() {
		Plan.Step step = new Plan.Step();
		step.setTitle("collect city profile");
		step.setExecutionRes("result");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0");

		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		OverAllState state = new OverAllState(new HashMap<>(Map.of("current_plan", plan)));

		ReflectionProcessor processor = mock(ReflectionProcessor.class);
		when(processor.evaluateCompletedStep(any(Plan.Step.class), eq("researcher")))
			.thenReturn(ReflectionProcessor.ReflectionDecision.RETRY);

		ResearchReflectionNode node = new ResearchReflectionNode(processor);
		Map<String, Object> updated = node.apply(state);

		assertEquals("parallel_executor", updated.get("research_reflection_next_node"));
		assertEquals(StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX + "researcher_0", step.getExecutionStatus());
		assertEquals("", step.getExecutionRes());
	}

	@Test
	@DisplayName("reflection max-attempt force pass should stop workflow instead of generating report")
	void shouldStopWorkflowWhenReflectionForcePassesAtMaxAttempts() {
		Plan.Step step = new Plan.Step();
		step.setTitle("collect city profile");
		step.setExecutionRes("still low quality");
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0");

		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		OverAllState state = new OverAllState(new HashMap<>(Map.of("current_plan", plan)));

		ReflectionProcessor processor = mock(ReflectionProcessor.class);
		when(processor.evaluateCompletedStep(any(Plan.Step.class), eq("researcher")))
			.thenReturn(ReflectionProcessor.ReflectionDecision.FORCE_PASS);

		ResearchReflectionNode node = new ResearchReflectionNode(processor);
		Map<String, Object> updated = node.apply(state);

		assertEquals(END, updated.get("research_reflection_next_node"));
		assertEquals(StateUtil.EXECUTION_STATUS_ERROR_PREFIX + "researcher_0", step.getExecutionStatus());
	}

}
