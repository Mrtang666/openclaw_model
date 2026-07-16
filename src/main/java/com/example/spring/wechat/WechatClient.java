package com.example.spring.wechat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WechatClient extends AutoCloseable {

    String executeLogin();

    CompletableFuture<WechatLoginInfo> loginFuture();

    List<WechatIncomingMessage> getUpdates() throws IOException;

    void sendText(String toUserId, String text) throws IOException;

    @Override
    void close();
}
