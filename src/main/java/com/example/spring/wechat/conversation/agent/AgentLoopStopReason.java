package com.example.spring.wechat.conversation.agent;

enum AgentLoopStopReason {
    NONE,
    FINAL_ANSWER,
    MODEL_EMPTY,
    MAX_ROUNDS,
    TOOL_FAILURE,
    NEEDS_CLARIFICATION,
    MEDIA_RESULT,
    SPECIAL_TOOL_DONE
}
