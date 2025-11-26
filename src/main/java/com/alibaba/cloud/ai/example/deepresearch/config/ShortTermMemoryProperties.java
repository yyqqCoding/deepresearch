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
     * Number of recent messages to retain in short-term memory
     */
    private int recentMessageCount = 10;

    /**
     * Type of memory storage
     */
    private MemoryType memoryType = MemoryType.IN_MEMORY;

    public static enum  MemoryType{

        IN_MEMORY
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRecentMessageCount() {
        return recentMessageCount;
    }

    public void setRecentMessageCount(int recentMessageCount) {
        this.recentMessageCount = recentMessageCount;
    }

    public MemoryType getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(MemoryType memoryType) {
        this.memoryType = memoryType;
    }
}
