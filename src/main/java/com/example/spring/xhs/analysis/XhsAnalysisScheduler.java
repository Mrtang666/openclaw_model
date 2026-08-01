package com.example.spring.xhs.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.analysis", name = "enabled", havingValue = "true")
public class XhsAnalysisScheduler {

    private final XhsAnalysisPipeline pipeline;

    public XhsAnalysisScheduler(XhsAnalysisPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Scheduled(fixedDelayString = "${xhs.analysis.polling-delay:15s}")
    public void analyze() {
        pipeline.processPending();
    }
}
