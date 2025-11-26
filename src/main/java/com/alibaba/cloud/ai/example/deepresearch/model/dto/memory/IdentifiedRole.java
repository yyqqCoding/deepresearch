package com.alibaba.cloud.ai.example.deepresearch.model.dto.memory;

import com.alibaba.cloud.ai.example.deepresearch.model.enums.ConfidenceLevel;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author benym
 */
public class IdentifiedRole {

    /**
     * 可能的职业
     */
    @JsonProperty("possibleOccupations")
    private List<String> possibleOccupations;

    /**
     * 角色特征
     */
    @JsonProperty("primaryCharacteristics")
    private List<String> primaryCharacteristics;

    /**
     * 角色特征证据摘要
     */
    @JsonProperty("evidenceSummary")
    private String evidenceSummary;

    /**
     * 置信度等级
     */
    @JsonProperty("confidenceLevel")
    private ConfidenceLevel confidenceLevel;

    public List<String> getPossibleOccupations() {
        return possibleOccupations;
    }

    public void setPossibleOccupations(List<String> possibleOccupations) {
        this.possibleOccupations = possibleOccupations;
    }

    public ConfidenceLevel getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }

    public void setEvidenceSummary(String evidenceSummary) {
        this.evidenceSummary = evidenceSummary;
    }

    public List<String> getPrimaryCharacteristics() {
        return primaryCharacteristics;
    }

    public void setPrimaryCharacteristics(List<String> primaryCharacteristics) {
        this.primaryCharacteristics = primaryCharacteristics;
    }
}
