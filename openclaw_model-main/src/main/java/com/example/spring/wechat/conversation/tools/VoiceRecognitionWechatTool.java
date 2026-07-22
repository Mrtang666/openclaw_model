package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;
import com.example.spring.wechat.voice.recognition.service.VoiceRecognitionService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信语音识别工具，把当前消息中的语音附件统一转成文本，供后续工具规划继续处理。
 */
@Component
public class VoiceRecognitionWechatTool implements WechatTool {

    private final VoiceRecognitionService voiceRecognitionService;

    public VoiceRecognitionWechatTool(VoiceRecognitionService voiceRecognitionService) {
        this.voiceRecognitionService = voiceRecognitionService;
    }

    @Override
    public String name() {
        return "voice_recognition";
    }

    @Override
    public String description() {
        return "内部工具：把微信用户发来的语音附件识别成文本，只在当前消息包含语音附件时使用";
    }

    @Override
    public List<String> arguments() {
        return List.of("voice_index");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalString(
                "voice_index",
                "当前微信消息中要识别的语音序号；通常由系统自动处理，普通文本提到语音时不要调用本工具",
                "1"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "把当前微信消息里的语音附件识别成文本，供后续聊天、天气、图片生成、文档等工具继续处理。",
                List.of("只在当前消息真的包含语音附件时调用；用户在纯文本中提到语音不代表要调用语音识别。"),
                List.of("voices：当前微信消息中的语音附件"),
                List.of("语音转写文本"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        if (request.voices().isEmpty()) {
            return WechatReply.text("没有收到可识别的语音内容");
        }

        StringBuilder recognizedText = new StringBuilder();
        for (WechatIncomingVoice voice : request.voices()) {
            VoiceRecognitionResult result = voiceRecognitionService.recognize(voice);
            if (result != null && result.text() != null && !result.text().isBlank()) {
                if (recognizedText.length() > 0) {
                    recognizedText.append('\n');
                }
                recognizedText.append(result.text().strip());
            }
        }
        return WechatReply.text(recognizedText.toString().strip());
    }
}
