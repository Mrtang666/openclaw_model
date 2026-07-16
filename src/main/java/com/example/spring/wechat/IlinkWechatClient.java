package com.example.spring.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IlinkWechatClient implements WechatClient {

    private final ILinkClient delegate;

    public IlinkWechatClient() {
        this(ILinkClient.builder().build());
    }

    IlinkWechatClient(ILinkClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String executeLogin() {
        return delegate.executeLogin();
    }

    @Override
    public CompletableFuture<WechatLoginInfo> loginFuture() {
        return delegate.getLoginFuture().thenApply(this::toLoginInfo);
    }

    @Override
    public List<WechatIncomingMessage> getUpdates() throws IOException {
        List<WeixinMessage> messages = delegate.getUpdates();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<WechatIncomingMessage> incomingMessages = new ArrayList<>();
        for (WeixinMessage message : messages) {
            if (message.getFrom_user_id() == null || message.getItem_list() == null) {
                continue;
            }
            for (MessageItem item : message.getItem_list()) {
                if (item.getText_item() != null && item.getText_item().getText() != null) {
                    incomingMessages.add(new WechatIncomingMessage(
                            message.getFrom_user_id(),
                            item.getText_item().getText()));
                }
            }
        }
        return incomingMessages;
    }

    @Override
    public void sendText(String toUserId, String text) throws IOException {
        delegate.sendText(toUserId, text);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private WechatLoginInfo toLoginInfo(LoginContext context) {
        return new WechatLoginInfo(context.getBotId());
    }
}
