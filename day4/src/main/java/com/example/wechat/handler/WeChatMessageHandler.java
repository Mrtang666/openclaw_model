package com.example.wechat.handler;

import com.example.wechat.agent.AgentRouter;
import com.example.wechat.agent.AgentRouter.RouteResult;
import com.example.wechat.memory.ConversationMemory;
import com.example.wechat.service.DeepSeekService;
import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeChatMessageHandler {

    private final AgentRouter agentRouter;
    private final ConversationMemory memory;
    private final DeepSeekService deepSeekService;
    private final Tool ttsTool;

    public WeChatMessageHandler(AgentRouter agentRouter,
                                ConversationMemory memory,
                                DeepSeekService deepSeekService,
                                Tool ttsTool) {
        this.agentRouter = agentRouter;
        this.memory = memory;
        this.deepSeekService = deepSeekService;
        this.ttsTool = ttsTool;
    }

    public String handleMessage(String userId, String message, byte[] imageData) {
        log.info("========== 处理消息 ==========");
        log.info("用户: {}, 消息: {}, 图片: {}", userId, message, imageData != null ? "有" : "无");

        // 1. 添加用户消息到记忆
        memory.addUserMessage(userId, message != null ? message : "【图片消息】");

        // 2. 如果有图片，优先使用图生文工具
        if (imageData != null && imageData.length > 0) {
            log.info("检测到图片，调用图生文...");
            String imageResult = processImage(userId, imageData, message);
            if (imageResult != null && !imageResult.isEmpty()) {
                memory.addAssistantMessage(userId, imageResult);
                return imageResult;
            }
        }

        // =============================================
        // 3. 检测退出语音模式（最优先）
        // =============================================
        if (message != null && shouldExitVoiceMode(message)) {
            memory.setVoiceMode(userId, false);
            memory.setWaitingForVoiceSelection(userId, false, null);
            log.info("🔇 用户退出语音模式: {}", message);
            String response = "✅ 已退出语音回复模式，切换到普通文字回复。\n有任何问题随时找我～";
            memory.addAssistantMessage(userId, response);
            return response;
        }

        // =============================================
        // 4. 检测音色切换请求（用户主动说"换音色"等）
        // =============================================
        if (message != null && isVoiceSwitchRequest(message)) {
            String voiceSwitchResult = handleVoiceSwitch(userId, message);
            if (voiceSwitchResult != null) {
                memory.addAssistantMessage(userId, voiceSwitchResult);
                return voiceSwitchResult;
            }
        }

        // =============================================
        // 5. 检查是否正在等待选择音色（音色切换流程中）
        // =============================================
        if (memory.isWaitingForVoiceSelection(userId)) {
            log.info("⏳ 用户正在等待选择音色: {}", message);
            String voiceResult = handleVoiceSelection(userId, message);
            if (voiceResult != null) {
                memory.addAssistantMessage(userId, voiceResult);
                return voiceResult;
            }
        }

        // =============================================
        // 6. 检查是否触发语音模式（触发词）
        //    用户有默认音色，直接进入语音模式
        // =============================================
        boolean isTtsTrigger = message != null && isTtsTrigger(message);

        if (isTtsTrigger) {
            memory.setVoiceMode(userId, true);
            memory.refreshVoiceModeActiveTime(userId);
            String currentVoice = memory.getUserVoice(userId);
            log.info("🔊 用户触发语音模式，已开启，音色: {}", currentVoice);
        }

        // =============================================
        // 7. 判断是否需要语音回复
        // =============================================
        boolean shouldUseVoice = false;

        if (memory.isVoiceMode(userId)) {
            shouldUseVoice = true;
            memory.refreshVoiceModeActiveTime(userId);
            log.info("🔊 用户处于语音模式，自动使用语音回复");
        }

        // =============================================
        // 8. 语音模式处理
        // =============================================
        if (shouldUseVoice) {
            log.info("🔊 进入语音回复流程...");

            String userQuestion = message;
            if (isTtsTrigger) {
                userQuestion = extractQuestionFromMessage(message);
            }
            if (userQuestion == null || userQuestion.isEmpty()) {
                userQuestion = "请说点什么吧";
            }

            String aiResponse = deepSeekService.chat(userId, userQuestion);
            log.info("AI生成的回复: {}", aiResponse);

            try {
                String userVoice = memory.getUserVoice(userId);

                Map<String, Object> ttsParams = Map.of(
                        "text", aiResponse,
                        "voice", userVoice,
                        "userId", userId
                );
                String audioResponse = ttsTool.execute(ttsParams);

                long remain = memory.getVoiceModeRemainingSeconds(userId);
                String timeReminder = "";
                if (remain > 0 && remain < 120) {
                    timeReminder = "\n\n⏰ 语音模式剩余 " + remain + " 秒，发送「取消语音」可关闭。";
                }

                if (audioResponse != null && audioResponse.startsWith("AUDIO:")) {
                    String combinedResult = "📝 " + aiResponse + "\n\n🎵 语音已生成，请点击播放\n\n" + audioResponse + timeReminder;
                    memory.addAssistantMessage(userId, aiResponse);
                    return combinedResult;
                } else {
                    memory.addAssistantMessage(userId, aiResponse);
                    return "⚠️ 语音合成暂时不可用，以下是文字回复：\n\n📝 " + aiResponse + timeReminder;
                }

            } catch (Exception e) {
                log.error("TTS合成异常", e);
                memory.addAssistantMessage(userId, aiResponse);
                return "❌ 语音合成失败，以下是文字回复：\n\n📝 " + aiResponse;
            }
        }


        // ========== 10. 普通路由 ==========
        RouteResult routeResult = agentRouter.route(userId, message);
        log.info("路由结果: toolName={}, isChat={}", routeResult.getToolName(), routeResult.isChat());

        String response;
        if (routeResult.isChat()) {
            log.info("走聊天模式...");
            response = deepSeekService.chat(userId, message);
        } else {
            Tool tool = routeResult.getTool();
            Map<String, Object> params = routeResult.getParams();
            log.info("执行工具: {}, 参数: {}", tool.getName(), params);
            try {
                response = tool.execute(params);
            } catch (Exception e) {
                log.error("工具执行失败", e);
                response = "执行失败: " + e.getMessage();
            }
        }

        if (response != null && !response.isEmpty()) {
            memory.addAssistantMessage(userId, response);
        }

        return response;
    }

    // =============================================
    // 检测是否为音色切换请求
    // =============================================

    private boolean isVoiceSwitchRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();

        return lower.contains("切换音色")
                || lower.contains("换音色")
                || (lower.contains("我想要") && lower.contains("音色"))
                || lower.matches(".*(温柔|阳光|可爱|御姐|沉稳|磁性|甜美|治愈|二次元|英语|搞怪|男声|女声).*音色.*")
                || lower.matches(".*音色.*(温柔|阳光|可爱|御姐|沉稳|磁性|甜美|治愈|二次元|英语|搞怪|男声|女声).*");
    }

    // =============================================
    // 音色选择处理（在用户主动切换音色时使用）
    // =============================================

    private String handleVoiceSelection(String userId, String message) {
        if (message == null) return null;

        String keyword = message.trim();

        List<ConversationMemory.VoiceInfo> matched = memory.findVoicesByTags(keyword);

        if (matched.isEmpty()) {
            String voiceName = fuzzyMatchVoiceName(keyword);
            if (voiceName != null && memory.isValidVoice(voiceName)) {
                memory.setUserVoice(userId, voiceName);

                String displayName = memory.getVoiceDisplayName(voiceName);
                String description = memory.getVoiceDescription(voiceName);
                String pendingQuestion = memory.getPendingVoiceQuestion(userId);

                memory.setWaitingForVoiceSelection(userId, false, null);

                if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                    log.info("✅ 音色选择完成，执行原始问题: {}", pendingQuestion);
                    return executeVoiceReply(userId, pendingQuestion, voiceName, "🎤 已切换音色为: **" + displayName + "**\n\n");
                }

                return "🎤 已切换音色为: **" + displayName + "**\n" +
                        "   " + description + "\n\n" +
                        "后续语音回复将使用这个音色 🎵";
            }

            return "🎤 没找到匹配「" + keyword + "」的音色。\n\n" +
                    "请选择以下类别之一：\n" +
                    "• 温柔女声\n" +
                    "• 阳光男声\n" +
                    "• 可爱/二次元\n" +
                    "• 御姐音\n" +
                    "• 英语发音\n" +
                    "• 搞怪有趣\n" +
                    "• 沉稳老者\n\n" +
                    "💡 或者直接输入音色名称，例如：芊悦、十三";
        }

        if (matched.size() == 1) {
            ConversationMemory.VoiceInfo voice = matched.get(0);
            memory.setUserVoice(userId, voice.name);

            String pendingQuestion = memory.getPendingVoiceQuestion(userId);

            memory.setWaitingForVoiceSelection(userId, false, null);

            if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                log.info("✅ 音色选择完成，执行原始问题: {}", pendingQuestion);
                return executeVoiceReply(userId, pendingQuestion, voice.name, "🎤 已切换音色为: **" + voice.displayName + "**\n\n");
            }

            return "🎤 已切换音色为: **" + voice.displayName + "**\n" +
                    "   " + voice.description + "\n\n" +
                    "后续语音回复将使用这个音色 🎵";
        } else {
            StringBuilder sb = new StringBuilder("🎤 找到多个匹配「" + keyword + "」的音色：\n\n");
            for (int i = 0; i < Math.min(5, matched.size()); i++) {
                ConversationMemory.VoiceInfo voice = matched.get(i);
                sb.append("• **").append(voice.displayName).append("** - ")
                        .append(voice.description).append("\n");
            }
            sb.append("\n💡 直接回复具体名称精确选择");
            return sb.toString();
        }
    }

    // =============================================
    // 音色切换处理方法（用户主动触发）
    // =============================================

    private String handleVoiceSwitch(String userId, String message) {
        if (message == null) return null;

        String keyword = extractVoiceKeyword(message);
        if (keyword == null || keyword.isEmpty()) {
            return "🎤 请告诉我你想要什么样的声音，比如：\n" +
                    "• 切换音色 温柔女声\n" +
                    "• 我想要阳光男声\n" +
                    "• 换一个可爱的声音\n\n" +
                    "💡 输入「查看音色」查看所有分类";
        }

        List<ConversationMemory.VoiceInfo> matched = memory.findVoicesByTags(keyword);

        if (matched.isEmpty()) {
            String voiceName = fuzzyMatchVoiceName(keyword);
            if (voiceName != null && memory.isValidVoice(voiceName)) {
                memory.setUserVoice(userId, voiceName);
                String displayName = memory.getVoiceDisplayName(voiceName);
                String description = memory.getVoiceDescription(voiceName);

                if (memory.isVoiceMode(userId)) {
                    return "🎤 已切换音色为: **" + displayName + "**\n" +
                            "   " + description;
                } else {
                    return "🎤 已切换音色为: **" + displayName + "**\n" +
                            "   " + description + "\n\n" +
                            "💡 发送「语音回复 + 内容」开始使用语音模式";
                }
            }

            return "🎤 没找到匹配「" + keyword + "」的音色。\n\n" +
                    "💡 试试这些关键词：\n" +
                    "• 温柔女声 / 阳光男声 / 可爱 / 御姐\n" +
                    "• 英语 / 搞怪 / 沉稳 / 磁性\n\n" +
                    "输入「查看音色」查看所有分类";
        }

        if (matched.size() == 1) {
            ConversationMemory.VoiceInfo voice = matched.get(0);
            memory.setUserVoice(userId, voice.name);
            if (memory.isVoiceMode(userId)) {
                return "🎤 已切换音色为: **" + voice.displayName + "**\n" +
                        "   " + voice.description;
            } else {
                return "🎤 已切换音色为: **" + voice.displayName + "**\n" +
                        "   " + voice.description + "\n\n" +
                        "💡 发送「语音回复 + 内容」开始使用语音模式";
            }
        } else {
            // 多个匹配，进入选择流程
            memory.setWaitingForVoiceSelection(userId, true, null);
            StringBuilder sb = new StringBuilder("🎤 找到多个匹配「" + keyword + "」的音色：\n\n");
            for (int i = 0; i < Math.min(5, matched.size()); i++) {
                ConversationMemory.VoiceInfo voice = matched.get(i);
                sb.append("• **").append(voice.displayName).append("** - ")
                        .append(voice.description).append("\n");
            }
            sb.append("\n💡 直接回复具体名称精确选择");
            return sb.toString();
        }
    }

    /**
     * 执行语音回复（工具方法）
     */
    private String executeVoiceReply(String userId, String question, String voiceName, String prefix) {
        try {
            String aiResponse = deepSeekService.chat(userId, question);
            log.info("AI生成的回复: {}", aiResponse);

            Map<String, Object> ttsParams = Map.of(
                    "text", aiResponse,
                    "voice", voiceName,
                    "userId", userId
            );
            String audioResponse = ttsTool.execute(ttsParams);

            memory.addAssistantMessage(userId, aiResponse);

            if (audioResponse != null && audioResponse.startsWith("AUDIO:")) {
                return prefix + "📝 " + aiResponse + "\n\n🎵 语音已生成，请点击播放\n\n" + audioResponse;
            } else {
                return prefix + "⚠️ 语音合成暂时不可用，以下是文字回复：\n\n📝 " + aiResponse;
            }

        } catch (Exception e) {
            log.error("语音回复执行失败", e);
            return "❌ 语音回复失败: " + e.getMessage();
        }
    }

    // =============================================
    // 辅助方法
    // =============================================

    private String extractVoiceKeyword(String message) {
        String cleaned = message.replaceAll("(?i)(切换音色|换音色|我想要|换一个|换个|换成|用|音色|的|声音)", "");
        cleaned = cleaned.replaceAll("[，,。.、：:！!？?\\s]+", " ").trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        String[] parts = cleaned.split("\\s+");
        for (String part : parts) {
            if (part.length() >= 2) {
                return part;
            }
        }
        return cleaned;
    }

    private String fuzzyMatchVoiceName(String keyword) {
        String lower = keyword.toLowerCase();
        for (ConversationMemory.VoiceInfo voice : ConversationMemory.VOICE_LIBRARY) {
            if (voice.displayName.contains(lower) ||
                    voice.name.toLowerCase().contains(lower)) {
                return voice.name;
            }
        }
        return null;
    }

    private boolean shouldExitVoiceMode(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();

        String[] exitKeywords = {
                "退出语音", "取消语音", "停止语音",
                "不用语音", "不要语音", "别语音",
                "换文字", "文字回复", "不用回复语音",
                "语音关闭", "关闭语音", "语音停止",
                "不用了", "算了", "不需要了",
                "就这样吧", "可以了", "好了"
        };
        for (String keyword : exitKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        if ((lower.contains("不用") || lower.contains("不要") || lower.contains("别")) && lower.contains("语音")) {
            return true;
        }
        return false;
    }

    private boolean isTtsTrigger(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("语音回复")
                || lower.contains("语音形式")
                || lower.contains("用语音")
                || lower.contains("说给我听")
                || lower.contains("读给我听");
    }

    private String extractQuestionFromMessage(String message) {
        if (message == null) return null;
        String result = message.replaceAll("(?i)(语音回复|语音形式|用语音|说给我听|读给我听|语音播报|语音输出)", "");
        result = result.replaceAll("^[：:：\\s]+", "").trim();
        return result.isEmpty() ? null : result;
    }

    private String processImage(String userId, byte[] imageData, String userMessage) {
        try {
            Tool imageTool = agentRouter.getTool("image_to_text");
            if (imageTool == null) {
                return "图生文功能暂不可用";
            }

            String base64Image = Base64.getEncoder().encodeToString(imageData);

            String prompt = "请详细描述这张图片的内容";
            if (userMessage != null && !userMessage.isEmpty() && !"【图片消息】".equals(userMessage)) {
                prompt = userMessage;
            }

            Map<String, Object> params = Map.of(
                    "image_content", base64Image,
                    "prompt", prompt
            );

            return imageTool.execute(params);

        } catch (Exception e) {
            log.error("图生文处理失败", e);
            return "图片理解失败: " + e.getMessage();
        }
    }
}