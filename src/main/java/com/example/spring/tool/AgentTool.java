package com.example.spring.tool;


/**
 * CLI工具的统一接口
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import java.util.Map;

public interface AgentTool {

    String name();

    String execute(Map<String, String> arguments);
}


