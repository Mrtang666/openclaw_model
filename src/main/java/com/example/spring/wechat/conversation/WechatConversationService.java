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
import com.example.spring.wechat.image.ImageUnderstandingException;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import com.example.spring.wechat.image.service.ImageInputResolver;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
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
            ImageInputResolver imageInputResolver,
            WeatherIntentParser weatherIntentParser,
            ImageGenerationIntentParser imageGenerationIntentParser) {
        this.chatService = chatService;
        this.weatherService = weatherService;
        this.imageUnderstandingService = imageUnderstandingService;
        this.imageGenerationService = imageGenerationService;
        this.imageInputResolver = imageInputResolver;
        this.weatherIntentParser = weatherIntentParser;
        this.imageGenerationIntentParser = imageGenerationIntentParser;
    }

    WechatConversationService(ChatService chatService, WeatherService weatherService) {
        this(chatService, weatherService, null, null, new ImageInputResolver(), new WeatherIntentParser(), new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser());
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
        ImageAnalysisRequest request = imageInputResolver.resolve(message);
        String text = request.userText();
        boolean hasImages = request.hasImages();

        Optional<String> imageToolPrompt = resolveImageToolPrompt(sessionKey, text);
        if (imageToolPrompt.isPresent()) {
            return handleImageGeneration(sessionKey, text, imageToolPrompt.get());
        }

        if (text == null || text.isBlank()) {
            if (hasImages) {
                StringBuilder output = new StringBuilder();
                handleStreaming(message, output::append);
                return WechatReply.text(output.toString().strip());
            }
            return WechatReply.text("");
        }

        StringBuilder output = new StringBuilder();
        handleStreaming(message, output::append);
        return WechatReply.text(output.toString().strip());
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

    private WechatReply handleImageGeneration(String sessionKey, String originalText, String prompt) {
        if (imageGenerationService == null) {
            return WechatReply.text("图片生成服务暂未配置");
        }

        try {
            ImageGenerationResult image = imageGenerationService.generate(new ImageGenerationRequest(prompt));
            memoryFor(sessionKey).recordImage(originalText, prompt);
            return WechatReply.textAndImage("我已经帮你生成好了，图片如下：", image);
        } catch (RuntimeException exception) {
            log.warn("微信图片生成失败，userId={}, prompt={}, error={}",
                    sessionKey,
                    preview(prompt),
                    rootMessage(exception));
            return WechatReply.text("图片生成失败，请稍后重试");
        }
    }

    private Optional<String> resolveImageToolPrompt(String sessionKey, String text) {
        if (imageGenerationIntentParser == null || text == null || text.isBlank()) {
            return Optional.empty();
        }

        Optional<String> directPrompt = imageGenerationIntentParser.extractPrompt(text);
        if (directPrompt.isPresent()) {
            return directPrompt;
        }

        Optional<String> previousPrompt = memoryFor(sessionKey).lastImagePrompt();
        if (previousPrompt.isEmpty()) {
            return Optional.empty();
        }

        return imageGenerationIntentParser.extractFollowUpInstruction(text)
                .map(instruction -> buildImageFollowUpPrompt(previousPrompt.get(), instruction));
    }

    private String buildImageFollowUpPrompt(String previousPrompt, String instruction) {
        return "基于上一轮图片需求重新生成图片。"
                + "上一轮图片需求：" + previousPrompt + "。"
                + "本次用户修改要求：" + instruction + "。"
                + "请保持主体、风格和构图尽量一致，只调整用户明确指出的部分。";
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
                .append("如果用户的话很短，也要根据上下文接住话题。").append('\n')
                .append("如果上下文不够，就礼貌追问，不要胡编。").append('\n');
        appendHistory(prompt, memoryFor(sessionKey));
        prompt.append("当前用户：").append(originalText).append('\n')
                .append("请直接回复用户。");
        return prompt.toString();
    }

    private String buildWeatherPrompt(String sessionKey, String originalText, WeatherResult weather) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是微信聊天助手，请结合最近对话上下文回答天气问题，并给出出门建议。").append('\n')
                .append("要求：").append('\n')
                .append("1. 只能依据下面的天气数据，不要编造。").append('\n')
                .append("2. 重点说明是否需要带伞、穿衣建议和出行建议。").append('\n')
                .append("3. 语气自然，适合微信聊天。").append('\n');
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

        prompt.append("请根据这些信息给出自然、简洁、实用的回答。");
        return prompt.toString();
    }

    private String buildWeatherFailurePrompt(
            String sessionKey,
            String originalText,
            String city,
            Exception exception) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户在问天气，但天气服务暂时失败了。").append('\n')
                .append("请用自然、简洁的中文说明情况，并建议稍后重试。").append('\n');
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
            }
        }

        synchronized void recordUserImage(String userText, String imageDescription) {
            record(userText, imageDescription);
            if (imageDescription != null && !imageDescription.isBlank()) {
                lastImagePrompt = "用户上传图片的识别描述：" + imageDescription.strip();
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
    }

    private record ConversationTurn(String userText, String assistantText) {
    }
}
