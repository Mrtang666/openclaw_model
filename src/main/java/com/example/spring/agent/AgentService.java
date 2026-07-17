package com.example.spring.agent;

import com.example.spring.command.CommandDispatcher;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final CommandDispatcher dispatcher;

    public AgentService(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public String handle(String input) {
        return dispatcher.dispatch(input);
    }

    public void handleStreaming(String input, ReplyEmitter emitter) {
        dispatcher.dispatchStreaming(input, emitter);
    }
}
