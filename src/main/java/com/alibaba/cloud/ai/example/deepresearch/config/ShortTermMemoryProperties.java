package com.alibaba.cloud.ai.example.deepresearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Short-term memory configuration properties
 *
 * @author benym
 */
@ConfigurationProperties(prefix = ShortTermMemoryProperties.PREFIX)
public class ShortTermMemoryProperties {

	public static final String PREFIX = DeepResearchProperties.PREFIX + ".short-term-memory";

	/**
	 * Whether short-term memory is enabled
	 */
	private boolean enabled = true;

	/**
	 * Scope of short-term memory guidance
	 */
	private GuideScope guideScope = GuideScope.EVERY;

	/**
	 * Similarity threshold for updating short-term memory
	 */
	private Double updateSimilarityThreshold = 0.8;

	/**
	 * The number of recent user questions for reference in user role extraction
	 */
	private int historyUserMessagesNum = 10;

	/**
	 * Type of memory storage
	 */
	private MemoryType memoryType = MemoryType.IN_MEMORY;

	public enum GuideScope {

		/**
		 * Only in the first round of the guiding model
		 */
		ONCE,

		/**
		 * Each round will guide the model
		 */
		EVERY

	}

	public enum MemoryType {

		IN_MEMORY

	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public GuideScope getGuideScope() {
		return guideScope;
	}

	public void setGuideScope(GuideScope guideScope) {
		this.guideScope = guideScope;
	}

	public Double getUpdateSimilarityThreshold() {
		return updateSimilarityThreshold;
	}

	public void setUpdateSimilarityThreshold(Double updateSimilarityThreshold) {
		this.updateSimilarityThreshold = updateSimilarityThreshold;
	}

	public int getHistoryUserMessagesNum() {
		return historyUserMessagesNum;
	}

	public void setHistoryUserMessagesNum(int historyUserMessagesNum) {
		this.historyUserMessagesNum = historyUserMessagesNum;
	}

	public MemoryType getMemoryType() {
		return memoryType;
	}

	public void setMemoryType(MemoryType memoryType) {
		this.memoryType = memoryType;
	}

}
