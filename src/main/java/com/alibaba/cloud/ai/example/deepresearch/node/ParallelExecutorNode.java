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

import com.alibaba.cloud.ai.example.deepresearch.model.enums.ParallelEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.example.deepresearch.config.DeepResearchProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * @author sixiyida
 * @since 2025/6/12
 */

public class ParallelExecutorNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ParallelExecutorNode.class);

	private final Map<String, Integer> parallelNodeCount;

	public ParallelExecutorNode(DeepResearchProperties properties) {
		this.parallelNodeCount = properties.getParallelNodeCount();
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		long currResearcher = 0;
		long currCoder = 0;

			Plan curPlan = StateUtil.getPlan(state);
			for (Plan.Step step : curPlan.getSteps()) {
				// 跳过已经在处理中或已完成的步骤，"pending" 视为待分配
				if (shouldSkipStep(step)) {
					continue;
				}

			Plan.StepType stepType = step.getStepType();

			switch (stepType) {
				case PROCESSING:
					if (areAllResearchStepsCompleted(curPlan)) {
						step.setExecutionStatus(assignRole(stepType, currCoder));
						currCoder = (currCoder + 1) % parallelNodeCount.get(ParallelEnum.CODER.getValue());
					}
					logger.info("Waiting for remaining research steps executed");
					break;

				case RESEARCH:
					step.setExecutionStatus(assignRole(stepType, currResearcher));
					currResearcher = (currResearcher + 1) % parallelNodeCount.get(ParallelEnum.RESEARCHER.getValue());
					break;

				// 处理其他可能的StepType
				default:
					logger.debug("Unhandled step type: {}", stepType);
			}
		}
		return Map.of();
	}

	private String assignRole(Plan.StepType type, long executorId) {
		String role = type == Plan.StepType.PROCESSING ? ParallelEnum.CODER.getValue()
				: ParallelEnum.RESEARCHER.getValue();
		return StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX + role + "_" + executorId;
	}

	private boolean areAllResearchStepsCompleted(Plan plan) {
		if (CollectionUtils.isEmpty(plan.getSteps())) {
			return true;
		}

		return plan.getSteps()
			.stream()
			.filter(step -> step.getStepType() == Plan.StepType.RESEARCH)
			.allMatch(step -> {
				String status = step.getExecutionStatus();
				return StringUtils.hasText(status)
						&& (status.startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
								|| status.startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX));
			});
	}

	private boolean shouldSkipStep(Plan.Step step) {
		if (StringUtils.hasText(step.getExecutionRes())) {
			return true;
		}
		String status = step.getExecutionStatus();
		if (!StringUtils.hasText(status)) {
			return false;
		}
		String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
		if ("pending".equals(normalizedStatus)) {
			return false;
		}
		return normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX)
				|| normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_PROCESSING_PREFIX)
				|| normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
				|| normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING)
				|| normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_WAITING_PROCESSING)
				|| normalizedStatus.startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX);
	}

}
