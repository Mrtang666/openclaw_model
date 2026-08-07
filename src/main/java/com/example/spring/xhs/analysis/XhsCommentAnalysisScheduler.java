package com.example.spring.xhs.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.comment-analysis", name = "enabled", havingValue = "true")
public class XhsCommentAnalysisScheduler {

    private final XhsCommentAnalysisPipeline pipeline;

    public XhsCommentAnalysisScheduler(XhsCommentAnalysisPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Scheduled(fixedDelayString = "${xhs.comment-analysis.polling-delay:30s}")
    public void analyze() {
        pipeline.processPending();
    }
}
