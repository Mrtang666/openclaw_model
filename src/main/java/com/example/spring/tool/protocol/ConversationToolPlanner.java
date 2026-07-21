package com.example.spring.tool.protocol;

import com.example.spring.wechat.conversation.tools.WechatToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 微信端任务拆解规划器的统一接口。
 *
 * <p>旧版 prompt-json 规划器和新版 Function Calling 规划器都实现这个接口，
 * 主业务流程只关心“拿到用户输入后生成结构化任务”，不关心底层具体使用哪种协议。</p>
 */
public interface ConversationToolPlanner {

    /**
     * 根据用户输入、可用工具定义和最近上下文，生成本轮对话的结构化任务决策。
     *
     * @param userText 用户当前输入
     * @param toolDefinitions 当前已经注册到工具中心的工具定义
     * @param historyText 最近对话上下文
     * @return 可执行的任务决策；为空表示规划失败或不需要走工具流程
     */
    Optional<ConversationIntentDecision> planDecision(
            String userText,
            List<WechatToolDefinition> toolDefinitions,
            String historyText);
}
