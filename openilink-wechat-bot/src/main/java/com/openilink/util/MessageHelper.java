package com.openilink.util;

import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.WeixinMessage;

public class MessageHelper {
    private MessageHelper() {}

    public static String extractText(WeixinMessage msg) {
        if (msg == null || msg.getItemList() == null) return null;
        for (MessageItem item : msg.getItemList()) {
            if (item.getType() == MessageItemType.TEXT && item.getTextItem() != null && item.getTextItem().getText() != null) {
                return item.getTextItem().getText();
            }
        }
        return null;
    }
}
