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

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.example.deepresearch.config.SmartAgentProperties;
import com.alibaba.cloud.ai.example.deepresearch.model.enums.StreamNodePrefixEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.model.multiagent.AgentSelectionResult;
import com.alibaba.cloud.ai.example.deepresearch.service.McpProviderFactory;
import com.alibaba.cloud.ai.example.deepresearch.service.ResearchMcpToolCallbackAdapter;
import com.alibaba.cloud.ai.example.deepresearch.service.ResearchSearchOrchestrator;
import com.alibaba.cloud.ai.example.deepresearch.service.SearchFilterService;
import com.alibaba.cloud.ai.example.deepresearch.service.multiagent.SmartAgentDispatcherService;
import com.alibaba.cloud.ai.example.deepresearch.service.multiagent.SmartAgentSelectionHelperService;
import com.alibaba.cloud.ai.example.deepresearch.tool.MemoryGetTool;
import com.alibaba.cloud.ai.example.deepresearch.tool.MemorySearchSupport;
import com.alibaba.cloud.ai.example.deepresearch.tool.SearchFilterTool;
import com.alibaba.cloud.ai.example.deepresearch.util.*;
import com.alibaba.cloud.ai.example.deepresearch.util.convert.FluxConverter;
import com.alibaba.cloud.ai.example.deepresearch.util.multiagent.AgentIntegrationUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.toolcalling.searches.SearchEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author sixiyida
 * @since 2025/6/14 11:17
 */

public class ResearcherNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ResearcherNode.class);

	private static final Set<String> RESEARCH_MCP_SEARCH_TOOL_NAMES = Set.of("search_web");

	private final ChatClient researchAgent;

	private final String executorNodeId;

	private final String nodeName;

	private final ReflectionProcessor reflectionProcessor;

	// MCP工厂
	private final McpProviderFactory mcpFactory;

	private final SearchFilterService searchFilterService;

	private final SmartAgentSelectionHelperService smartAgentSelectionHelper;

	// Long-term memory tools (optional, may be null)
	private final MemorySearchSupport memorySearchTool;

	private final MemoryGetTool memoryGetTool;

	private final ResearchSearchOrchestrator researchSearchOrchestrator;

	private final int researchMcpExtraSources;

	public ResearcherNode(ChatClient researchAgent, String executorNodeId, ReflectionProcessor reflectionProcessor,
			McpProviderFactory mcpFactory, SearchFilterService searchFilterService,
			SmartAgentDispatcherService smartAgentDispatcher, SmartAgentProperties smartAgentProperties,
			MemorySearchSupport memorySearchTool, MemoryGetTool memoryGetTool, int researchMcpExtraSources) {
		this.researchAgent = researchAgent;
		this.executorNodeId = executorNodeId;
		this.nodeName = "researcher_" + executorNodeId;
		this.reflectionProcessor = reflectionProcessor;
		this.mcpFactory = mcpFactory;
		this.searchFilterService = searchFilterService;
		this.smartAgentSelectionHelper = AgentIntegrationUtil.createSelectionHelper(smartAgentProperties,
				smartAgentDispatcher, null, null);
		this.memorySearchTool = memorySearchTool;
		this.memoryGetTool = memoryGetTool;
		this.researchSearchOrchestrator = new ResearchSearchOrchestrator();
		this.researchMcpExtraSources = researchMcpExtraSources;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Plan currentPlan = StateUtil.getPlan(state);
		Map<String, Object> updated = new HashMap<>();

		Plan.Step assignedStep = findAssignedStep(currentPlan);

		if (assignedStep == null) {
			logger.info("No remaining steps to be executed by {}", nodeName);
			return updated;
		}

		// Handle reflection logic
		if (reflectionProcessor != null) {
			ReflectionProcessor.ReflectionHandleResult reflectionResult = reflectionProcessor
				.handleReflection(assignedStep, nodeName, "researcher");

			if (!ReflectionUtil.shouldContinueAfterReflection(reflectionResult)) {
				logger.debug("Step {} reflection processing completed, skipping execution", assignedStep.getTitle());
				return updated;
			}
		}

		// Mark step as processing
		assignedStep.setExecutionStatus(StateUtil.EXECUTION_STATUS_PROCESSING_PREFIX + nodeName);

		try {
			// Build task messages
			List<Message> messages = new ArrayList<>();
			TemplateUtil.addShortUserRoleMemory(messages, state);
			// Build task message with reflection history
			String originTaskContent = buildTaskMessage(assignedStep);
			String taskContent = buildTaskMessageWithReflectionHistory(assignedStep);
			Message taskMessage = new UserMessage(taskContent);
			messages.add(taskMessage);

			// Add researcher-specific citation reminder
			Message citationMessage = new UserMessage(
					"IMPORTANT: DO NOT include inline citations in the text. Instead, track all sources and include a References section at the end using link reference format. Include an empty line between each citation for better readability. Use this format for each reference:\\n- [Source Title](URL)\\n- [Source Title](URL)");
			messages.add(citationMessage);

			logger.debug("{} Node messages: {}", nodeName, messages);

			boolean enableSearchFilter = state.value("enable_search_filter", true);

			AgentSelectionResult agentSelection = selectSmartAgent(assignedStep, taskContent, state);
			ChatClient selectedAgent = agentSelection.getSelectedAgent();

			// 将智能Agent的状态更新合并到updated中
			updated.putAll(agentSelection.getStateUpdate());

				AsyncMcpToolCallbackProvider mcpProvider = mcpFactory != null
						? mcpFactory.createProvider(state, "researchAgent") : null;
				ToolCallback[] researchMcpToolCallbacks = prepareResearchMcpToolCallbacks(
						mcpProvider != null ? mcpProvider.getToolCallbacks() : null, researchMcpExtraSources);
				boolean hasMcpTools = researchMcpToolCallbacks.length > 0;
				logFilteredResearchMcpTools(mcpProvider, researchMcpToolCallbacks);
				String researchSearchMode = StateUtil.getResearchSearchMode(state);
				ResearchSearchOrchestrator.ResearchSearchExecution searchExecution = researchSearchOrchestrator.execute(
						researchSearchMode, hasMcpTools,
						() -> invokeResearchAttempt(selectedAgent, messages, researchMcpToolCallbacks, true,
								enableSearchFilter),
						() -> invokeResearchAttempt(selectedAgent, messages, null, false, enableSearchFilter));
				putResearchSearchState(updated, executorNodeId, searchExecution);
				logger.info("ResearcherNode {} route={} fallbackReason={}", executorNodeId, searchExecution.route(),
						searchExecution.fallbackReason());

			ChatResponse finalResponse = createCompletedChatResponse(searchExecution.content());
			Flux<ChatResponse> streamResult = Flux.just(finalResponse);

			// Add step title
			boolean isReflectionNode = assignedStep.getReflectionHistory() != null
					&& !assignedStep.getReflectionHistory().isEmpty();
			String prefix = isReflectionNode ? StreamNodePrefixEnum.RESEARCHER_REFLECT_LLM_STREAM.getPrefix()
					: StreamNodePrefixEnum.RESEARCHER_LLM_STREAM.getPrefix();
			String nodeNum = NodeStepTitleUtil.registerStepTitle(state, isReflectionNode, executorNodeId, "Researcher",
					assignedStep.getTitle(), prefix);

			logger.info("ResearcherNode {} starting streaming with key: {}", executorNodeId, nodeName);

			Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder()
				.startingNode(nodeNum)
				.startingState(state)
				.mapResult(response -> {
					// Only handle successful responses - errors are handled in doOnError
					String researchContent = response.getResult().getOutput().getText();
					assignedStep
						.setExecutionStatus(ReflectionUtil.getCompletionStatus(reflectionProcessor != null, nodeName));
					assignedStep.setExecutionRes(Objects.requireNonNull(researchContent));
					logger.info("{} completed, content: {}", nodeName, researchContent);

					updated.put("researcher_content_" + executorNodeId, researchContent);
					return updated;
				})
				.buildWithChatResponse(streamResult);

			updated.put("researcher_content_" + executorNodeId, generator);
			return updated;
		}
		catch (Exception e) {
			// Handle any exception that occurs before or during stream setup
			StateUtil.handleStepError(assignedStep, nodeName, e, logger);
			return updated;
		}
	}

	/**
	 * Find steps assigned to current node
	 */
	private Plan.Step findAssignedStep(Plan currentPlan) {
		for (Plan.Step step : currentPlan.getSteps()) {
			if (Plan.StepType.RESEARCH.equals(step.getStepType()) && ReflectionUtil.shouldProcessStep(step, nodeName)) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Build task message
	 */
	private String buildTaskMessage(Plan.Step step) {
		StringBuilder content = new StringBuilder();

		// Basic task information
		content.append("# Current Task\n\n")
			.append("## Title\n\n")
			.append(step.getTitle())
			.append("\n\n")
			.append("## Description\n\n")
			.append(step.getDescription())
			.append("\n\n");

		return content.toString();
	}

	private String invokeResearchAttempt(ChatClient selectedAgent, List<Message> baseMessages,
			ToolCallback[] mcpToolCallbacks, boolean mcpFirstAttempt, boolean enableSearchFilter) {
		List<Message> attemptMessages = buildAttemptMessages(baseMessages, mcpFirstAttempt, mcpToolCallbacks);
		var requestSpec = selectedAgent.prompt()
			.options(DashScopeChatOptions.builder().withParallelToolCalls(false).build());

		if (mcpFirstAttempt) {
			if (mcpToolCallbacks != null && mcpToolCallbacks.length > 0) {
				requestSpec = requestSpec.toolCallbacks(mcpToolCallbacks);
			}
			if (memorySearchTool != null && memoryGetTool != null) {
				requestSpec = requestSpec.tools(memorySearchTool, memoryGetTool);
			}
			else if (memorySearchTool != null) {
				requestSpec = requestSpec.tools(memorySearchTool);
			}
			else if (memoryGetTool != null) {
				requestSpec = requestSpec.tools(memoryGetTool);
			}
		}
		else {
			SearchFilterTool searchFilterTool = new SearchFilterTool(searchFilterService, SearchEnum.TAVILY,
					enableSearchFilter);
			if (memorySearchTool != null && memoryGetTool != null) {
				requestSpec = requestSpec.tools(searchFilterTool, memorySearchTool, memoryGetTool);
			}
			else if (memorySearchTool != null) {
				requestSpec = requestSpec.tools(searchFilterTool, memorySearchTool);
			}
			else if (memoryGetTool != null) {
				requestSpec = requestSpec.tools(searchFilterTool, memoryGetTool);
			}
			else {
				requestSpec = requestSpec.tools(searchFilterTool);
			}
		}

		return requestSpec.messages(attemptMessages).call().chatResponse().getResult().getOutput().getText();
	}

	private List<Message> buildAttemptMessages(List<Message> baseMessages, boolean mcpFirstAttempt,
			ToolCallback[] mcpToolCallbacks) {
		List<Message> attemptMessages = new ArrayList<>(baseMessages);
		attemptMessages.add(new UserMessage(buildRuntimeToolMessage(mcpFirstAttempt, mcpToolCallbacks)));
		attemptMessages.add(new UserMessage(mcpFirstAttempt
				? "This is the MCP-first research attempt. Use only MCP tools exposed by the runtime schema for external retrieval. searchFilterTool is not available in this attempt. If MCP tools are unavailable, fail, time out, or return no usable evidence, output exactly [[FALLBACK_TO_TAVILY:<reason>]] and nothing else. Valid reasons: no_mcp_tool, tool_error, timeout, empty_result, no_usable_evidence."
				: "This is the Tavily fallback attempt. searchFilterTool is available in this attempt. Use it for web retrieval when needed and continue the research normally."));
		return attemptMessages;
	}

	private String buildRuntimeToolMessage(boolean mcpFirstAttempt, ToolCallback[] mcpToolCallbacks) {
		List<String> runtimeToolNames = new ArrayList<>();
		if (mcpFirstAttempt && mcpToolCallbacks != null && mcpToolCallbacks.length > 0) {
			runtimeToolNames.addAll(Arrays.stream(mcpToolCallbacks)
				.map(toolCallback -> toolCallback.getToolDefinition().name())
				.toList());
		}
		else if (!mcpFirstAttempt) {
			runtimeToolNames.add("searchFilterTool");
		}
		if (memorySearchTool != null) {
			runtimeToolNames.add("memorySearch");
		}
		if (memoryGetTool != null) {
			runtimeToolNames.add("memoryGet");
		}
		if (runtimeToolNames.isEmpty()) {
			runtimeToolNames.add("none");
		}
		return "Runtime available tools for this request (strict): " + String.join(", ", runtimeToolNames)
				+ ". Only call tools in this list or in the runtime tool schema. If a tool is not available in this attempt, do not call it. Call at most one tool per assistant message, wait for the tool result, and then decide whether another tool is needed. CRITICAL RULE: Maximum 3 tool calls allowed. If you have already used tools, analyze the existing results and output the final response. DO NOT repeat similar searches in a loop.";
	}

	static ToolCallback[] filterResearchMcpToolCallbacks(ToolCallback[] toolCallbacks) {
		if (toolCallbacks == null || toolCallbacks.length == 0) {
			return new ToolCallback[0];
		}
		return Arrays.stream(toolCallbacks)
			.filter(Objects::nonNull)
			.filter(toolCallback -> RESEARCH_MCP_SEARCH_TOOL_NAMES.contains(toolCallback.getToolDefinition().name()))
			.toArray(ToolCallback[]::new);
	}

	static ToolCallback[] prepareResearchMcpToolCallbacks(ToolCallback[] toolCallbacks, int researchMcpExtraSources) {
		return Arrays.stream(filterResearchMcpToolCallbacks(toolCallbacks))
			.map(toolCallback -> ResearchMcpToolCallbackAdapter.wrapSearchCallback(toolCallback, researchMcpExtraSources))
			.toArray(ToolCallback[]::new);
	}

	static void putResearchSearchState(Map<String, Object> updated, String executorNodeId,
			ResearchSearchOrchestrator.ResearchSearchExecution searchExecution) {
		updated.put(StateUtil.getResearchSearchRouteKey(executorNodeId), searchExecution.route());
		if (searchExecution.fallbackReason() != null) {
			updated.put(StateUtil.getResearchSearchFallbackReasonKey(executorNodeId), searchExecution.fallbackReason());
		}
	}

	private void logFilteredResearchMcpTools(AsyncMcpToolCallbackProvider mcpProvider, ToolCallback[] filteredCallbacks) {
		if (mcpProvider == null) {
			return;
		}
		ToolCallback[] allCallbacks = mcpProvider.getToolCallbacks();
		if (allCallbacks.length == filteredCallbacks.length) {
			return;
		}
		Set<String> filteredNames = Arrays.stream(filteredCallbacks)
			.map(toolCallback -> toolCallback.getToolDefinition().name())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		List<String> excludedNames = Arrays.stream(allCallbacks)
			.map(toolCallback -> toolCallback.getToolDefinition().name())
			.filter(name -> !filteredNames.contains(name))
			.toList();
		logger.info("ResearcherNode {} filtered MCP tools for research attempt. allowed={} excluded={}", executorNodeId,
				filteredNames, excludedNames);
	}

	private ChatResponse createCompletedChatResponse(String content) {
		org.springframework.ai.chat.messages.AssistantMessage message = new org.springframework.ai.chat.messages.AssistantMessage(
				content);
		org.springframework.ai.chat.model.Generation generation = new org.springframework.ai.chat.model.Generation(
				message);
		return new ChatResponse(List.of(generation));
	}

	/**
	 * Build task message with reflection history
	 */
	private String buildTaskMessageWithReflectionHistory(Plan.Step step) {
		StringBuilder content = new StringBuilder();

		// Basic task information
		content.append("# Current Task\n\n")
			.append("## Title\n\n")
			.append(step.getTitle())
			.append("\n\n")
			.append("## Description\n\n")
			.append(step.getDescription())
			.append("\n\n");

		// Add reflection history if available
		if (ReflectionUtil.hasReflectionHistory(step)) {
			content.append(ReflectionUtil.buildReflectionHistoryContent(step));
			content.append(
					"Please re-complete this research task based on the above previous attempt results and reflection feedback, ensuring to avoid the previously identified issues and improve upon the previous results.\n\n");
		}

		return content.toString();
	}

	/**
	 * 智能选择Agent 如果智能Agent功能开启，则根据问题类型选择专业化Agent 否则使用原有的researchAgent
	 */
	private AgentSelectionResult selectSmartAgent(Plan.Step step, String taskContent, OverAllState state) {
		String questionContent = step.getTitle();
		if (step.getDescription() != null) {
			questionContent += " " + step.getDescription();
		}

		AgentSelectionResult selectionResult = smartAgentSelectionHelper.selectSmartAgent(questionContent, state,
				researchAgent);

		if (selectionResult.isSmartAgent()) {
			logger.info("为研究任务选择智能Agent: {} -> {} (executorNodeId: {})", questionContent,
					selectionResult.getAgentType(), executorNodeId);
		}
		else {
			logger.debug("使用默认researchAgent: {} (executorNodeId: {})", selectionResult.getReason(), executorNodeId);
		}

		return selectionResult;
	}

}
