package com.example.spring.agent;

import com.example.spring.bailian.BailianChatService;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent implements ModuleAgent {
    private final BailianChatService chatService;

    public ChatAgent(BailianChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        return AgentResponse.text(chatService.chat(
            request.userId(), request.text(), request.history()));
    }
}
