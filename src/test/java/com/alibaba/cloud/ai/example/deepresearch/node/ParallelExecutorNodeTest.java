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

import com.alibaba.cloud.ai.example.deepresearch.config.DeepResearchProperties;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParallelExecutorNodeTest {

	@Test
	@DisplayName("pending status should be treated as unassigned and get assigned to researcher")
	void shouldAssignPendingResearchStep() throws Exception {
		Plan.Step step = new Plan.Step();
		step.setStepType(Plan.StepType.RESEARCH);
		step.setExecutionStatus("pending");
		step.setExecutionRes("");

		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		Map<String, Object> data = new HashMap<>();
		data.put("current_plan", plan);
		OverAllState state = new OverAllState(data);

		DeepResearchProperties properties = new DeepResearchProperties();
		properties.setParallelNodeCount(Map.of("researcher", 4));
		ParallelExecutorNode node = new ParallelExecutorNode(properties);

		node.apply(state);

		assertEquals(StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX + "researcher_0", step.getExecutionStatus());
	}

	@Test
	@DisplayName("processing status should also be assigned to researcher after removing coder branch")
	void shouldAssignProcessingStepToResearcher() throws Exception {
		Plan.Step step = new Plan.Step();
		step.setStepType(Plan.StepType.PROCESSING);
		step.setExecutionStatus(null);
		step.setExecutionRes("");

		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		Map<String, Object> data = new HashMap<>();
		data.put("current_plan", plan);
		OverAllState state = new OverAllState(data);

		DeepResearchProperties properties = new DeepResearchProperties();
		properties.setParallelNodeCount(Map.of("researcher", 2));
		ParallelExecutorNode node = new ParallelExecutorNode(properties);

		node.apply(state);

		assertEquals(StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX + "researcher_0", step.getExecutionStatus());
	}

}
