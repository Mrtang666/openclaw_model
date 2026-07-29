package com.example.spring.xhs.source;

public record XhsCollectorSubmission(String externalJobId) {

    public XhsCollectorSubmission {
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new IllegalArgumentException("采集侧车没有返回任务 ID");
        }
        externalJobId = externalJobId.strip();
    }
}
