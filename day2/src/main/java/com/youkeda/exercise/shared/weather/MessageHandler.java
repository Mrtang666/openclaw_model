package com.youkeda.exercise.shared.weather;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.TextItem;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.VoiceItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;

import java.io.IOException;
import java.util.List;

/**
 * 微信消息处理器 - 处理所有收到的消息
 */
public class MessageHandler {

    private final WeatherService weatherService;
    private ILinkClient client;

    public MessageHandler(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * 设置微信客户端（在登录成功后调用）
     */
    public void setClient(ILinkClient client) {
        this.client = client;
    }

    /**
     * 处理收到的消息
     */
    public void handleMessage(WeixinMessage msg) {
        // 检查客户端是否初始化
        if (client == null) {
            System.err.println("⚠️ 微信客户端未初始化，无法发送消息");
            return;
        }

        // 获取发送者ID
        String fromUser = msg.getFrom_user_id();
        if (fromUser == null || fromUser.isEmpty()) {
            System.out.println("⚠️ 发送者ID为空，忽略消息");
            return;
        }

        try {
            // 获取消息列表
            List<MessageItem> itemList = msg.getItem_list();
            if (itemList == null || itemList.isEmpty()) {
                System.out.println("⚠️ 消息内容为空");
                return;
            }

            // 遍历处理每条消息
            for (MessageItem item : itemList) {
                if (item == null) {
                    continue;
                }

                int itemType = item.getType();
                System.out.println("📩 来自 " + fromUser + "，消息类型: " + itemType);

                // 根据消息类型分别处理
                switch (itemType) {
                    case 1 : handleTextMessage(fromUser, item);
                    case 2 : handleImageMessage(fromUser, item);
                    case 3 : handleVoiceMessage(fromUser, item);
                    case 4 : handleVideoMessage(fromUser);
                    case 5 : handleFileMessage(fromUser);
                    default : handleUnknownMessage(fromUser, itemType);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 处理消息异常: " + e.getMessage());
            e.printStackTrace();
            // 尝试发送错误消息给用户
            try {
                client.sendText(fromUser, "❌ 处理消息时出错，请稍后重试");
            } catch (Exception ex) {
                // 忽略发送失败
            }
        }
    }

    /**
     * 处理文本消息
     */
    private void handleTextMessage(String fromUser, MessageItem item) throws IOException {
        TextItem textItem = item.getText_item();
        if (textItem == null) {
            System.out.println("⚠️ 文本消息内容为空");
            return;
        }

        String content = textItem.getText();
        if (content == null || content.isEmpty()) {
            System.out.println("⚠️ 文本内容为空");
            return;
        }

        System.out.println("📝 文字内容: " + content);

        // 处理命令并回复
        String reply = processCommand(content);
        client.sendText(fromUser, reply);
    }

    /**
     * 处理图片消息
     */
    private void handleImageMessage(String fromUser, MessageItem item) throws IOException {
        ImageItem imageItem = item.getImage_item();
        if (imageItem != null) {
            System.out.println("🖼️ 收到图片，图片ID: " );
        } else {
            System.out.println("🖼️ 收到图片");
        }
        client.sendText(fromUser, "✅ 收到图片，暂不支持图片识别");
    }

    /**
     * 处理语音消息
     */
    private void handleVoiceMessage(String fromUser, MessageItem item) throws IOException {
        VoiceItem voiceItem = item.getVoice_item();
        if (voiceItem != null) {
            String voiceText = voiceItem.getText();
            System.out.println("🗣️ 语音转文字: " + voiceText);

            if (voiceText != null && !voiceText.isEmpty()) {
                // 语音转文字后也尝试解析命令
                String reply = processCommand(voiceText);
                client.sendText(fromUser, reply);
            } else {
                client.sendText(fromUser, "🔊 收到语音，但未能识别出文字");
            }
        } else {
            client.sendText(fromUser, "🔊 收到语音");
        }
    }

    /**
     * 处理视频消息
     */
    private void handleVideoMessage(String fromUser) throws IOException {
        System.out.println("🎬 收到视频");
        client.sendText(fromUser, "✅ 收到视频，暂不支持视频播放");
    }

    /**
     * 处理文件消息
     */
    private void handleFileMessage(String fromUser) throws IOException {
        System.out.println("📎 收到文件");
        client.sendText(fromUser, "✅ 收到文件");
    }

    /**
     * 处理未知类型消息
     */
    private void handleUnknownMessage(String fromUser, int itemType) throws IOException {
        System.out.println("⚠️ 未处理类型: " + itemType);
        client.sendText(fromUser, "🤔 暂不支持 [" + itemType + "] 类型消息");
    }

    /**
     * 处理命令，返回回复内容
     */
    private String processCommand(String content) {
        if (content == null || content.isEmpty()) {
            return "👋 你好！\n💡 发送「天气 城市名」查询天气，例如：天气 杭州";
        }

        // 去除首尾空格
        String trimmed = content.trim();

        // ========== 天气查询命令 ==========
        if (trimmed.startsWith("天气")) {
            // 提取城市名
            String city = trimmed.replaceFirst("^天气\\s*", "").trim();
            if (city.isEmpty()) {
                return "🌤️ 请指定城市名\n📝 格式：天气 城市名\n例如：天气 杭州";
            }
            System.out.println("🔍 查询天气：城市=" + city);
            return weatherService.queryWeather(city);
        }

        // ========== 帮助命令 ==========
        if (trimmed.equals("帮助") || trimmed.equals("help") ||
                trimmed.equals("？") || trimmed.equals("?")) {
            return "📖 可用命令：\n" +
                    "─────────────────\n" +
                    "• 天气 城市名 - 查询天气\n" +
                    "  例如：天气 杭州\n" +
                    "─────────────────\n" +
                    "• 帮助 / help - 显示此帮助\n" +
                    "─────────────────\n" +
                    "💡 直接发送文字我会原样回复";
        }

        // ========== 版本命令 ==========
        if (trimmed.equals("版本") || trimmed.equals("version")) {
            return "🤖 微信机器人 v2.0\n" +
                    "🌤️ 基于心知天气API\n" +
                    "📅 2026年7月16日";
        }

        // ========== 普通文本回复 ==========
        return "👋 我收到了你的文字：\n「" + content + "」\n\n" +
                "💡 发送「天气 城市名」查询天气\n" +
                "例如：天气 杭州";
    }
}