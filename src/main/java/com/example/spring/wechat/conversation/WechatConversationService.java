package com.example.spring.wechat.conversation;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.chat.ChatService;
import com.example.spring.chat.ChatServiceException;
import com.example.spring.image.generation.ImageGenerationRequest;
import com.example.spring.image.generation.ImageGenerationIntentParser;
import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.image.generation.ImageGenerationService;
import com.example.spring.exception.WeatherServiceException;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.client.WechatIncomingMessage;
import com.example.spring.wechat.client.WechatIncomingVoice;
import com.example.spring.wechat.image.ImageUnderstandingException;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import com.example.spring.wechat.image.service.ImageInputResolver;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
import com.example.spring.wechat.voice.VoiceRecognitionException;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;
import com.example.spring.wechat.voice.service.VoiceRecognitionService;
import com.example.spring.weather.WeatherResult;
import com.example.spring.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WechatConversationService {

    private static final Logger log = LoggerFactory.getLogger(WechatConversationService.class);
    private static final int MAX_HISTORY_TURNS = 6;
    private static final String DEFAULT_SESSION_KEY = "default";

    private final ChatService chatService;
    private final WeatherService weatherService;
    private final ImageUnderstandingService imageUnderstandingService;
    private final ImageGenerationService imageGenerationService;
    private final VoiceRecognitionService voiceRecognitionService;
    private final ImageInputResolver imageInputResolver;
    private final WeatherIntentParser weatherIntentParser;
    private final ImageGenerationIntentParser imageGenerationIntentParser;
    private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();

    @Autowired
    public WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            ImageInputResolver imageInputResolver,
            WeatherIntentParser weatherIntentParser,
            ImageGenerationIntentParser imageGenerationIntentParser) {
        this.chatService = chatService;
        this.weatherService = weatherService;
        this.imageUnderstandingService = imageUnderstandingService;
        this.imageGenerationService = imageGenerationService;
        this.voiceRecognitionService = voiceRecognitionService;
        this.imageInputResolver = imageInputResolver;
        this.weatherIntentParser = weatherIntentParser;
        this.imageGenerationIntentParser = imageGenerationIntentParser;
    }

    WechatConversationService(ChatService chatService, WeatherService weatherService) {
        this(chatService, weatherService, null, null, null, new ImageInputResolver(), new WeatherIntentParser(), new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, null, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, voiceRecognitionService, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    public String handle(String input) {
        return handle(null, input);
    }

    public String handle(String userId, String input) {
        StringBuilder output = new StringBuilder();
        handleStreaming(userId, input, output::append);
        return output.toString().strip();
    }

    public String handle(WechatIncomingMessage message) {
        WechatReply reply = handleWechat(message);
        return reply == null || reply.text() == null ? "" : reply.text().strip();
    }

    public WechatReply handleWechat(WechatIncomingMessage message) {
        if (message == null) {
            return WechatReply.text("");
        }

        String sessionKey = sessionKey(message.fromUserId());
        if (message.hasVoices()) {
            return handleVoiceMessage(sessionKey, message);
        }

        ImageAnalysisRequest request = imageInputResolver.resolve(message);
        String text = request.userText();
        boolean hasImages = request.hasImages();

        if (text == null || text.isBlank()) {
            if (hasImages) {
                StringBuilder output = new StringBuilder();
                handleStreaming(message, output::append);
                return WechatReply.text(output.toString().strip());
            }
            return WechatReply.text("");
        }

        if (!hasImages) {
            List<String> tasks = splitUserTasks(text);
            if (tasks.size() > 1) {
                return handleTextTasks(sessionKey, tasks);
            }
        }

        return handleSingleTextTask(sessionKey, text);
    }

    public void handleStreaming(String input, ReplyEmitter emitter) {
        handleStreaming(null, input, emitter);
    }

    public void handleStreaming(String userId, String input, ReplyEmitter emitter) {
        if (input == null || input.isBlank()) {
            return;
        }

        if (emitter == null) {
            throw new ChatServiceException("缺少流式输出处理器");
        }

        String text = input.strip();
        String sessionKey = sessionKey(userId);
        log.info("微信会话收到文本消息，userId={}, text={}", sessionKey, preview(text));

        Optional<String> weatherCity = weatherIntentParser.extractCity(text);
        if (weatherCity.isPresent()) {
            log.info("微信会话识别到天气意图，userId={}, city={}", sessionKey, weatherCity.get());
            handleWeatherQuestion(sessionKey, text, weatherCity.get(), emitter);
            return;
        }

        handleNormalConversation(sessionKey, text, emitter);
    }

    public void handleStreaming(WechatIncomingMessage message, ReplyEmitter emitter) {
        if (message == null) {
            return;
        }

        if (emitter == null) {
            throw new ChatServiceException("缺少流式输出处理器");
        }

        String sessionKey = sessionKey(message.fromUserId());
        if (message.hasVoices()) {
            WechatReply reply = handleVoiceMessage(sessionKey, message);
            emit(emitter, reply.text());
            return;
        }

        ImageAnalysisRequest request = imageInputResolver.resolve(message);
        String text = request.userText();
        boolean hasImages = request.hasImages();

        log.info(
                "微信会话收到消息，userId={}, messageId={}, text={}, imageCount={}",
                sessionKey,
                valueOrUnknown(message.messageId()),
                preview(text),
                request.images().size());

        if (hasImages) {
            handleImageConversation(sessionKey, message, request, emitter);
            return;
        }

        if (text == null || text.isBlank()) {
            return;
        }

        handleStreaming(sessionKey, text, emitter);
    }

    private void handleNormalConversation(String sessionKey, String originalText, ReplyEmitter emitter) {
        String prompt = buildConversationPrompt(sessionKey, originalText);
        log.debug("微信普通对话上下文轮数={}, userId={}", memoryFor(sessionKey).snapshot().size(), sessionKey);
        streamReplyAndRemember(sessionKey, originalText, prompt, emitter);
    }

    private WechatReply handleTextTasks(String sessionKey, List<String> tasks) {
        List<WechatReply.Part> parts = new ArrayList<>();
        for (String task : tasks) {
            if (task == null || task.isBlank()) {
                continue;
            }

            WechatReply reply = handleSingleTextTask(sessionKey, task.strip());
            parts.addAll(toReplyParts(reply));
        }

        if (parts.isEmpty()) {
            return WechatReply.text("");
        }
        return WechatReply.ordered(parts);
    }

    private WechatReply handleSingleTextTask(String sessionKey, String text) {
        Optional<ImageToolTask> imageToolTask = resolveImageToolTask(sessionKey, text);
        if (imageToolTask.isPresent()) {
            return handleImageGenerationTask(sessionKey, text, imageToolTask.get());
        }

        Optional<String> weatherCity = weatherIntentParser.extractCity(text);
        if (weatherCity.isPresent()) {
            log.info("微信会话识别到天气意图，userId={}, city={}", sessionKey, weatherCity.get());
            StringBuilder output = new StringBuilder();
            handleWeatherQuestion(sessionKey, text, weatherCity.get(), output::append);
            return WechatReply.text(output.toString().strip());
        }

        StringBuilder output = new StringBuilder();
        handleNormalConversation(sessionKey, text, output::append);
        return WechatReply.text(output.toString().strip());
    }

    private List<WechatReply.Part> toReplyParts(WechatReply reply) {
        if (reply == null) {
            return List.of();
        }

        if (reply.parts() != null && !reply.parts().isEmpty()) {
            return reply.parts();
        }

        List<WechatReply.Part> parts = new ArrayList<>();
        if (reply.preImageTexts() != null) {
            reply.preImageTexts().stream()
                    .filter(text -> text != null && !text.isBlank())
                    .map(WechatReply.Part::text)
                    .forEach(parts::add);
        }

        if (reply.hasImage()) {
            parts.add(WechatReply.Part.image(reply.text(), reply.image()));
        } else if (reply.text() != null && !reply.text().isBlank()) {
            parts.add(WechatReply.Part.text(reply.text()));
        }
        return parts;
    }

    private List<String> splitUserTasks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tasks = new ArrayList<>();
        String[] primaryParts = text.strip().split("(?:然后|另外|还有|接着|最后|同时|并且)");
        for (String primaryPart : primaryParts) {
            if (primaryPart == null || primaryPart.isBlank()) {
                continue;
            }

            String[] secondaryParts = primaryPart.strip()
                    .split("[，。；;]\\s*(?=(?:帮我|请帮我|给我|帮忙|再帮我|查询|查一下|查一查))");
            for (String secondaryPart : secondaryParts) {
                String task = trimTaskText(secondaryPart);
                if (!task.isBlank()) {
                    tasks.add(task);
                }
            }
        }

        if (tasks.size() <= 1) {
            return List.of(text.strip());
        }
        return tasks;
    }

    private String trimTaskText(String text) {
        if (text == null) {
            return "";
        }
        return text.strip().replaceAll("^[，。；;、\\s]+|[，。；;、\\s]+$", "");
    }

    private WechatReply handleVoiceMessage(String sessionKey, WechatIncomingMessage message) {
        if (voiceRecognitionService == null) {
            return WechatReply.text("语音识别服务暂未配置");
        }

        try {
            String recognizedText = recognizeVoices(message.voices());
            if (recognizedText.isBlank()) {
                return WechatReply.text("我没有听清楚，可以再说一遍吗？");
            }

            String mergedText = mergeText(message.text(), recognizedText);
            WechatIncomingMessage syntheticMessage = new WechatIncomingMessage(
                    message.messageId(),
                    message.fromUserId(),
                    message.contextToken(),
                    mergedText,
                    message.images(),
                    List.of());
            WechatReply reply = handleWechat(syntheticMessage);
            String prefix = "我听到你说：" + recognizedText;
            String replyText = reply == null || reply.text() == null ? "" : reply.text().strip();
            String text = replyText.isBlank() ? prefix : prefix + "\n\n" + replyText;
            if (reply != null && reply.hasImage()) {
                return WechatReply.textsAndImage(reply.preImageTexts(), text, reply.image());
            }
            return WechatReply.text(text);
        } catch (VoiceRecognitionException exception) {
            log.warn("微信语音识别失败，userId={}, messageId={}, error={}",
                    sessionKey,
                    valueOrUnknown(message.messageId()),
                    rootMessage(exception));
            return WechatReply.text("语音识别失败：" + messageOrDefault(exception, "请稍后重试"));
        }
    }

    private String recognizeVoices(List<WechatIncomingVoice> voices) {
        StringBuilder recognized = new StringBuilder();
        for (WechatIncomingVoice voice : voices) {
            VoiceRecognitionResult result = voiceRecognitionService.recognize(voice);
            if (result != null && result.text() != null && !result.text().isBlank()) {
                if (recognized.length() > 0) {
                    recognized.append('\n');
                }
                recognized.append(result.text().strip());
            }
        }
        return recognized.toString().strip();
    }

    private String mergeText(String originalText, String recognizedText) {
        if (originalText == null || originalText.isBlank()) {
            return recognizedText == null ? "" : recognizedText.strip();
        }
        if (recognizedText == null || recognizedText.isBlank()) {
            return originalText.strip();
        }
        return originalText.strip() + "\n" + recognizedText.strip();
    }

    private void handleImageConversation(
            String sessionKey,
            WechatIncomingMessage originalMessage,
            ImageAnalysisRequest request,
            ReplyEmitter emitter) {
        if (imageUnderstandingService == null) {
            emit(emitter, "图片识别服务暂未配置");
            return;
        }

        try {
            StringBuilder reply = new StringBuilder();
            imageUnderstandingService.streamReply(originalMessage, chunk -> {
                if (chunk != null) {
                    reply.append(chunk);
                    emitter.emit(chunk);
                }
            });

            String assistantReply = reply.toString().strip();
            if (assistantReply.isBlank()) {
                throw new ImageUnderstandingException("图片识别未返回有效内容");
            }

            String userText = request.userText();
            memoryFor(sessionKey).recordUserImage(
                    userText == null || userText.isBlank() ? "[图片]" : userText,
                    assistantReply);
        } catch (ImageUnderstandingException exception) {
            String message = "图片识别失败：" + messageOrDefault(exception, "请稍后重试");
            log.warn("微信图片识别失败，userId={}, messageId={}, error={}",
                    sessionKey,
                    valueOrUnknown(originalMessage.messageId()),
                    rootMessage(exception));
            emit(emitter, message);
        }
    }

    private void handleWeatherQuestion(String sessionKey, String originalText, String city, ReplyEmitter emitter) {
        try {
            WeatherResult weather = weatherService.query(city);
            String prompt = buildWeatherPrompt(sessionKey, originalText, weather);
            log.debug(
                    "微信天气对话上下文轮数={}, userId={}, city={}",
                    memoryFor(sessionKey).snapshot().size(),
                    sessionKey,
                    city);
            streamReplyAndRemember(sessionKey, originalText, prompt, emitter);
        } catch (WeatherServiceException exception) {
            String prompt = buildWeatherFailurePrompt(sessionKey, originalText, city, exception);
            log.warn(
                    "微信天气查询失败，userId={}, city={}, error={}",
                    sessionKey,
                    city,
                    rootMessage(exception));
            streamReplyAndRemember(sessionKey, originalText, prompt, emitter);
        }
    }

    private WechatReply handleImageGenerationTask(String sessionKey, String originalText, ImageToolTask task) {
        if (imageGenerationService == null) {
            return WechatReply.text("图片生成服务暂未配置");
        }

        String optimizedPrompt = optimizeImagePrompt(sessionKey, originalText, task.prompt());
        String promptMessage = formatOptimizedImagePrompt(optimizedPrompt);
        if (task.waitForApproval()) {
            memoryFor(sessionKey).recordPendingImagePrompt(originalText, optimizedPrompt);
            return WechatReply.text(promptMessage + "\n\n如果确认要生成，直接回复“可以生成了”就行。");
        }

        return handleImageGeneration(sessionKey, originalText, optimizedPrompt, task.showOptimizedPrompt()
                ? List.of(promptMessage)
                : List.of());
    }

    private WechatReply handleImageGeneration(String sessionKey, String originalText, String prompt, List<String> preImageTexts) {
        if (imageGenerationService == null) {
            return WechatReply.text("图片生成服务暂未配置");
        }

        try {
            ImageGenerationResult image = imageGenerationService.generate(new ImageGenerationRequest(prompt));
            memoryFor(sessionKey).recordImage(originalText, prompt);
            return WechatReply.textsAndImage(preImageTexts, "我已经帮你生成好了，图片如下：", image);
        } catch (RuntimeException exception) {
            log.warn("微信图片生成失败，userId={}, prompt={}, error={}",
                    sessionKey,
                    preview(prompt),
                    rootMessage(exception));
            return WechatReply.text("图片生成失败，请稍后重试");
        }
    }

    private Optional<ImageToolTask> resolveImageToolTask(String sessionKey, String text) {
        if (imageGenerationIntentParser == null || text == null || text.isBlank()) {
            return Optional.empty();
        }

        ConversationMemory memory = memoryFor(sessionKey);
        if (isPendingImageGenerationApproval(text)) {
            Optional<String> pendingPrompt = memory.lastPendingImagePrompt();
            if (pendingPrompt.isPresent()) {
                memory.clearPendingImagePrompt();
                return Optional.of(new ImageToolTask(pendingPrompt.get(), false, false));
            }
        }

        Optional<String> directPrompt = imageGenerationIntentParser.extractPrompt(text);
        if (directPrompt.isPresent()) {
            return Optional.of(new ImageToolTask(
                    directPrompt.get(),
                    requiresApprovalBeforeGeneration(text),
                    shouldShowOptimizedPrompt(text)));
        }

        Optional<String> previousPrompt = memory.lastImagePrompt();
        if (previousPrompt.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> followUpInstruction = imageGenerationIntentParser.extractFollowUpInstruction(text)
                .or(() -> memory.lastAssistantInvitedImageRefinement() ? Optional.of(text.strip()) : Optional.empty());

        return followUpInstruction
                .map(instruction -> new ImageToolTask(
                        buildImageFollowUpPrompt(previousPrompt.get(), instruction, memory.recentImageContextTurns(2)),
                        requiresApprovalBeforeGeneration(text),
                        true));
    }

    private String optimizeImagePrompt(String sessionKey, String originalText, String roughPrompt) {
        String fallback = roughPrompt == null ? "" : roughPrompt.strip();
        if (fallback.isBlank()) {
            return "";
        }

        try {
            String optimized = chatService.reply(buildImagePromptOptimizationPrompt(sessionKey, originalText, fallback));
            optimized = sanitizeOptimizedImagePrompt(optimized);
            if (!optimized.isBlank()) {
                return optimized;
            }
        } catch (RuntimeException exception) {
            log.warn("微信图片提示词优化失败，userId={}, prompt={}, error={}",
                    sessionKey,
                    preview(fallback),
                    rootMessage(exception));
        }
        return fallback;
    }

    private String buildImagePromptOptimizationPrompt(String sessionKey, String originalText, String roughPrompt) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是图片提示词优化器。你的任务不是闲聊，而是把用户的图片需求改写成可直接交给图片生成模型的中文提示词。").append('\n')
                .append("请先理解用户当前这句话里的任务顺序、限制条件和上下文：用户可能要求先优化提示词、再生成图片；也可能要求先别生成，等他确认。").append('\n')
                .append("输出规则：").append('\n')
                .append("1. 只输出最终图片提示词正文，不要输出解释、标题、编号、Markdown、引号。").append('\n')
                .append("2. 提示词要具体描述主体、动作、场景、风格、光线、色彩、构图、镜头/画幅、画面质感。").append('\n')
                .append("3. 如果是修改上一张图，要综合原始图片需求、最近用户反馈、助手上一轮引导和当前用户最新要求。").append('\n')
                .append("4. 不要把“先优化提示词”“再生成图片”“等我允许”这类流程性文字放进图片提示词。").append('\n')
                .append("5. 不要编造与用户目标冲突的新主体；缺失细节可以补充通用且安全的视觉细节。").append('\n');
        appendHistory(prompt, memoryFor(sessionKey));
        prompt.append("用户当前原话：").append(originalText).append('\n')
                .append("待优化的粗提示词：").append(roughPrompt).append('\n')
                .append("请输出优化后的最终图片提示词：");
        return prompt.toString();
    }

    private String sanitizeOptimizedImagePrompt(String optimized) {
        if (optimized == null) {
            return "";
        }

        String text = optimized.strip();
        String[] removablePrefixes = {
                "优化后的图片提示词：",
                "优化后的提示词：",
                "图片提示词：",
                "提示词：",
                "最终图片提示词：",
                "最终提示词："
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : removablePrefixes) {
                if (text.startsWith(prefix)) {
                    text = text.substring(prefix.length()).strip();
                    changed = true;
                    break;
                }
            }
        }
        return text.strip();
    }

    private String formatOptimizedImagePrompt(String optimizedPrompt) {
        return "优化后的图片提示词：\n" + optimizedPrompt;
    }

    private boolean shouldShowOptimizedPrompt(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeForIntent(text);
        return normalized.contains("优化提示词")
                || normalized.contains("优化一下提示词")
                || normalized.contains("先优化")
                || normalized.contains("改写提示词")
                || normalized.contains("完善提示词")
                || normalized.contains("润色提示词");
    }

    private boolean requiresApprovalBeforeGeneration(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeForIntent(text);
        return normalized.contains("等我允许")
                || normalized.contains("等我同意")
                || normalized.contains("等我确认")
                || normalized.contains("我确认后")
                || normalized.contains("确认后再生成")
                || normalized.contains("同意后再生成")
                || normalized.contains("允许后再生成")
                || normalized.contains("先别生成")
                || normalized.contains("先不要生成")
                || normalized.contains("暂时不要生成")
                || normalized.contains("不要现在生成")
                || normalized.contains("只优化提示词");
    }

    private boolean isPendingImageGenerationApproval(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeForIntent(text);
        return normalized.equals("可以生成了")
                || normalized.equals("可以生成")
                || normalized.equals("同意生成")
                || normalized.equals("确认生成")
                || normalized.equals("开始生成")
                || normalized.equals("现在生成")
                || normalized.equals("按这个生成")
                || normalized.equals("就这样生成")
                || normalized.equals("生成吧")
                || normalized.equals("生成");
    }

    private String normalizeForIntent(String value) {
        return value.strip().replaceAll("[\\s，。！？、：:,.!?~～]+", "");
    }

    private String buildImageFollowUpPrompt(
            String previousPrompt,
            String instruction,
            List<ConversationTurn> recentTurns) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("基于上一轮图片需求和最近对话上下文重新生成图片。")
                .append("上一轮图片需求：").append(previousPrompt).append("。");

        if (recentTurns != null && !recentTurns.isEmpty()) {
            prompt.append("最近对话上下文：");
            for (ConversationTurn turn : recentTurns) {
                prompt.append("用户说：").append(turn.userText()).append("。")
                        .append("助手回复：").append(turn.assistantText()).append("。");
            }
        }

        prompt.append("本次用户新的修改要求：").append(instruction).append("。")
                .append("请综合理解：原始图片需求、用户对上一张图的不满、助手上一轮引导用户补充的方向、以及本次用户最新偏好。")
                .append("最终目标是重新生成一张更符合用户预期的新图，而不是只回复文字。")
                .append("请保持主体、主题和必要构图延续上一轮，只调整用户和上下文共同指向的问题。")
                .append("如果用户对人物状态、场景质感、风格氛围提出反馈，要把这些反馈转成明确的视觉要求。");
        return prompt.toString();
    }

    private void streamReplyAndRemember(
            String sessionKey,
            String userText,
            String prompt,
            ReplyEmitter emitter) {
        StringBuilder reply = new StringBuilder();
        chatService.streamReply(prompt, chunk -> {
            if (chunk != null) {
                reply.append(chunk);
                emitter.emit(chunk);
            }
        });
        String assistantReply = reply.toString().strip();
        if (!assistantReply.isBlank()) {
            memoryFor(sessionKey).record(userText, assistantReply);
        }
    }

    private String buildConversationPrompt(String sessionKey, String originalText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是微信聊天助手，请结合最近对话上下文自然回复，不要忽略前文。").append('\n')
                .append("你的回复要像真人微信聊天，语气自然、简洁、具体。").append('\n')
                .append("如果用户的话很短，也要根据上下文接住话题，不要机械复读。").append('\n')
                .append("如果用户明显在延续上一句，就顺着上下文回答，不要重新开场。").append('\n')
                .append("如果上下文不够，就礼貌追问，不要胡编。").append('\n')
                .append("如果用户在提需求、补充偏好、继续修改上一轮内容，要把它当作新输入的一部分理解。").append('\n');
        appendHistory(prompt, memoryFor(sessionKey));
        prompt.append("当前用户：").append(originalText).append('\n')
                .append("请直接回复用户。");
        return prompt.toString();
    }

    private String buildWeatherPrompt(String sessionKey, String originalText, WeatherResult weather) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是微信聊天助手，请结合最近对话上下文回答天气问题，并给出出门建议。").append('\n')
                .append("要求：").append('\n')
                .append("1. 只能依据下面的天气数据，不要编造，不要为了好听而夸大。").append('\n')
                .append("2. 重点说明当前天气、体感、是否需要带伞、穿衣建议和出行建议。").append('\n')
                .append("3. 如果有未来天气，优先总结今天和明天的变化，再给出行动建议。").append('\n')
                .append("4. 如果用户问的是“适不适合出门”“怎么穿”“要不要带伞”这类问题，也要直接结合天气数据作答。").append('\n')
                .append("5. 语气自然，适合微信聊天，别写成天气播报稿。").append('\n');
        appendHistory(prompt, memoryFor(sessionKey));
        prompt.append("当前用户：").append(originalText).append('\n')
                .append("天气数据：").append('\n')
                .append("省份：").append(valueOrUnknown(weather.province())).append('\n')
                .append("城市：").append(valueOrUnknown(weather.city())).append('\n')
                .append("天气：").append(valueOrUnknown(weather.weather())).append('\n')
                .append("温度：").append(valueOrUnknown(weather.temperature())).append("℃\n")
                .append("湿度：").append(valueOrUnknown(weather.humidity())).append("%\n")
                .append("风向：").append(valueOrUnknown(weather.windDirection())).append('\n')
                .append("风力：").append(valueOrUnknown(weather.windPower())).append('\n')
                .append("发布时间：").append(valueOrUnknown(weather.reportTime())).append('\n');

        if (!weather.forecasts().isEmpty()) {
            prompt.append("未来天气：").append('\n');
            for (WeatherResult.Forecast forecast : weather.forecasts()) {
                prompt.append("- ")
                        .append(forecast.date())
                        .append(' ')
                        .append(weekName(forecast.week()))
                        .append("：白天 ")
                        .append(valueOrUnknown(forecast.dayWeather()))
                        .append(' ')
                        .append(valueOrUnknown(forecast.dayTemperature()))
                        .append("℃ ")
                        .append(valueOrUnknown(forecast.dayWind()))
                        .append("风 ")
                        .append(valueOrUnknown(forecast.dayPower()))
                        .append("；夜间 ")
                        .append(valueOrUnknown(forecast.nightWeather()))
                        .append(' ')
                        .append(valueOrUnknown(forecast.nightTemperature()))
                        .append("℃ ")
                        .append(valueOrUnknown(forecast.nightWind()))
                        .append("风 ")
                        .append(valueOrUnknown(forecast.nightPower()))
                        .append('\n');
            }
        }

        prompt.append("请根据这些信息给出自然、简洁、实用、能直接抄给用户看的回答。");
        return prompt.toString();
    }

    private String buildWeatherFailurePrompt(
            String sessionKey,
            String originalText,
            String city,
            Exception exception) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户在问天气，但天气服务暂时失败了。").append('\n')
                .append("请用自然、简洁的中文说明情况，并建议稍后重试。").append('\n')
                .append("不要编造天气，不要假装已经查到了结果。").append('\n')
                .append("如果合适，可以顺手提醒用户稍后重新发一次城市名更明确的天气请求。").append('\n');
        appendHistory(prompt, memoryFor(sessionKey));
        prompt.append("用户原话：").append(originalText).append('\n')
                .append("尝试查询的城市：").append(city).append('\n')
                .append("错误原因：").append(rootMessage(exception)).append('\n');
        return prompt.toString();
    }

    private void appendHistory(StringBuilder prompt, ConversationMemory memory) {
        List<ConversationTurn> turns = memory.snapshot();
        if (turns.isEmpty()) {
            return;
        }

        prompt.append("最近对话：").append('\n');
        for (ConversationTurn turn : turns) {
            prompt.append("用户：").append(turn.userText()).append('\n')
                    .append("助手：").append(turn.assistantText()).append('\n');
        }
    }

    private ConversationMemory memoryFor(String sessionKey) {
        return memories.computeIfAbsent(sessionKey, key -> new ConversationMemory());
    }

    private String sessionKey(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_SESSION_KEY : userId.strip();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String weekName(String week) {
        return switch (week) {
            case "1" -> "周一";
            case "2" -> "周二";
            case "3" -> "周三";
            case "4" -> "周四";
            case "5" -> "周五";
            case "6" -> "周六";
            case "7" -> "周日";
            default -> "周" + (week == null || week.isBlank() ? "未知" : week);
        };
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String messageOrDefault(Throwable exception, String defaultMessage) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
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

    private void emit(ReplyEmitter emitter, String text) {
        if (text != null && !text.isBlank()) {
            emitter.emit(text);
        }
    }

    private static final class ConversationMemory {

        private final Deque<ConversationTurn> turns = new ArrayDeque<>();
        private String lastImagePrompt;
        private String pendingImagePrompt;
        private int lastImagePromptTurnCount;

        synchronized void record(String userText, String assistantText) {
            if (userText == null || userText.isBlank() || assistantText == null || assistantText.isBlank()) {
                return;
            }

            turns.addLast(new ConversationTurn(userText.strip(), assistantText.strip()));
            while (turns.size() > MAX_HISTORY_TURNS) {
                turns.removeFirst();
            }
        }

        synchronized void recordImage(String userText, String imagePrompt) {
            record(userText, "已为你生成图片");
            if (imagePrompt != null && !imagePrompt.isBlank()) {
                lastImagePrompt = imagePrompt.strip();
                pendingImagePrompt = null;
                lastImagePromptTurnCount = turns.size();
            }
        }

        synchronized void recordPendingImagePrompt(String userText, String imagePrompt) {
            record(userText, "已优化图片提示词，等待你确认后生成图片");
            if (imagePrompt != null && !imagePrompt.isBlank()) {
                pendingImagePrompt = imagePrompt.strip();
            }
        }

        synchronized void recordUserImage(String userText, String imageDescription) {
            record(userText, imageDescription);
            if (imageDescription != null && !imageDescription.isBlank()) {
                lastImagePrompt = "用户上传图片的识别描述：" + imageDescription.strip();
                lastImagePromptTurnCount = turns.size();
            }
        }

        synchronized List<ConversationTurn> snapshot() {
            return new ArrayList<>(turns);
        }

        synchronized Optional<String> lastImagePrompt() {
            if (lastImagePrompt == null || lastImagePrompt.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(lastImagePrompt);
        }

        synchronized Optional<String> lastPendingImagePrompt() {
            if (pendingImagePrompt == null || pendingImagePrompt.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(pendingImagePrompt);
        }

        synchronized void clearPendingImagePrompt() {
            pendingImagePrompt = null;
        }

        synchronized List<ConversationTurn> recentTurns(int maxTurns) {
            if (maxTurns <= 0 || turns.isEmpty()) {
                return List.of();
            }

            List<ConversationTurn> snapshot = new ArrayList<>(turns);
            int start = Math.max(0, snapshot.size() - maxTurns);
            return new ArrayList<>(snapshot.subList(start, snapshot.size()));
        }

        synchronized List<ConversationTurn> recentImageContextTurns(int maxTurns) {
            if (maxTurns <= 0 || turns.isEmpty()) {
                return List.of();
            }

            List<ConversationTurn> snapshot = new ArrayList<>(turns);
            int imageContextStart = Math.max(0, lastImagePromptTurnCount - 1);
            int recentStart = Math.max(0, snapshot.size() - maxTurns);
            int start = Math.max(imageContextStart, recentStart);
            return new ArrayList<>(snapshot.subList(start, snapshot.size()));
        }

        synchronized boolean lastAssistantInvitedImageRefinement() {
            ConversationTurn latest = turns.peekLast();
            if (latest == null || latest.assistantText() == null || latest.assistantText().isBlank()) {
                return false;
            }

            String assistant = latest.assistantText();
            return (assistant.contains("重新生成")
                    || assistant.contains("重新画")
                    || assistant.contains("再生成")
                    || assistant.contains("新图片")
                    || assistant.contains("新图")
                    || assistant.contains("告诉我偏好")
                    || assistant.contains("告诉我你的偏好")
                    || assistant.contains("随时告诉我")
                    || assistant.contains("我马上帮你"))
                    && (assistant.contains("图片")
                    || assistant.contains("图")
                    || assistant.contains("画面")
                    || assistant.contains("场景")
                    || assistant.contains("人物"));
        }
    }

    private record ConversationTurn(String userText, String assistantText) {
    }

    private record ImageToolTask(String prompt, boolean waitForApproval, boolean showOptimizedPrompt) {
    }
}
