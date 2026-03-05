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

package com.alibaba.cloud.ai.example.deepresearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Long-term memory configuration properties. Controls the OpenClaw-style Markdown
 * file-based persistent memory system.
 *
 * @author deepresearch
 */
@ConfigurationProperties(prefix = LongTermMemoryProperties.PREFIX)
public class LongTermMemoryProperties {

	public static final String PREFIX = DeepResearchProperties.PREFIX + ".long-term-memory";

	/**
	 * Whether long-term memory is enabled.
	 */
	private boolean enabled = false;

	/**
	 * Workspace root path for memory files. Defaults to ~/.deepresearch/workspace. Memory
	 * files will be stored under {workspacePath}/memory/.
	 */
	private String workspacePath = System.getProperty("user.home") + "/.deepresearch/workspace";

	/**
	 * Whether to automatically flush memory after report generation in ReporterNode.
	 */
	private boolean autoFlush = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(String workspacePath) {
		this.workspacePath = workspacePath;
	}

	public boolean isAutoFlush() {
		return autoFlush;
	}

	public void setAutoFlush(boolean autoFlush) {
		this.autoFlush = autoFlush;
	}

	/**
	 * Get the memory directory path: {workspacePath}/memory/
	 */
	public String getMemoryDirectoryPath() {
		return workspacePath + "/memory";
	}

	/**
	 * Get the MEMORY.md file path: {workspacePath}/memory/MEMORY.md
	 */
	public String getMemoryFilePath() {
		return getMemoryDirectoryPath() + "/MEMORY.md";
	}

}
