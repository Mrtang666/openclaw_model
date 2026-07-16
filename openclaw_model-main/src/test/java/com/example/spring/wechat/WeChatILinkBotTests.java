package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeChatILinkBotTests {
    @Test
    void recognizesNonBlankTextMessages() {
        WeixinMessage message = new WeixinMessage();
        message.setItem_list(List.of(MessageItem.text("hello")));

        assertThat(WeChatILinkBot.containsText(message)).isTrue();
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
}
