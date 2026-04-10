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

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanFeedbackNodeTest {

	@Test
	@DisplayName("max iterations should keep workflow on research team")
	void shouldStayOnResearchTeamWhenMaxIterationsReached() throws Exception {
		OverAllState state = new OverAllState(new HashMap<>(Map.of("plan_iterations", 3, "max_plan_iterations", 3)));

		HumanFeedbackNode node = new HumanFeedbackNode();
		Map<String, Object> updated = node.apply(state);

		assertEquals("research_team", updated.get("human_next_node"));
	}

	@Test
	@DisplayName("negative feedback should route back to planner")
	void shouldRouteBackToPlannerWhenFeedbackIsFalse() throws Exception {
		OverAllState state = new OverAllState(
				new HashMap<>(Map.of("plan_iterations", 0, "max_plan_iterations", 3, "feedback", false,
						"feedback_content", "补充预算维度")));

		HumanFeedbackNode node = new HumanFeedbackNode();
		Map<String, Object> updated = node.apply(state);

		assertEquals("planner", updated.get("human_next_node"));
		assertEquals("补充预算维度", updated.get("feedback_content"));
	}

}
