package com.youkeda.exercise.shared;


import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.*;
import com.youkeda.exercise.shared.deepseek.DeepSeekService;
import com.youkeda.exercise.shared.picture.ImageGenerationService;
import com.youkeda.exercise.shared.picture.QwenVisionService;
import com.youkeda.exercise.shared.weather.WeatherService;

import java.io.IOException;
import java.util.List;

/**
 * 微信消息处理器 - 处理所有收到的消息
 */
public class MessageHandler {

    private final WeatherService weatherService;
    private final DeepSeekService deepSeekService;
    private final QwenVisionService qwenVisionService;
    private final ImageGenerationService imageGenerationService;
    private ILinkClient client;

    // 是否启用AI模式
    private boolean aiModeEnabled = true;

    public MessageHandler(WeatherService weatherService, DeepSeekService deepSeekService, QwenVisionService qwenVisionService, ImageGenerationService imageGenerationService) {
        this.weatherService = weatherService;
        this.deepSeekService = deepSeekService;
        this.qwenVisionService = qwenVisionService;
        this.imageGenerationService = imageGenerationService;
    }

    /**
     * 设置微信客户端（在登录成功后调用）
     */
    public void setClient(ILinkClient client) {
        this.client = client;
    }

    /**
     * 切换AI模式
     */
    public void setAiModeEnabled(boolean enabled) {
        this.aiModeEnabled = enabled;
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
                    case 1 : handleTextMessage(fromUser, item);break;
                    case 2 : handleImageMessage(fromUser, item);break;
                    case 3 : handleVoiceMessage(fromUser, item);break;
                    case 4 : handleVideoMessage(fromUser);break;
                    case 5 : handleFileMessage(fromUser);break;
                    default : handleUnknownMessage(fromUser, itemType);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 处理消息异常: " + e.getMessage());
            e.printStackTrace();
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


        String trimmed = content.trim();
        // 2. 画图
        if (trimmed.startsWith("画图") || trimmed.endsWith("图片")) {
            String prompt = trimmed.replaceFirst("^(画图|图片)\\s*", "").trim();
            if (prompt.isEmpty()) {
                client.sendText(fromUser,"🎨 请描述你想画的图片，例如：画图 一只可爱的橘猫");
            }
            // 异步生成，避免阻塞
            new Thread(() -> {
                try {
                    client.sendText(fromUser, "🎨 正在生成图片，请稍候...（可能需要10-20秒）");
                    byte[] imageData = imageGenerationService.generateImage(prompt);
                    // 发送图片（使用你发现的重载方法）
                    client.sendImage(fromUser, imageData, "generated.jpg", prompt);
                } catch (Exception e) {
                    try {
                        client.sendText(fromUser, "❌ 生成图片失败：" + e.getMessage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();
        }
        else {
            // 处理命令并回复
            String reply = processCommand(content,fromUser);
            client.sendText(fromUser, reply);
        }
    }

    /**
     * 处理图片消息
     */
    private void handleImageMessage(String fromUser, MessageItem item) throws IOException {
        ImageItem imageItem = item.getImage_item();
        if (imageItem == null) {
            client.sendText(fromUser, "⚠️ 未能获取图片信息");
            return;
        }

        // 1. 获取 media_id
        CDNMedia mediaId = imageItem.getMedia();  // 注意：具体方法名可能是 getMedia_id() 或 getMediaId()
        if (mediaId == null ) {
            // 万一 mediaId 也为空，打印完整对象看看还有啥
            System.out.println("❌ mediaId 也为空，完整对象：" + imageItem);
            client.sendText(fromUser, "⚠️ 无法获取图片ID，请重试");
            return;
        }

        System.out.println("🖼️ 收到图片，media_id: " + mediaId);

        // 2. 通过客户端下载图片数据（这里需要你确认实际的方法名）
        byte[] imageData = null;
        try {
            // 尝试调用 SDK 的下载方法（请根据 IDE 提示替换为实际方法名）
            imageData = client.downloadMedia(mediaId);
            // 如果返回的是 InputStream，就读取成 byte[]
            // 如果返回的是 File，就 FileInputStream 读取
        } catch (Exception e) {
            System.err.println("❌ 下载图片失败：" + e.getMessage());
            client.sendText(fromUser, "❌ 下载图片失败，请稍后重试");
            return;
        }

        if (imageData == null || imageData.length == 0) {
            client.sendText(fromUser, "❌ 下载的图片数据为空");
            return;
        }

        System.out.println("✅ 图片下载成功，大小：" + imageData.length + " 字节");

        // 3. 调用视觉模型分析（传入字节数组）
        String description = qwenVisionService.analyzeImage(imageData);

        //String finalDescription = deepSeekService.chat(description);
        String finalDescription = deepSeekService.chat( description);

        // 4. 回复用户
        String reply = "📸 我看了一下这张图片：\n" + finalDescription;
        client.sendText(fromUser, reply);
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
                String reply = processCommand(voiceText,fromUser);
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
    private String processCommand(String content, String fromUser) {
        if (content == null || content.isEmpty()) {
            return "👋 你好！\n💡 发送「天气 城市名」查询天气\n💡 直接发送任何文字，我会用AI智能回复你！";
        }

        // 去除首尾空格
        String trimmed = content.trim();

        // ========== AI模式开关命令 ==========
        if (trimmed.equals("开启AI")) {
            aiModeEnabled = true;
            return "🤖 AI模式已开启！\n💡 现在我会用AI智能回复你的所有问题";
        }
        if (trimmed.equals("关闭AI")) {
            aiModeEnabled = false;
            return "🤖 AI模式已关闭\n💡 现在只处理天气查询等基础命令";
        }

        // ========== 天气查询命令 ==========
        if (trimmed.startsWith("天气")) {
            String city = trimmed.replaceFirst("^天气\\s*", "").trim();
            if (city.isEmpty()) {
                return "🌤️ 请指定城市名\n📝 格式：天气 城市名\n例如：天气 杭州";
            }
            System.out.println("🔍 查询天气：城市=" + city);
            return weatherService.queryWeather(city);
        }


        // 2. 画图
        if (trimmed.startsWith("画图") || trimmed.startsWith("生成图片")) {
            String prompt = trimmed.replaceFirst("^(画图|生成图片)\\s*", "").trim();
            if (prompt.isEmpty()) {
                return "🎨 请描述你想画的图片，例如：画图 一只可爱的橘猫";
            }
            // 异步生成，避免阻塞
            new Thread(() -> {
                try {
                    client.sendText(fromUser, "🎨 正在生成图片，请稍候...（可能需要10-20秒）");
                    byte[] imageData = imageGenerationService.generateImage(prompt);
                    // 发送图片（使用你发现的重载方法）
                    client.sendImage(fromUser, imageData, "generated.jpg", prompt);
                } catch (Exception e) {
                    try {
                        client.sendText(fromUser, "❌ 生成图片失败：" + e.getMessage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();
            return null;  // 不立即回复，异步发送
        }

        // ========== 帮助命令 ==========
        if (trimmed.equals("帮助") || trimmed.equals("help") ||
                trimmed.equals("？") || trimmed.equals("?")) {
            return getHelpMessage();
        }

        // ========== 版本命令 ==========
        if (trimmed.equals("版本") || trimmed.equals("version")) {
            return getVersionMessage();
        }

        // ========== 状态命令 ==========
        if (trimmed.equals("状态") || trimmed.equals("status")) {
            return getStatusMessage();
        }

        // ========== 使用AI回复 ==========
        if (aiModeEnabled && deepSeekService.isAvailable()) {
            try {
                System.out.println("🤖 正在调用DeepSeek AI...");
                String aiReply = deepSeekService.chat(trimmed);
                System.out.println("🤖 AI回复: " + aiReply.substring(0, Math.min(aiReply.length(), 100)) + "...");
                return aiReply;
            } catch (Exception e) {
                System.err.println("❌ AI调用失败: " + e.getMessage());
                return "❌ AI服务暂时不可用，请稍后重试\n💡 你可以继续使用「天气 城市名」查询天气";
            }
        } else if (aiModeEnabled && !deepSeekService.isAvailable()) {
            return "⚠️ AI模式已开启，但DeepSeek API密钥未配置\n" +
                    "📝 请配置 deepseek.api.key 后重启程序\n" +
                    "💡 你可以继续使用「天气 城市名」查询天气";
        }

        // ========== 普通文本回复（AI关闭时） ==========
        return "👋 我收到了你的文字：\n「" + content + "」\n\n" +
                "💡 发送「天气 城市名」查询天气\n" +
                "💡 发送「开启AI」启用智能对话\n" +
                "💡 发送「帮助」查看所有命令";
    }

    /**
     * 获取帮助信息
     */
    private String getHelpMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 可用命令：\n");
        sb.append("─────────────────\n");
        sb.append("• 天气 城市名 - 查询天气\n");
        sb.append("  例如：天气 杭州\n");
        sb.append("─────────────────\n");
        if (deepSeekService.isAvailable()) {
            sb.append("• 任意文字 - AI智能回复\n");
            sb.append("  例如：今天适合出去玩吗？\n");
            sb.append("─────────────────\n");
            sb.append("• 开启AI - 启用AI模式\n");
            sb.append("• 关闭AI - 关闭AI模式\n");
            sb.append("─────────────────\n");
        }
        sb.append("• 帮助/help - 显示此帮助\n");
        sb.append("• 版本 - 显示版本信息\n");
        sb.append("• 状态 - 查看系统状态\n");
        sb.append("─────────────────\n");
        sb.append("💡 AI模式" + (aiModeEnabled ? "✅ 已开启" : "❌ 已关闭") + "\n");
        if (deepSeekService.isAvailable()) {
            sb.append("🤖 DeepSeek API ✅ 已连接\n");
        } else {
            sb.append("⚠️ DeepSeek API ❌ 未配置\n");
        }
        return sb.toString();
    }

    /**
     * 获取版本信息
     */
    private String getVersionMessage() {
        return "🤖 微信机器人 v3.0（AI版）\n" +
                "🌤️ 基于心知天气API\n" +
                "🧠 基于DeepSeek大模型\n" +
                "📅 2026年7月17日";
    }

    /**
     * 获取状态信息
     */
    private String getStatusMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 系统状态\n");
        sb.append("─────────────────\n");
        sb.append("🤖 AI模式: " + (aiModeEnabled ? "✅ 已开启" : "❌ 已关闭") + "\n");
        sb.append("🧠 DeepSeek API: " + (deepSeekService.isAvailable() ? "✅ 已连接" : "❌ 未配置") + "\n");
        sb.append("🌤️ 天气服务: ✅ 可用\n");
        sb.append("📱 微信客户端: " + (client != null ? "✅ 已连接" : "❌ 未连接") + "\n");
        sb.append("─────────────────\n");
        sb.append("💡 发送「帮助」查看所有命令");
        return sb.toString();
    }

}
