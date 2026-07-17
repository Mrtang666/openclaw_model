package com.example.spring.wechat.client;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IlinkWechatClient implements WechatClient {

    private static final Logger log = LoggerFactory.getLogger(IlinkWechatClient.class);

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
            log.debug("iLink getUpdates 没有新消息");
            return List.of();
        }

        log.debug("iLink getUpdates 拉取到 {} 条原始消息", messages.size());
        List<WechatIncomingMessage> incomingMessages = new ArrayList<>();
        for (WeixinMessage message : messages) {
            WechatIncomingMessage incoming = toIncomingMessage(message);
            if (incoming != null) {
                incomingMessages.add(incoming);
            }
        }

        log.debug("iLink 本次映射出 {} 条消息", incomingMessages.size());
        return incomingMessages;
    }

    @Override
    public void sendText(String toUserId, String text) throws IOException {
        delegate.sendText(toUserId, text);
    }

    @Override
    public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) throws IOException {
        delegate.sendImage(toUserId, imageBytes, fileName, caption);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private WechatIncomingMessage toIncomingMessage(WeixinMessage message) throws IOException {
        if (message == null || message.getFrom_user_id() == null || message.getItem_list() == null) {
            log.debug("iLink 消息被跳过：fromUserId 或 itemList 为空，messageId={}, messageType={}",
                    valueOrUnknown(message == null ? null : String.valueOf(message.getMessage_id())),
                    valueOrUnknown(message == null ? null : String.valueOf(message.getMessage_type())));
            return null;
        }

        StringBuilder text = new StringBuilder();
        List<WechatIncomingImage> images = new ArrayList<>();
        int imageIndex = 0;
        for (MessageItem item : message.getItem_list()) {
            if (item == null) {
                continue;
            }

            if (item.getText_item() != null && item.getText_item().getText() != null) {
                String chunk = item.getText_item().getText().strip();
                if (!chunk.isBlank()) {
                    if (text.length() > 0) {
                        text.append(System.lineSeparator());
                    }
                    text.append(chunk);
                }
            }

            if (item.getImage_item() != null) {
                try {
                    byte[] imageBytes = delegate.downloadImageFromMessageItem(item);
                    String sourceReference = buildSourceReference(message, imageIndex);
                    images.add(new WechatIncomingImage(
                            ImageSourceType.WECHAT_ATTACHMENT,
                            sourceReference,
                            imageBytes,
                            null,
                            buildFileName(message, imageIndex),
                            null,
                            null,
                            null));
                    imageIndex++;
                } catch (Exception exception) {
                    log.warn("iLink 图片下载失败，messageId={}, fromUserId={}, error={}",
                            valueOrUnknown(String.valueOf(message.getMessage_id())),
                            valueOrUnknown(message.getFrom_user_id()),
                            rootMessage(exception));
                }
            }
        }

        String normalizedText = text.toString().strip();
        if (normalizedText.isBlank() && images.isEmpty()) {
            log.debug("iLink 消息没有可用文本或图片，messageId={}", valueOrUnknown(String.valueOf(message.getMessage_id())));
            return null;
        }

        log.info(
                "iLink 收到消息，messageId={}, fromUserId={}, contextToken={}, text={}, imageCount={}",
                valueOrUnknown(String.valueOf(message.getMessage_id())),
                valueOrUnknown(message.getFrom_user_id()),
                valueOrUnknown(message.getContext_token()),
                preview(normalizedText),
                images.size());

        return new WechatIncomingMessage(
                message.getMessage_id() == null ? null : String.valueOf(message.getMessage_id()),
                message.getFrom_user_id(),
                message.getContext_token(),
                normalizedText,
                images);
    }

    private WechatLoginInfo toLoginInfo(LoginContext context) {
        return new WechatLoginInfo(context.getBotId());
    }

    private String buildSourceReference(WeixinMessage message, int imageIndex) {
        return "wechat://" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "/image/" + (imageIndex + 1);
    }

    private String buildFileName(WeixinMessage message, int imageIndex) {
        return "wechat-" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "-image-" + (imageIndex + 1) + ".png";
    }

    private String preview(String value) {
        if (value == null) {
            return "null";
        }
        String text = value.strip();
        if (text.length() <= 80) {
            return text;
        }
        return text.substring(0, 77) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "null" : value;
    }
}
