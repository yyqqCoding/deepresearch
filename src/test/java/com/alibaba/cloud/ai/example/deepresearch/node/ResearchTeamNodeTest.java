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
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchTeamNodeTest {

	@Test
	@DisplayName("all completed steps should route to research_reflection")
	void shouldRouteToReflectionWhenAllStepsDone() throws Exception {
		Plan.Step step = new Plan.Step();
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0");
		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		OverAllState state = new OverAllState(new HashMap<>(Map.of("current_plan", plan)));

		ResearchTeamNode node = new ResearchTeamNode();
		Map<String, Object> updated = node.apply(state);

		assertEquals("research_reflection", updated.get("research_team_next_node"));
	}

}
