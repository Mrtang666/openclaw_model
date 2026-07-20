package com.example.spring.tool;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import java.util.Map;

public interface AgentTool {

    String name();

    String execute(Map<String, String> arguments);
}


