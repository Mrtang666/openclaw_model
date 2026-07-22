package com.example.spring.wechat.adapter;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatLoginInfo;
import com.example.spring.wechat.model.WechatLoginState;

public interface WechatClient extends AutoCloseable {

    String executeLogin();

    CompletableFuture<WechatLoginInfo> loginFuture();

    default WechatLoginState loginState() {
        CompletableFuture<WechatLoginInfo> future = loginFuture();
        if (future.isCompletedExceptionally() || future.isCancelled()) {
            return WechatLoginState.ERROR;
        }
        return future.isDone() ? WechatLoginState.LOGGED_IN : WechatLoginState.WAITING;
    }

    List<WechatIncomingMessage> getUpdates() throws IOException;

    void sendText(String toUserId, String text) throws IOException;

    void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) throws IOException;

    default void sendFile(String toUserId, byte[] fileBytes, String fileName, String caption) throws IOException {
        throw new UnsupportedOperationException("当前微信客户端暂不支持发送文件");
    }

    default void sendVoice(
            String toUserId,
            byte[] voiceBytes,
            String fileName,
            Integer playTimeMs,
            Integer sampleRate,
            Integer encodeType,
            Integer bitsPerSample,
            String transcriptText) throws IOException {
        throw new UnsupportedOperationException("当前微信客户端暂不支持发送语音");
    }

    @Override
    void close();
}

