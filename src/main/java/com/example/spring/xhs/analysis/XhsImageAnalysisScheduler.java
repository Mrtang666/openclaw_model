package com.example.spring.xhs.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.image-analysis", name = "enabled", havingValue = "true")
public class XhsImageAnalysisScheduler {

    private final XhsImageAnalysisPipeline pipeline;

    public XhsImageAnalysisScheduler(XhsImageAnalysisPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Scheduled(fixedDelayString = "${xhs.image-analysis.polling-delay:30s}")
    public void analyze() {
        pipeline.processPending();
    }
}
