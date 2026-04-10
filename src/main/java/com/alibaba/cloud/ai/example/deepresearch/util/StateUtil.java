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
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author yingzi
 * @since 2025/6/9
 */

public class StateUtil {

	public static final String RESEARCH_SEARCH_MODE = "research_search_mode";

	public static final String RESEARCH_SEARCH_ROUTE = "research_search_route";

	public static final String RESEARCH_SEARCH_FALLBACK_REASON = "research_search_fallback_reason";

	public static final String RESEARCH_SEARCH_MODE_MCP_FIRST_FALLBACK_TAVILY = "mcp_first_fallback_tavily";

	public static final String RESEARCH_SEARCH_ROUTE_MCP = "mcp";

	public static final String RESEARCH_SEARCH_ROUTE_TAVILY_FALLBACK = "tavily_fallback";

	public static final String FALLBACK_REASON_NO_MCP_TOOL = "no_mcp_tool";

	public static final String FALLBACK_REASON_TOOL_ERROR = "tool_error";

	public static final String FALLBACK_REASON_TIMEOUT = "timeout";

	public static final String FALLBACK_REASON_EMPTY_RESULT = "empty_result";

	public static final String FALLBACK_REASON_NO_USABLE_EVIDENCE = "no_usable_evidence";

	public static final String EXECUTION_STATUS_ASSIGNED_PREFIX = "assigned_";

	public static final String EXECUTION_STATUS_PROCESSING_PREFIX = "processing_";

	public static final String EXECUTION_STATUS_COMPLETED_PREFIX = "completed_";

	public static final String EXECUTION_STATUS_WAITING_REFLECTING = "waiting_reflecting_";

	public static final String EXECUTION_STATUS_WAITING_PROCESSING = "waiting_processing_";

	public static final String EXECUTION_STATUS_ERROR_PREFIX = "error_";

	public static String getResearchSearchRouteKey(String executorNodeId) {
		return RESEARCH_SEARCH_ROUTE + "_" + executorNodeId;
	}

	public static String getResearchSearchFallbackReasonKey(String executorNodeId) {
		return RESEARCH_SEARCH_FALLBACK_REASON + "_" + executorNodeId;
	}

	/**
	 * Handle step execution error by setting error status and logging
	 * @param step the plan step that failed
	 * @param nodeName the name of the node
	 * @param error the exception that occurred
	 * @param logger the logger to use
	 */
	public static void handleStepError(Plan.Step step, String nodeName, Throwable error, org.slf4j.Logger logger) {
		String errorMessage = "ERROR: " + error.getMessage();
		step.setExecutionStatus(EXECUTION_STATUS_ERROR_PREFIX + nodeName);
		step.setExecutionRes(errorMessage);
		logger.error("{} failed: {}", nodeName, error.getMessage(), error);
	}

	public static List<String> getParallelMessages(OverAllState state, List<String> researcherTeam, int count) {
		List<String> resList = new ArrayList<>();

		for (String item : researcherTeam) {
			for (int i = 0; i < count; i++) {
				String nodeName = item + "_content_" + i;
				Optional<String> value = state.value(nodeName, String.class);
				if (value.isPresent()) {
					resList.add(value.get());
				}
				else {
					break;
				}
			}
		}
		return resList;
	}

	public static String getQuery(OverAllState state) {
		return state.value("query", "草莓蛋糕怎么做呀");
	}

	public static List<String> getOptimizeQueries(OverAllState state) {
		return state.value("optimize_queries", (List<String>) null);
	}

	public static Plan getPlan(OverAllState state) {
		return state.value("current_plan", Plan.class).get();
	}

	public static Integer getPlanIterations(OverAllState state) {
		return state.value("plan_iterations", 0);
	}

	public static Integer getPlanMaxIterations(OverAllState state) {
		return state.value("max_plan_iterations", 1);
	}

	public static Integer getMaxStepNum(OverAllState state) {
		return state.value("max_step_num", 3);
	}

	public static Integer getOptimizeQueryNum(OverAllState state) {
		return state.value("optimize_query_num", 3);
	}

	public static String getThreadId(OverAllState state) {
		return state.value("thread_id", "");
	}

	public static String getSessionId(OverAllState state) {
		return state.value("session_id", "__default__");
	}

	public static boolean getAutoAcceptedPlan(OverAllState state) {
		return state.value("auto_accepted_plan", true);
	}

	public static String getRagContent(OverAllState state) {
		return state.value("rag_content", "");
	}

	public static boolean isSearchFilter(OverAllState state) {
		return state.value("search_filter", true);
	}

	public static boolean isDeepresearch(OverAllState state) {
		return state.value("enable_deepresearch", true);
	}

	public static String getResearchSearchMode(OverAllState state) {
		return state.value(RESEARCH_SEARCH_MODE, RESEARCH_SEARCH_MODE_MCP_FIRST_FALLBACK_TAVILY);
	}

}
