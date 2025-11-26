package com.alibaba.cloud.ai.example.deepresearch.model.dto.memory;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author benym
 */
public class ConversationAnalysis {

    /**
     * 置信度分数
     */
    @JsonProperty("confidenceScore")
    private Double confidenceScore;

    /**
     * 交互次数
     */
    @JsonProperty("interactionCount")
    private Integer interactionCount;

    /**
     * 分析时间
     */
    @JsonProperty("analysisDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private String analysisDate;

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getInteractionCount() {
        return interactionCount;
    }

    public void setInteractionCount(Integer interactionCount) {
        this.interactionCount = interactionCount;
    }

    public String getAnalysisDate() {
        return analysisDate;
    }

    public void setAnalysisDate(String analysisDate) {
        this.analysisDate = analysisDate;
    }
}
