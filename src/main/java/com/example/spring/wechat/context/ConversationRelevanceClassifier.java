package com.example.spring.wechat.context;

import java.util.List;

public interface ConversationRelevanceClassifier {

    ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics);
}
