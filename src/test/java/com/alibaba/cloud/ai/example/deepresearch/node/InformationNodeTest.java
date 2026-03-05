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
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InformationNodeTest {

	@Test
	@DisplayName("planner pending status should be normalized before entering research execution")
	void shouldNormalizePendingExecutionStatus() {
		String plannerContent = """
				{
				  "has_enough_context": false,
				  "thought": "need research",
				  "title": "travel plan",
				  "steps": [
				    {
				      "need_web_search": true,
				      "title": "collect data",
				      "description": "collect city climate data",
				      "step_type": "research",
				      "executionStatus": "pending"
				    }
				  ]
				}
				""";

		Map<String, Object> data = new HashMap<>();
		data.put("planner_content", plannerContent);
		data.put("plan_iterations", 0);
		data.put("max_plan_iterations", 1);
		OverAllState state = new OverAllState(data);

		InformationNode node = new InformationNode();
		Map<String, Object> updated = node.apply(state);

		assertEquals("human_feedback", updated.get("information_next_node"));

		Plan plan = (Plan) updated.get("current_plan");
		assertNotNull(plan);
		assertNotNull(plan.getSteps());
		assertEquals(1, plan.getSteps().size());
		assertNull(plan.getSteps().get(0).getExecutionStatus());
	}

}
