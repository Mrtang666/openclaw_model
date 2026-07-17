package com.example.spring.agent;

import com.example.spring.memory.ConversationMemoryService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentCoordinator {
    private final Map<AgentType, ModuleAgent> agents = new EnumMap<>(AgentType.class);
    private final ConversationMemoryService memoryService;

    public AgentCoordinator(
        List<ModuleAgent> moduleAgents,
        ConversationMemoryService memoryService) {
        this.memoryService = memoryService;
        for (ModuleAgent agent : moduleAgents) {
            agents.put(agent.type(), agent);
        }
    }

    public AgentResponse execute(AgentPlan plan, AgentRequest request) throws Exception {
        memoryService.rememberUserRequest(request);
        AgentResponse latest = AgentResponse.text("");
        AgentRequest current = request;
        for (AgentType step : plan.steps()) {
            ModuleAgent agent = agents.get(step);
            if (agent == null) {
                throw new IllegalStateException("未配置 Agent：" + step);
            }
            latest = agent.execute(current);
            memoryService.rememberAgentResult(step, current, latest);
            if (step == AgentType.VISION && plan.steps().contains(AgentType.IMAGE_GENERATION)) {
                current = request.withText(
                    request.text() + "\n参考图片识别结果：" + latest.text());
            }
        }
        return latest;
    }
}
