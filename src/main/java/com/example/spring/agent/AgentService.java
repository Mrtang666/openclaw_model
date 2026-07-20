package com.example.spring.agent;


/**
 * Agent 入口层组件，负责协调 CLI 输入和回复输出。
 */
import com.example.spring.cli.command.core.CommandDispatcher;
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

