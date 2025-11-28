package com.alibaba.cloud.ai.example.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * long term user profile memory node
 *
 * @author benym
 */
public class LongUserProfileMemoryNode implements NodeAction {

	private final ChatClient longMemoryAgent;

	public LongUserProfileMemoryNode(ChatClient longMemoryAgent) {
		this.longMemoryAgent = longMemoryAgent;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		return Map.of();
	}

}
