package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplyModeCommandTests {
    @Test
    void recognizesEnableAndDisableCommands() {
        assertThat(ReplyModeCommand.parse("开启语音对话")).isEqualTo(ReplyModeCommand.ENABLE_VOICE);
        assertThat(ReplyModeCommand.parse("以后请用语音回复我")).isEqualTo(ReplyModeCommand.ENABLE_VOICE);
        assertThat(ReplyModeCommand.parse("关闭语音模式")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("恢复文字回复")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("不要用语音回复了")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("别再发语音了")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("把语音模式关掉")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("关闭对话模式")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("退出对话模式")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
        assertThat(ReplyModeCommand.parse("结束对话模式吧")).isEqualTo(ReplyModeCommand.DISABLE_VOICE);
    }

    @Test
    void doesNotTreatNormalVoiceRequestsAsModeChanges() {
        assertThat(ReplyModeCommand.parse("帮我识别这段语音")).isEqualTo(ReplyModeCommand.NONE);
        assertThat(ReplyModeCommand.parse("查询今天的天气")).isEqualTo(ReplyModeCommand.NONE);
        assertThat(ReplyModeCommand.parse("不要关闭语音模式")).isEqualTo(ReplyModeCommand.NONE);
    }
}
