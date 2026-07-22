package com.example.feature.media;

import com.example.MediaHelper;
import com.example.application.ReplyOrchestrator;
import com.example.speech.SpeechRecognitionService;
import com.example.vision.VisionService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VoiceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts incoming image and voice messages into application-level text or replies. */
public class IncomingMediaHandler {

    private static final Logger log = LoggerFactory.getLogger(IncomingMediaHandler.class);

    private final VisionService vision;
    private final SpeechRecognitionService speech;
    private final ReplyOrchestrator replies;

    public IncomingMediaHandler(VisionService vision,
                                SpeechRecognitionService speech,
                                ReplyOrchestrator replies) {
        this.vision = vision;
        this.speech = speech;
        this.replies = replies;
    }

    public void handleImage(ILinkClient client, MessageItem item, String userId, String messageId) {
        log.info("收到用户 [{}] 的图片，正在识别...", userId);
        replies.reply(client, userId, "正在识别图片，请稍候...");
        try {
            byte[] imageBytes = client.downloadImageFromMessageItem(item);
            MediaHelper.downloadAndSave(imageBytes, userId, messageId, ".jpg", "image");
            String description = vision.describeImage(imageBytes, "image/jpeg");
            replies.reply(client, userId,
                    description == null || description.isBlank()
                            ? "识别图片失败，请稍后重试。" : description);
        } catch (Exception e) {
            log.error("处理图片失败", e);
            replies.reply(client, userId, "图片识别出错了：" + e.getMessage());
        }
    }

    public String transcribeVoice(ILinkClient client, MessageItem item,
                                  String userId, String messageId) {
        log.info("收到用户 [{}] 的语音消息，正在识别...", userId);
        VoiceItem voice = item.getVoice_item();
        if (voice != null) {
            log.info("收到语音元数据: user={}, encodeType={}, bitsPerSample={}, sampleRate={}, playtime={}, text={}",
                    userId, voice.getEncode_type(), voice.getBits_per_sample(),
                    voice.getSample_rate(), voice.getPlaytime(), voice.getText());
        }
        try {
            byte[] voiceBytes = client.downloadVoiceFromMessageItem(item);
            String fileName = (messageId != null ? messageId : String.valueOf(System.currentTimeMillis())) + ".silk";
            MediaHelper.downloadAndSave(voiceBytes, userId, messageId, ".silk", "voice");

            String text = speech.transcribe(voiceBytes, fileName);
            if ((text == null || text.isBlank()) && voice != null) {
                text = voice.getText();
            }
            if (text == null || text.isBlank()) {
                replies.reply(client, userId, "这条语音暂时没听清，可以再发一次或换成文字。");
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            log.error("处理语音消息失败", e);
            replies.reply(client, userId, "语音识别出错了：" + e.getMessage());
            return null;
        }
    }

    public void handleVideo(ILinkClient client, String userId) {
        log.info("收到用户 [{}] 的视频", userId);
        replies.reply(client, userId, "收到视频，暂不支持处理。");
    }
}
