package com.example.spring.tool;

import java.util.Map;

public interface AgentTool {

    String name();

    String execute(Map<String, String> arguments);
}

