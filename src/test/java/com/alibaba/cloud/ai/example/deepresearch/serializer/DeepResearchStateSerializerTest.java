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

package com.alibaba.cloud.ai.example.deepresearch.serializer;

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.toolcalling.searches.SearchEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DeepResearchStateSerializerTest {

	@Test
	@DisplayName("readData should restore current_plan as Plan and search_engine as SearchEnum")
	void shouldRestoreTypedStateValues() throws Exception {
		DeepResearchStateSerializer serializer = new DeepResearchStateSerializer(OverAllState::new);

		Plan.Step step = new Plan.Step();
		step.setExecutionStatus(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0");
		Plan plan = new Plan();
		plan.setSteps(List.of(step));

		Map<String, Object> original = new HashMap<>();
		original.put("current_plan", plan);
		original.put("search_engine", SearchEnum.TAVILY);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			serializer.writeData(original, oos);
		}

		Map<String, Object> restored;
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
			restored = serializer.readData(ois);
		}

		assertInstanceOf(Plan.class, restored.get("current_plan"));
		assertInstanceOf(SearchEnum.class, restored.get("search_engine"));
		assertEquals(SearchEnum.TAVILY, restored.get("search_engine"));

		Plan restoredPlan = (Plan) restored.get("current_plan");
		assertEquals(1, restoredPlan.getSteps().size());
		assertEquals(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX + "researcher_0",
				restoredPlan.getSteps().get(0).getExecutionStatus());
	}

}
