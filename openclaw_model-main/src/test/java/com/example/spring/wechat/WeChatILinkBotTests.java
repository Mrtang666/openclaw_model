package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.VoiceItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeChatILinkBotTests {
    @Test
    void recognizesNonBlankTextMessages() {
        WeixinMessage message = new WeixinMessage();
        message.setItem_list(List.of(MessageItem.text("hello"), MessageItem.text("world")));

        assertThat(WeChatILinkBot.containsText(message)).isTrue();
        assertThat(WeChatILinkBot.extractText(message)).isEqualTo("hello\nworld");
    }

    @Test
    void ignoresMessagesWithoutUsableText() {
        WeixinMessage blankText = new WeixinMessage();
        blankText.setItem_list(List.of(MessageItem.text("  ")));

        WeixinMessage noItems = new WeixinMessage();
        noItems.setItem_list(List.of());

        assertThat(WeChatILinkBot.containsText(blankText)).isFalse();
        assertThat(WeChatILinkBot.containsText(noItems)).isFalse();
        assertThat(WeChatILinkBot.containsText(null)).isFalse();
    }

    @Test
    void recognizesImagesAndVoiceTranscription() {
        MessageItem image = new MessageItem();
        image.setImage_item(new ImageItem());
        MessageItem voice = new MessageItem();
        VoiceItem voiceItem = new VoiceItem();
        voiceItem.setText("查询无锡天气");
        voice.setVoice_item(voiceItem);

        WeixinMessage message = new WeixinMessage();
        message.setItem_list(List.of(image, voice));

        WeChatILinkBot.MessageSummary summary = WeChatILinkBot.summarize(message);
        assertThat(summary.text()).isEqualTo("查询无锡天气");
        assertThat(summary.imageItems()).hasSize(1);
        assertThat(summary.voiceItems()).isEmpty();
        assertThat(summary.hasProcessableContent()).isTrue();
    }

    @Test
    void keepsBlankVoiceForExternalRecognition() {
        MessageItem voice = new MessageItem();
        voice.setVoice_item(new VoiceItem());

        WeixinMessage message = new WeixinMessage();
        message.setItem_list(List.of(voice));

        WeChatILinkBot.MessageSummary summary = WeChatILinkBot.summarize(message);
        assertThat(summary.voiceItems()).containsExactly(voice);
        assertThat(summary.hasProcessableContent()).isTrue();
    }

    @Test
    void recognizesFilesAsProcessableContent() {
        MessageItem file = new MessageItem();
        FileItem fileItem = new FileItem();
        fileItem.setFile_name("report.pdf");
        file.setFile_item(fileItem);
        WeixinMessage message = new WeixinMessage();
        message.setItem_list(List.of(file));

        WeChatILinkBot.MessageSummary summary = WeChatILinkBot.summarize(message);

        assertThat(summary.fileItems()).containsExactly(file);
        assertThat(summary.unsupportedTypes()).doesNotContain("文件");
        assertThat(summary.hasProcessableContent()).isTrue();
    }

    @Test
    void enablesHeartbeatAndAutomaticReconnect() {
        var config = WeChatILinkBot.createILinkConfig();

        assertThat(config.isHeartbeatEnabled()).isTrue();
        assertThat(config.getHeartbeatIntervalMs()).isEqualTo(30_000);
        assertThat(config.isAutoReconnectEnabled()).isTrue();
        assertThat(config.getReconnectMaxAttempts()).isEqualTo(5);
    }
}
