package com.alibaba.cloud.ai.example.deepresearch.node;

import com.alibaba.cloud.ai.example.deepresearch.config.ShortTermMemoryProperties;
import com.alibaba.cloud.ai.example.deepresearch.memory.ShortTermMemoryRepository;
import com.alibaba.cloud.ai.example.deepresearch.model.SessionHistory;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.memory.ConversationAnalysis;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.memory.ShortUserRoleExtractResult;
import com.alibaba.cloud.ai.example.deepresearch.service.SessionContextService;
import com.alibaba.cloud.ai.example.deepresearch.util.JsonUtil;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.example.deepresearch.util.TemplateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Short-term user role memory node
 *
 * @author benym
 */
public class ShortUserRoleMemoryNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ShortUserRoleMemoryNode.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String USER_ID = "MOCK_USER_ID";

    private final ChatClient shortMemoryAgent;

    private final SessionContextService sessionContextService;

    private final ShortTermMemoryProperties shortTermMemoryProperties;

    private final ShortTermMemoryRepository shortTermMemoryRepository;

    private final BeanOutputConverter<ShortUserRoleExtractResult> converter;

    public ShortUserRoleMemoryNode(ChatClient shortMemoryAgent, SessionContextService sessionContextService, ShortTermMemoryProperties shortTermMemoryProperties, ShortTermMemoryRepository shortTermMemoryRepository) {
        this.shortMemoryAgent = shortMemoryAgent;
        this.sessionContextService = sessionContextService;
        this.shortTermMemoryProperties = shortTermMemoryProperties;
        this.shortTermMemoryRepository = shortTermMemoryRepository;
        this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("short_user_role_memory node is running.");
        if (!shortTermMemoryProperties.isEnabled()) {
            return Collections.emptyMap();
        }
        Map<String, Object> updated = new HashMap<>();
        try {
            // 1. 获取最近n轮用户提问
            String historyUserMessages = buildHistoryUserMessages(state);
            // 2. 添加extract prompt消息
            ShortUserRoleExtractResult currentResult = extractShortTermMemory(state, historyUserMessages);
            // 3. 保存或更新短期记忆
            saveOrUpdateShortTermMemory(state, currentResult);
            updated.put("short_user_role_memory", JsonUtil.toJson(currentResult));
            updated.put("short_user_role_next_node", "coordinator");
            logger.info("generated short user role memory: {}", JsonUtil.toJson(currentResult));
        } catch (Exception e) {
            logger.error("short user role memory extraction failed, conversationId: {}", StateUtil.getSessionId(state), e);
            updated.put("short_user_role_next_node", "coordinator");
        }
        return updated;
    }

    /**
     * 构建历史用户消息
     *
     * @param state state
     * @return String
     */
    private String buildHistoryUserMessages(OverAllState state) {
        List<SessionHistory> recentReports = sessionContextService.getRecentReports(
                StateUtil.getSessionId(state),
                shortTermMemoryProperties.getRecentMessageCount()
        );
        if (CollectionUtils.isEmpty(recentReports)) {
            return "";
        }
        StringBuilder historyUserMessages = new StringBuilder();
        for (int i = 0; i < recentReports.size(); i++) {
            String userMessage = String.format("第%s轮, 用户消息:%s\n", i + 1, recentReports.get(i).getUserQuery());
            historyUserMessages.append(userMessage);
        }
        return historyUserMessages.toString();
    }

    /**
     * 提取用户角色短期记忆
     *
     * @param state               state
     * @param historyUserMessages 历史用户提问
     * @return ShortUserRoleExtractResult
     * @throws IOException IOException
     */
    private ShortUserRoleExtractResult extractShortTermMemory(OverAllState state, String historyUserMessages) throws IOException {
        List<Message> messages = Collections.singletonList(
                TemplateUtil.getShortMemoryExtractMessage(StateUtil.getQuery(state), historyUserMessages)
        );
        logger.debug("extract messages: {}", messages);
        ChatResponse chatResponse = callShortMemoryAgent(messages);
        String text = chatResponse.getResult().getOutput().getText();
        assert text != null;
        ShortUserRoleExtractResult result = converter.convert(text);
        assert result != null;
        initializeResult(state, result);
        return result;
    }

    /**
     * 调用短期记忆Agent
     */
    private ChatResponse callShortMemoryAgent(List<Message> messages) {
        return shortMemoryAgent.prompt(converter.getFormat())
                .messages(messages)
                .call()
                .chatResponse();
    }

    /**
     * 初始化结果对象
     */
    private void initializeResult(OverAllState state, ShortUserRoleExtractResult result) {
        result.setUserId(USER_ID);
        result.setConversationId(StateUtil.getSessionId(state));
        ConversationAnalysis analysis = result.getConversationAnalysis();
        analysis.setAnalysisDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DATE_TIME_FORMATTER));
    }

    /**
     * 保存或更新短期记忆
     */
    private void saveOrUpdateShortTermMemory(OverAllState state, ShortUserRoleExtractResult currentResult) throws IOException {
        Message historyShortResult = shortTermMemoryRepository.findLatestMessage(USER_ID, StateUtil.getSessionId(state));
        // 如果没有历史用户角色记忆，直接保存当前结果
        if (historyShortResult == null) {
            SystemMessage newShortMemory = new SystemMessage(JsonUtil.toJson(currentResult));
            shortTermMemoryRepository.saveAll(USER_ID, StateUtil.getSessionId(state), Collections.singletonList(newShortMemory));
            return;
        }
        ShortUserRoleExtractResult latestExtract = JsonUtil.fromJson(historyShortResult.getText(), ShortUserRoleExtractResult.class);
        Double latestConfidence = Objects.requireNonNull(latestExtract).getConversationAnalysis().getConfidenceScore();
        Double currentConfidence = currentResult.getConversationAnalysis().getConfidenceScore();
        // 如果当前结果的置信度更高，融合历史用户角色信息后更新短期记忆
        if (latestConfidence > currentConfidence) {
            mergeAndUpdateShortTermMemory(state, currentResult, latestExtract);
        }
    }

    /**
     * 合并并更新短期记忆
     */
    private void mergeAndUpdateShortTermMemory(OverAllState state, ShortUserRoleExtractResult current,
                                               ShortUserRoleExtractResult latest) throws IOException {
        // 组装update prompt消息
        List<Message> updateMessages = Collections.singletonList(
                TemplateUtil.getShortMemoryUpdateMessage(current, latest)
        );
        ChatResponse updateResponse = callShortMemoryAgent(updateMessages);
        String updateText = updateResponse.getResult().getOutput().getText();
        assert updateText != null;
        ShortUserRoleExtractResult mergedResult = converter.convert(updateText);
        SystemMessage mergedMemory = new SystemMessage(JsonUtil.toJson(mergedResult));
        shortTermMemoryRepository.update(USER_ID, StateUtil.getSessionId(state), Collections.singletonList(mergedMemory));
    }
}
