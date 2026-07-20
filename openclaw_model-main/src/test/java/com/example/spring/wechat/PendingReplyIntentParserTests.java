package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PendingReplyIntentParserTests {
    @Test
    void recognizesShortRecoveryConfirmations() {
        assertThat(PendingReplyIntentParser.parse("需要"))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.AFFIRM);
        assertThat(PendingReplyIntentParser.parse("继续回复。"))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.AFFIRM);
        assertThat(PendingReplyIntentParser.parse("不用了"))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.DECLINE);
    }

    @Test
    void treatsSubstantiveContentAsANewRequest() {
        assertThat(PendingReplyIntentParser.parse("我需要查询今天无锡的天气"))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.NEW_REQUEST);
        assertThat(PendingReplyIntentParser.parse("帮我继续修改上一张图片"))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.NEW_REQUEST);
    }

    @Test
    void keepsBlankContentUnclear() {
        assertThat(PendingReplyIntentParser.parse("  "))
            .isEqualTo(PendingReplyIntentParser.RecoveryIntent.UNCLEAR);
    }
}
