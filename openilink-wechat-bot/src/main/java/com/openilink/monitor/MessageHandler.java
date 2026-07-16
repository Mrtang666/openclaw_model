package com.openilink.monitor;

import com.openilink.model.WeixinMessage;

@FunctionalInterface
public interface MessageHandler {
    void handle(WeixinMessage msg);
}
