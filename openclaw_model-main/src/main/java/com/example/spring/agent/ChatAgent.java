package com.example.spring.agent;

import com.example.spring.bailian.BailianChatService;
import com.example.spring.time.TimeContextProvider;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent implements ModuleAgent {
    private final BailianChatService chatService;
    private final TimeContextProvider timeContextProvider;

    public ChatAgent(
        BailianChatService chatService,
        TimeContextProvider timeContextProvider) {
        this.chatService = chatService;
        this.timeContextProvider = timeContextProvider;
    }

    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        return AgentResponse.text(chatService.chat(
            request.userId(), request.text(), request.history(),
            timeContextProvider.modelContext()));
    }
}
