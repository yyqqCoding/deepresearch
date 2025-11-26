package com.alibaba.cloud.ai.example.deepresearch.model.dto.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author benym
 */
public class ShortUserRoleExtractResult {

    /**
     * 用户ID
     */
    @JsonProperty("userId")
    private String userId;

    /**
     * 会话ID
     */
    @JsonProperty("conversationId")
    private String conversationId;

    /**
     * 会话分析信息
     */
    @JsonProperty("conversationAnalysis")
    private ConversationAnalysis conversationAnalysis;

    /**
     * 角色识别信息
     */
    @JsonProperty("identifiedRole")
    private IdentifiedRole identifiedRole;

    /**
     * 交流偏好信息
     */
    @JsonProperty("communicationPreferences")
    private CommunicationPreferences communicationPreferences;

    /**
     * 综合上述实体的用户概述信息
     */
    @JsonProperty("userOverview")
    private String userOverview;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public ConversationAnalysis getConversationAnalysis() {
        return conversationAnalysis;
    }

    public void setConversationAnalysis(ConversationAnalysis conversationAnalysis) {
        this.conversationAnalysis = conversationAnalysis;
    }

    public IdentifiedRole getIdentifiedRole() {
        return identifiedRole;
    }

    public void setIdentifiedRole(IdentifiedRole identifiedRole) {
        this.identifiedRole = identifiedRole;
    }

    public CommunicationPreferences getCommunicationPreferences() {
        return communicationPreferences;
    }

    public void setCommunicationPreferences(CommunicationPreferences communicationPreferences) {
        this.communicationPreferences = communicationPreferences;
    }

    public String getUserOverview() {
        return userOverview;
    }

    public void setUserOverview(String userOverview) {
        this.userOverview = userOverview;
    }
}
