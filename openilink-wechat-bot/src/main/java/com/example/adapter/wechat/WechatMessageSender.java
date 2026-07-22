package com.example.adapter.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;

import java.io.IOException;

/** Adapter around the iLink SDK so application code does not depend on SDK calls. */
public class WechatMessageSender {

    public void startTyping(ILinkClient client, String userId) throws IOException {
        client.startTyping(userId);
    }

    public void stopTyping(ILinkClient client, String userId) throws IOException {
        client.stopTyping(userId);
    }

    public void sendText(ILinkClient client, String userId, String text) throws IOException {
        client.sendText(userId, text);
    }

    public void sendImage(ILinkClient client, String userId, byte[] bytes,
                          String fileName, String caption) throws IOException {
        client.sendImage(userId, bytes, fileName, caption);
    }

    public void sendFile(ILinkClient client, String userId, byte[] bytes,
                         String fileName, String caption) throws IOException {
        client.sendFile(userId, bytes, fileName, caption);
    }

    public void sendVoice(ILinkClient client, String userId, byte[] bytes,
                          String fileName, Integer playTime, Integer sampleRate,
                          String contextToken, Integer encodeType,
                          Integer bitsPerSample, String text) throws IOException {
        client.sendVoice(userId, bytes, fileName, playTime, sampleRate,
                contextToken, encodeType, bitsPerSample, text);
    }
}
