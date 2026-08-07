package com.example.spring.xhs.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XhsCommentRiskClassifierTests {

    @Test
    void classifiesNegativeProductFeedbackWithoutCallingAModel() {
        var result = XhsCommentRiskClassifier.classify(
                "\u7528\u5b8c\u8fc7\u654f\u7ea2\u80bf\uff0c\u5f88\u5931\u671b\uff0c\u4e0d\u63a8\u8350");

        assertThat(result.negative()).isTrue();
        assertThat(result.sentiment()).isEqualTo("NEGATIVE");
        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void keepsOrdinaryQuestionsNeutral() {
        var result = XhsCommentRiskClassifier.classify(
                "\u8bf7\u95ee\u8fd9\u4e2a\u4ec0\u4e48\u65f6\u5019\u8865\u8d27");

        assertThat(result.negative()).isFalse();
        assertThat(result.riskScore()).isZero();
    }
}
