package com.example.spring.wechat.conversation;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.chat.ChatService;
import com.example.spring.chat.ChatServiceException;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.intent.ImageGenerationIntentParser;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.image.generation.service.ImageGenerationService;
import com.example.spring.exception.WeatherServiceException;
import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.ConversationToolPlanner;
import com.example.spring.tool.protocol.legacy.ToolCall;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.agent.FunctionCallingAgentLoop;
import com.example.spring.wechat.conversation.agent.FunctionCallingAgentRequest;
import com.example.spring.wechat.conversation.memory.WechatAgentMemoryContextBuilder;
import com.example.spring.wechat.document.model.ParsedDocument;
import com.example.spring.wechat.document.service.DocumentArchiveService;
import com.example.spring.wechat.document.service.DocumentParseService;
import com.example.spring.wechat.image.archive.ArchivedWechatImage;
import com.example.spring.wechat.image.archive.ImageArchiveService;
import com.example.spring.wechat.image.archive.ImageReferenceResolution;
import com.example.spring.wechat.image.archive.ImageReferenceResolver;
import com.example.spring.wechat.image.archive.ImageReferenceSemanticResolver;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.conversation.tools.WechatToolRequest;
import com.example.spring.wechat.image.exception.ImageUnderstandingException;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import com.example.spring.wechat.image.service.ImageInputResolver;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
import com.example.spring.wechat.memory.config.WechatMemoryProperties;
import com.example.spring.wechat.memory.fallback.InMemoryWechatMemoryFallback;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import com.example.spring.wechat.memory.service.WechatMemoryService;
import com.example.spring.wechat.voice.recognition.VoiceRecognitionException;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;
import com.example.spring.wechat.voice.recognition.service.VoiceRecognitionService;
import com.example.spring.weather.model.WeatherResult;
import com.example.spring.weather.service.WeatherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.example.spring.wechat.conversation.intent.WeatherIntentParser;

/**
 * 微信端对话编排服务。
 * 负责接收文本、图片、语音等输入，维护用户上下文记忆，
 * 调用工具规划器拆解用户需求，再按任务顺序执行天气、图片、语音、大模型对话等工具，
 * 最终组装成 WechatReply 返回给微信 Bot 发送。
 */
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
    private final ConversationToolPlanner toolCallPlanner;
    private final WechatToolRegistry wechatToolRegistry;
    private final WechatMemoryService wechatMemoryService;
    private final DocumentParseService documentParseService;
    private final DocumentArchiveService documentArchiveService;
    private final ImageArchiveService imageArchiveService;
    private final ImageReferenceResolver imageReferenceResolver = new ImageReferenceResolver();
    private final ImageReferenceSemanticResolver imageReferenceSemanticResolver;
    private final WechatAgentMemoryContextBuilder memoryContextBuilder = new WechatAgentMemoryContextBuilder();
    private FunctionCallingAgentLoop functionCallingAgentLoop;
    private String toolCallingMode = "prompt-json";
    private final Map<String, WechatConversationMemory> memories = new ConcurrentHashMap<>();

    @Autowired
    public WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            ImageInputResolver imageInputResolver,
            WeatherIntentParser weatherIntentParser,
            ImageGenerationIntentParser imageGenerationIntentParser,
            ConversationToolPlanner toolCallPlanner,
            WechatToolRegistry wechatToolRegistry,
            WechatMemoryService wechatMemoryService,
            DocumentParseService documentParseService,
            DocumentArchiveService documentArchiveService,
            ImageArchiveService imageArchiveService) {
        this.chatService = chatService;
        this.weatherService = weatherService;
        this.imageUnderstandingService = imageUnderstandingService;
        this.imageGenerationService = imageGenerationService;
        this.voiceRecognitionService = voiceRecognitionService;
        this.imageInputResolver = imageInputResolver;
        this.weatherIntentParser = weatherIntentParser;
        this.imageGenerationIntentParser = imageGenerationIntentParser;
        this.toolCallPlanner = toolCallPlanner;
        this.wechatToolRegistry = wechatToolRegistry;
        this.wechatMemoryService = wechatMemoryService;
        this.documentParseService = documentParseService == null ? DocumentParseService.defaultService() : documentParseService;
        this.documentArchiveService = documentArchiveService == null ? new DocumentArchiveService() : documentArchiveService;
        this.imageArchiveService = imageArchiveService == null ? new ImageArchiveService() : imageArchiveService;
        this.imageReferenceSemanticResolver = new ImageReferenceSemanticResolver(chatService, new ObjectMapper());
    }

    @Autowired
    void configureFunctionCallingAgentLoop(
            ObjectProvider<FunctionCallingAgentLoop> functionCallingAgentLoopProvider,
            @Value("${agent.tool-calling.mode:prompt-json}") String toolCallingMode) {
        configureFunctionCallingAgentLoop(functionCallingAgentLoopProvider.getIfAvailable(), toolCallingMode);
    }

    void configureFunctionCallingAgentLoop(FunctionCallingAgentLoop functionCallingAgentLoop, String toolCallingMode) {
        this.functionCallingAgentLoop = functionCallingAgentLoop;
        this.toolCallingMode = toolCallingMode == null || toolCallingMode.isBlank()
                ? "prompt-json"
                : toolCallingMode.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isFunctionCallingMode() {
        return "function-calling".equalsIgnoreCase(toolCallingMode);
    }

    private boolean hasReplyContent(WechatReply reply) {
        if (reply == null) {
            return false;
        }
        if (reply.text() != null && !reply.text().isBlank()) {
            return true;
        }
        return reply.parts() != null && reply.parts().stream()
                .anyMatch(part -> part != null
                        && (part.text() != null && !part.text().isBlank()
                        || part.hasImage()
                        || part.hasVoice()
                        || part.hasFile()));
    }

    WechatConversationService(ChatService chatService, WeatherService weatherService) {
        this(chatService, weatherService, null, null, null, new ImageInputResolver(), new WeatherIntentParser(), new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, null, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            WeatherIntentParser weatherIntentParser,
            ImageArchiveService imageArchiveService) {
        this(chatService, weatherService, imageUnderstandingService, null, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), imageArchiveService);
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            WeatherIntentParser weatherIntentParser,
            ImageArchiveService imageArchiveService) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, null, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), imageArchiveService);
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            WeatherIntentParser weatherIntentParser) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, voiceRecognitionService, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), null, null, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            WeatherIntentParser weatherIntentParser,
            ConversationToolPlanner toolCallPlanner,
            WechatToolRegistry wechatToolRegistry) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, voiceRecognitionService, new ImageInputResolver(), weatherIntentParser, new ImageGenerationIntentParser(), toolCallPlanner, wechatToolRegistry, localMemoryService(), DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
    }

    WechatConversationService(
            ChatService chatService,
            WeatherService weatherService,
            ImageUnderstandingService imageUnderstandingService,
            ImageGenerationService imageGenerationService,
            VoiceRecognitionService voiceRecognitionService,
            ImageInputResolver imageInputResolver,
            WeatherIntentParser weatherIntentParser,
            ImageGenerationIntentParser imageGenerationIntentParser,
            ConversationToolPlanner toolCallPlanner,
            WechatToolRegistry wechatToolRegistry,
            WechatMemoryService wechatMemoryService) {
        this(chatService, weatherService, imageUnderstandingService, imageGenerationService, voiceRecognitionService,
                imageInputResolver, weatherIntentParser, imageGenerationIntentParser, toolCallPlanner, wechatToolRegistry,
                wechatMemoryService, DocumentParseService.defaultService(), new DocumentArchiveService(), new ImageArchiveService());
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
        return handleWechat(message, true);
    }

    private WechatReply handleWechat(WechatIncomingMessage message, boolean persistIncomingMessage) {
        if (message == null) {
            return WechatReply.text("");
        }

        String sessionKey = sessionKey(message.fromUserId());
        if (persistIncomingMessage && !acceptWechatMessage(sessionKey, message)) {
            log.info("忽略微信重复消息，userId={}, messageId={}",
                    sessionKey, valueOrUnknown(message.messageId()));
            return WechatReply.text("");
        }
        try {
            if (message.hasVoices()) {
                return handleVoiceMessage(sessionKey, message);
            }

            if (message.hasFiles()) {
                rememberIncomingFiles(sessionKey, message.files());
                String rawText = message.text() == null ? "" : message.text().strip();
                if (rawText.isBlank()) {
                    String reply = buildFileRequirementPrompt(message.files());
                    memoryFor(sessionKey).record("发送文件", reply);
                    rememberAssistantReply(sessionKey, reply);
                    return WechatReply.text(reply);
                }
            }

            ImageAnalysisRequest request = imageInputResolver.resolve(message);
            String text = request.userText();
            boolean hasImages = request.hasImages();

            if (hasImages) {
                imageArchiveService.archiveUserImages(sessionKey, message.messageId(), request.images());
                if (text == null || text.isBlank()) {
                    String reply = buildImageRequirementPrompt(request.images());
                    memoryFor(sessionKey).record("发送图片", reply);
                    rememberAssistantReply(sessionKey, reply);
                    return WechatReply.text(reply);
                }

                Optional<WechatReply> structuredReply = handleIntentPlan(sessionKey, text, message.files(), request.images());
                if (structuredReply.isPresent()) {
                    return structuredReply.get();
                }
                StringBuilder output = new StringBuilder();
                handleImageConversation(sessionKey, message, request, output::append);
                Optional<ImageToolTask> imageToolTask = resolveImageToolTask(sessionKey, text);
                if (imageToolTask.isPresent() && imageGenerationService != null) {
                    return handleImageGenerationTask(sessionKey, text, imageToolTask.get());
                }
                return WechatReply.text(output.toString().strip());
            }

            if (text == null || text.isBlank()) {
                return WechatReply.text("");
            }

            List<ArchivedWechatImage> availableImages = imageArchiveService.availableImages(sessionKey);
            if (!availableImages.isEmpty()) {
                ImageReferenceResolution imageResolution = imageReferenceResolver.resolve(text, availableImages);
                if (imageResolution.needsClarification()) {
                    String clarification = imageResolution.clarificationQuestion();
                    memoryFor(sessionKey).recordPendingClarification(
                            text,
                            clarification,
                            "image_reference",
                            List.of("image_reference"));
                    rememberAssistantReply(sessionKey, clarification);
                    return WechatReply.text(clarification);
                }

                List<ArchivedWechatImage> selectedImages = imageResolution.selectedImages();
                if (selectedImages.isEmpty() && referencesImageResource(text)) {
                    ImageReferenceResolution semanticResolution =
                            imageReferenceSemanticResolver.resolve(text, availableImages);
                    if (semanticResolution.needsClarification()) {
                        String clarification = semanticResolution.clarificationQuestion();
                        memoryFor(sessionKey).recordPendingClarification(
                                text,
                                clarification,
                                "image_reference",
                                List.of("image_reference"));
                        rememberAssistantReply(sessionKey, clarification);
                        return WechatReply.text(clarification);
                    }
                    selectedImages = semanticResolution.hasSelection()
                            ? semanticResolution.selectedImages()
                            : imageReferenceResolver.defaultReferenceScope(availableImages);
                }
                List<WechatIncomingImage> selectedWechatImages = imageArchiveService.toWechatImages(selectedImages);
                if (!selectedWechatImages.isEmpty()) {
                Optional<WechatReply> imageStructuredReply = handleIntentPlan(sessionKey, text, message.files(), selectedWechatImages);
                if (imageStructuredReply.isPresent()) {
                    imageArchiveService.markUsed(sessionKey, selectedImages);
                    return imageStructuredReply.get();
                }

                if (containsPendingImage(selectedImages) || referencesImageResource(text)) {
                    WechatIncomingMessage syntheticMessage = new WechatIncomingMessage(
                            message.messageId(),
                            message.fromUserId(),
                            message.contextToken(),
                            text,
                            selectedWechatImages,
                            List.of(),
                            message.files());
                    ImageAnalysisRequest imageRequest = new ImageAnalysisRequest(text, selectedWechatImages);
                    StringBuilder output = new StringBuilder();
                    handleImageConversation(sessionKey, syntheticMessage, imageRequest, output::append);
                    imageArchiveService.markUsed(sessionKey, selectedImages);
                    Optional<ImageToolTask> imageToolTask = resolveImageToolTask(sessionKey, text);
                    if (imageToolTask.isPresent() && imageGenerationService != null) {
                        return handleImageGenerationTask(sessionKey, text, imageToolTask.get());
                    }
                    return WechatReply.text(output.toString().strip());
                }
                }
            }

            Optional<WechatReply> structuredReply = handleIntentPlan(sessionKey, text, message.files());
            if (structuredReply.isPresent()) {
                return structuredReply.get();
            }
            return handleSingleTextTask(sessionKey, text);
        } finally {
            if (persistIncomingMessage) {
                persistWechatMemory(sessionKey);
            }
        }
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

        Optional<WechatReply> weatherReply = handleWeatherIntent(sessionKey, text);
        if (weatherReply.isPresent()) {
            emit(emitter, weatherReply.get().text());
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
            imageArchiveService.archiveUserImages(sessionKey, message.messageId(), request.images());
            if (text == null || text.isBlank()) {
                emit(emitter, buildImageRequirementPrompt(request.images()));
                return;
            }
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

    private Optional<WechatReply> handleIntentPlan(String sessionKey, String text) {
        return handleIntentPlan(sessionKey, text, List.of());
    }

    private Optional<WechatReply> handleIntentPlan(String sessionKey, String text, List<WechatIncomingFile> files) {
        return handleIntentPlan(sessionKey, text, files, List.of());
    }

    private Optional<WechatReply> handleIntentPlan(
            String sessionKey,
            String text,
            List<WechatIncomingFile> files,
            List<WechatIncomingImage> images) {
        if (wechatToolRegistry == null) {
            return Optional.empty();
        }

        if (isFunctionCallingMode() && functionCallingAgentLoop != null) {
            Optional<WechatReply> loopReply = functionCallingAgentLoop.run(new FunctionCallingAgentRequest(
                    sessionKey,
                    text,
                    conversationContext(sessionKey),
                    files,
                    images,
                    (userText, prompt) -> memoryFor(sessionKey).recordPendingImagePrompt(userText, prompt),
                    (userText, prompt) -> memoryFor(sessionKey).recordImage(userText, prompt),
                    (toolName, arguments, resultSummary, status) -> {
                        if (!DEFAULT_SESSION_KEY.equals(sessionKey)) {
                            wechatMemoryService.recordToolExecution(
                                    sessionKey,
                                    toolName,
                                    arguments,
                                    resultSummary,
                                    status,
                                    java.time.Instant.now());
                        }
                    }));
            if (loopReply.isPresent() && hasReplyContent(loopReply.get())) {
                rememberPlannedReply(sessionKey, text, loopReply.get());
                return loopReply;
            }
        }

        if (toolCallPlanner == null) {
            return Optional.empty();
        }

        Optional<ConversationIntentDecision> decision =
                toolCallPlanner.planDecision(text, wechatToolRegistry.definitions(), conversationContext(sessionKey));
        if (decision.isEmpty()) {
            return Optional.empty();
        }

        ConversationIntentDecision intentDecision = decision.get();
        if (intentDecision.needsClarification()) {
            String clarification = clarificationQuestion(text, intentDecision);
            memoryFor(sessionKey).recordPendingClarification(
                    text,
                    clarification,
                    clarificationToolName(intentDecision),
                    clarificationMissingFields(intentDecision));
            return Optional.of(WechatReply.text(clarification));
        }

        if (!intentDecision.hasTasks()) {
            return Optional.empty();
        }

        List<ToolCall> tasks = intentDecision.tasks();
        List<WechatReply.Part> parts = new ArrayList<>();
        String rollingHistory = historyText(sessionKey);
        String previousToolReplyText = "";
        for (int index = 0; index < tasks.size(); index++) {
            ToolCall toolCall = tasks.get(index);
            if (!wechatToolRegistry.contains(toolCall.tool())) {
                log.warn("工具调用计划包含未注册工具，tool={}, userId={}", toolCall.tool(), sessionKey);
                continue;
            }

            Map<String, String> arguments = toolArgumentsWithPreviousResult(toolCall, previousToolReplyText);
            WechatToolRequest request = new WechatToolRequest(
                    sessionKey,
                    text,
                    arguments,
                    rollingHistory,
                    List.of(),
                    files,
                    images,
                    (userText, prompt) -> memoryFor(sessionKey).recordPendingImagePrompt(userText, prompt),
                    (userText, prompt) -> memoryFor(sessionKey).recordImage(userText, prompt));
            WechatReply reply = wechatToolRegistry.execute(toolCall.tool(), request);
            List<WechatReply.Part> replyParts = toReplyParts(reply);
            if (!isIntermediateDocumentAnalysis(toolCall, tasks, index)) {
                parts.addAll(replyParts);
            }
            String toolReplyText = replyMemoryText(replyParts);
            if (!DEFAULT_SESSION_KEY.equals(sessionKey)) {
                wechatMemoryService.recordToolExecution(
                        sessionKey,
                        toolCall.tool(),
                        arguments,
                        toolReplyText,
                        "SUCCESS",
                        java.time.Instant.now());
            }
            if (!toolReplyText.isBlank()) {
                previousToolReplyText = toolReplyText;
                rollingHistory = appendRollingHistory(rollingHistory, toolCall.tool(), toolReplyText);
            }
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        String assistantReply = replyMemoryText(parts);
        if (!assistantReply.isBlank()) {
            memoryFor(sessionKey).record(text, assistantReply);
            memoryFor(sessionKey).clearPendingClarification();
            rememberAssistantReply(sessionKey, assistantReply);
        }
        return Optional.of(WechatReply.ordered(parts));
    }

    private Map<String, String> toolArgumentsWithPreviousResult(ToolCall toolCall, String previousToolReplyText) {
        Map<String, String> arguments = toolCall.arguments() == null
                ? new HashMap<>()
                : new HashMap<>(toolCall.arguments());
        if ("voice_synthesis".equals(toolCall.tool())
                && previousToolReplyText != null
                && !previousToolReplyText.isBlank()) {
            arguments.putIfAbsent("previous_result", previousToolReplyText.strip());
        }
        if ("document_generation".equals(toolCall.tool())
                && previousToolReplyText != null
                && !previousToolReplyText.isBlank()) {
            arguments.putIfAbsent("previous_result", previousToolReplyText.strip());
        }
        return arguments;
    }

    private boolean isIntermediateDocumentAnalysis(ToolCall current, List<ToolCall> tasks, int currentIndex) {
        if (current == null || !"document_analysis".equals(current.tool()) || tasks == null) {
            return false;
        }

        for (int index = currentIndex + 1; index < tasks.size(); index++) {
            ToolCall next = tasks.get(index);
            if (next != null && "document_generation".equals(next.tool())) {
                return true;
            }
        }
        return false;
    }

    private WechatReply handleSingleTextTask(String sessionKey, String text) {
        Optional<ImageToolTask> imageToolTask = resolveImageToolTask(sessionKey, text);
        if (imageToolTask.isPresent() && imageGenerationService != null) {
            WechatReply reply = handleImageGenerationTask(sessionKey, text, imageToolTask.get());
            memoryFor(sessionKey).clearPendingClarification();
            return reply;
        }

        Optional<WechatReply> weatherReply = handleWeatherIntent(sessionKey, text);
        if (weatherReply.isPresent()) {
            memoryFor(sessionKey).clearPendingClarification();
            return weatherReply.get();
        }

        if (imageGenerationIntentParser != null && imageGenerationService != null && imageGenerationIntentParser.matches(text)) {
            memoryFor(sessionKey).clearPendingClarification();
            return WechatReply.text("\u4f60\u60f3\u751f\u6210\u4ec0\u4e48\u6837\u7684\u56fe\u7247\uff1f\u53ef\u4ee5\u544a\u8bc9\u6211\u4e3b\u4f53\u3001\u573a\u666f\u3001\u98ce\u683c\u3001\u989c\u8272\u548c\u6c1b\u56f4\u3002");
        }

        StringBuilder output = new StringBuilder();
        handleNormalConversation(sessionKey, text, output::append);
        memoryFor(sessionKey).clearPendingClarification();
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

    private String replyMemoryText(List<WechatReply.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (WechatReply.Part part : parts) {
            if (part == null) {
                continue;
            }

            if (part.text() != null && !part.text().isBlank()) {
                appendDistinctMemoryText(text, part.text());
            }

            if (part.hasVoice()) {
                String transcript = part.voice() == null ? "" : part.voice().transcriptText();
                if (transcript != null && !transcript.isBlank()) {
                    appendDistinctMemoryText(text, transcript);
                } else {
                    appendDistinctMemoryText(text, "[已发送语音]");
                }
            }

            if (part.hasImage()) {
                appendDistinctMemoryText(text, "[已发送图片]");
            }

            if (part.hasFile()) {
                String fileName = part.file() == null ? "" : part.file().fileName();
                appendDistinctMemoryText(text, fileName.isBlank() ? "[已发送文件]" : "[已发送文件：" + fileName + "]");
            }
        }
        return text.toString().strip();
    }

    private void rememberPlannedReply(String sessionKey, String userText, WechatReply reply) {
        String assistantReply = replyMemoryText(toReplyParts(reply));
        if (assistantReply.isBlank() && reply != null && reply.text() != null) {
            assistantReply = reply.text().strip();
        }
        if (assistantReply.isBlank()) {
            return;
        }
        memoryFor(sessionKey).record(userText, assistantReply);
        memoryFor(sessionKey).clearPendingClarification();
        rememberAssistantReply(sessionKey, assistantReply);
    }

    private void appendDistinctMemoryText(StringBuilder text, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }

        String value = fragment.strip();
        String existing = text.toString().strip();
        if (existing.equals(value) || (!existing.isBlank() && existing.contains(value))) {
            return;
        }

        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(value);
    }

    private String historyText(String sessionKey) {
        List<com.example.spring.wechat.memory.model.ConversationTurn> turns = memoryFor(sessionKey).snapshot();
        StringBuilder history = new StringBuilder();
        String fileContext = fileContext(sessionKey);
        if (!fileContext.isBlank()) {
            history.append(fileContext).append('\n');
        }
        String imageContext = imageArchiveService.imageResourceContext(sessionKey);
        if (!imageContext.isBlank()) {
            history.append(imageContext).append('\n');
        }
        if (turns.isEmpty()) {
            return history.isEmpty() ? "?" : history.toString().strip();
        }
        for (com.example.spring.wechat.memory.model.ConversationTurn turn : turns) {
            history.append("用户：").append(turn.userText()).append('\n')
                    .append("助手：").append(turn.assistantText()).append('\n');
        }
        return history.toString().strip();
    }

    private String appendRollingHistory(String currentHistory, String toolName, String toolReplyText) {
        StringBuilder history = new StringBuilder();
        if (currentHistory != null && !currentHistory.isBlank()) {
            history.append(currentHistory.strip()).append('\n');
        }
        history.append("工具 ").append(toolName).append(" 返回结果：").append(toolReplyText.strip());
        return history.toString().strip();
    }

    private String conversationContext(String sessionKey) {
        if (memoryContextBuilder != null) {
            return memoryContextBuilder.build(
                    memoryFor(sessionKey),
                    imageArchiveService.imageResourceContext(sessionKey));
        }
        WechatConversationMemory memory = memoryFor(sessionKey);
        StringBuilder context = new StringBuilder();
        memory.pendingClarificationUserText().ifPresent(text ->
                context.append("上一轮未完成需求：").append(text).append('\n'));
        memory.pendingClarificationQuestion().ifPresent(question ->
                context.append("上一轮追问：").append(question).append('\n'));
        String fileContext = fileContext(sessionKey);
        if (!fileContext.isBlank()) {
            context.append(fileContext).append('\n');
        }
        String history = historyText(sessionKey);
        if (!history.isBlank()) {
            context.append("最近对话：").append('\n').append(history).append('\n');
        }
        return context.toString().strip();
    }

    private void rememberIncomingFiles(String sessionKey, List<WechatIncomingFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (WechatIncomingFile file : files) {
            if (file == null) {
                continue;
            }
            try {
                ParsedDocument parsed = documentParseService.parse(file);
                documentArchiveService.archive(sessionKey, file, parsed);
                memoryFor(sessionKey).recordFile(
                        parsed.fileName(),
                        parsed.format().name(),
                        parsed.summary());
            } catch (RuntimeException exception) {
                memoryFor(sessionKey).recordFile(
                        file.fileName(),
                        "UNKNOWN",
                        "文件已收到，但解析失败：" + rootMessage(exception));
            }
        }
    }

    private String buildFileRequirementPrompt(List<WechatIncomingFile> files) {
        String fileName = files == null || files.isEmpty() || files.get(0) == null
                ? "文件"
                : files.get(0).fileName();
        return """
                我已经收到文件《%s》。

                你想让我怎么处理它？可以直接说：
                1. 总结全文
                2. 提取重点
                3. 提取表格
                4. 生成汇报稿
                5. 根据它生成新的 Word / PDF / Markdown 文档
                """.formatted(fileName).strip();
    }

    private String buildImageRequirementPrompt(List<WechatIncomingImage> images) {
        int count = images == null ? 0 : images.size();
        String imageText = count <= 1 ? "这张图片" : "这 %d 张图片".formatted(count);
        return """
                我已经收到%s。

                你想让我怎么处理？可以直接说：
                1. 描述图片内容
                2. 提取图片里的文字
                3. 分析图片并给建议
                4. 对比多张图片
                5. 按你的要求基于图片重新生成或修改图片
                """.formatted(imageText).strip();
    }

    private boolean referencesImageResource(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.strip().toLowerCase(java.util.Locale.ROOT);
        return value.contains("图")
                || value.contains("图片")
                || value.contains("照片")
                || value.contains("刚才")
                || value.contains("之前")
                || value.contains("上面")
                || value.contains("这些")
                || value.contains("这几张")
                || value.contains("两张")
                || value.contains("三张")
                || value.contains("放进")
                || value.contains("放到")
                || value.contains("pdf")
                || value.contains("风格")
                || value.contains("修改")
                || value.contains("换成")
                || value.contains("生成")
                || value.contains("image")
                || value.contains("photo")
                || value.contains("picture");
    }

    private boolean containsPendingImage(List<ArchivedWechatImage> images) {
        return images != null && images.stream()
                .anyMatch(image -> image != null && "PENDING".equalsIgnoreCase(image.status()));
    }

    private String fileContext(String sessionKey) {
        WechatConversationMemory memory = memoryFor(sessionKey);
        if (memory.lastFileName().isEmpty() && memory.lastFileSummary().isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("最近文件：");
        memory.lastFileName().ifPresent(context::append);
        memory.lastFileFormat().ifPresent(format -> context.append("，类型：").append(format));
        memory.lastFileSummary().ifPresent(summary -> context.append('\n').append("文件摘要：").append(summary));
        return context.toString().strip();
    }

    private String clarificationQuestion(String originalText, ConversationIntentDecision decision) {
        if (decision != null && decision.clarificationQuestion() != null && !decision.clarificationQuestion().isBlank()) {
            return decision.clarificationQuestion().strip();
        }
        return "我还需要你补充一点信息，才能继续这个需求。";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String clarificationToolName(ConversationIntentDecision decision) {
        if (decision == null || decision.tasks().isEmpty() || decision.tasks().get(0) == null) {
            return "";
        }
        return decision.tasks().get(0).tool();
    }

    private List<String> clarificationMissingFields(ConversationIntentDecision decision) {
        if (decision == null || decision.tasks().isEmpty() || decision.tasks().get(0) == null) {
            return List.of();
        }
        Map<String, String> arguments = decision.tasks().get(0).arguments();
        if (arguments == null || arguments.isEmpty()) {
            return List.of();
        }
        String fields = firstNonBlank(
                arguments.get("missing_fields"),
                arguments.get("missingFields"),
                arguments.get("required_fields"),
                arguments.get("requiredFields"));
        if (fields.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(fields.split("[,，、;；\\s]+"))
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    private WechatReply handleVoiceMessage(String sessionKey, WechatIncomingMessage message) {
        if (voiceRecognitionService == null) {
            return WechatReply.text("语音识别服务暂未配置");
        }

        try {
            String recognizedText = recognizeVoicesWithTool(sessionKey, message);
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
            WechatReply reply = handleWechat(syntheticMessage, false);
            if (reply != null && reply.parts() != null && reply.parts().stream().anyMatch(WechatReply.Part::hasVoice)) {
                return reply;
            }
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

    private String recognizeVoicesWithTool(String sessionKey, WechatIncomingMessage message) {
        if (wechatToolRegistry != null && wechatToolRegistry.contains("voice_recognition")) {
            WechatToolRequest request = new WechatToolRequest(
                    sessionKey,
                    message.text(),
                    Map.of(),
                    historyText(sessionKey),
                    message.voices(),
                    (userText, prompt) -> memoryFor(sessionKey).recordPendingImagePrompt(userText, prompt),
                    (userText, prompt) -> memoryFor(sessionKey).recordImage(userText, prompt));
            WechatReply reply = wechatToolRegistry.execute("voice_recognition", request);
            return reply == null || reply.text() == null ? "" : reply.text().strip();
        }
        return recognizeVoices(message.voices());
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
            rememberAssistantReply(sessionKey, assistantReply);
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
            memoryFor(sessionKey).recordWeatherCity(city);
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

    private Optional<WechatReply> handleWeatherIntent(String sessionKey, String text) {
        Optional<String> weatherCity = resolveWeatherCity(sessionKey, text);
        if (weatherCity.isPresent()) {
            log.info("微信会话识别到天气意图，userId={}, city={}", sessionKey, weatherCity.get());
            StringBuilder output = new StringBuilder();
            handleWeatherQuestion(sessionKey, text, weatherCity.get(), output::append);
            return Optional.of(WechatReply.text(output.toString().strip()));
        }

        if (weatherIntentParser != null && weatherIntentParser.matches(text)) {
            return Optional.of(WechatReply.text("你想查哪个城市的天气？"));
        }

        return Optional.empty();
    }

    private Optional<String> resolveWeatherCity(String sessionKey, String text) {
        if (weatherIntentParser == null || text == null || text.isBlank()) {
            return Optional.empty();
        }

        Optional<String> directCity = weatherIntentParser.extractCity(text);
        if (directCity.isPresent()) {
            return directCity;
        }

        if (!weatherIntentParser.isFollowUp(text)) {
            return Optional.empty();
        }

        WechatConversationMemory memory = memoryFor(sessionKey);
        return memory.lastWeatherCity();
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
            imageArchiveService.archiveGeneratedImage(sessionKey, image);
            rememberAssistantReply(sessionKey, "已为你生成图片");
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

        WechatConversationMemory memory = memoryFor(sessionKey);
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
                .append("2. 提示词要具体描述主体、动作、场景、风格、光线、色彩、构图、镜头、画幅、画面质感。").append('\n')
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
            List<com.example.spring.wechat.memory.model.ConversationTurn> recentTurns) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("基于上一轮图片需求和最近对话上下文重新生成图片。")
                .append("上一轮图片需求：").append(previousPrompt).append("。");

        if (recentTurns != null && !recentTurns.isEmpty()) {
            prompt.append("最近对话上下文：");
            for (com.example.spring.wechat.memory.model.ConversationTurn turn : recentTurns) {
                prompt.append("用户说：").append(turn.userText()).append("。")
                        .append("助手回复：").append(turn.assistantText()).append("。");
            }
        }

        prompt.append("本次用户新的修改要求：").append(instruction).append("。")
                .append("请综合理解：原始图片需求、用户对上一张图的不满、助手上一轮引导用户补充的方向，以及本次用户最新偏好。")
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
            rememberAssistantReply(sessionKey, assistantReply);
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
                        .append("；白天：")
                        .append(valueOrUnknown(forecast.dayWeather()))
                        .append(' ')
                        .append(valueOrUnknown(forecast.dayTemperature()))
                        .append("℃ ")
                        .append(valueOrUnknown(forecast.dayWind()))
                        .append("风 ")
                        .append(valueOrUnknown(forecast.dayPower()))
                        .append("；夜间：")
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

        prompt.append("请根据这些信息给出自然、简洁、实用、能直接发给用户看的回答。");
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

    private void appendHistory(StringBuilder prompt, WechatConversationMemory memory) {
        memory.conversationSummary().ifPresent(summary -> prompt.append("更早会话摘要：")
                .append('\n')
                .append(summary)
                .append('\n'));

        List<com.example.spring.wechat.memory.model.ConversationTurn> turns = memory.snapshot();
        if (turns.isEmpty()) {
            return;
        }

        prompt.append("最近对话：").append('\n');
        for (com.example.spring.wechat.memory.model.ConversationTurn turn : turns) {
            prompt.append("用户：").append(turn.userText()).append('\n')
                    .append("助手：").append(turn.assistantText()).append('\n');
        }
    }

    private WechatConversationMemory memoryFor(String sessionKey) {
        if (DEFAULT_SESSION_KEY.equals(sessionKey)) {
            return memories.computeIfAbsent(
                    sessionKey,
                    key -> WechatConversationMemory.empty(6));
        }
        return memories.computeIfAbsent(sessionKey, wechatMemoryService::memoryFor);
    }

    private boolean acceptWechatMessage(String sessionKey, WechatIncomingMessage message) {
        String contentType = message.hasVoices() ? "VOICE" : message.hasImages() ? "IMAGE" : message.hasFiles() ? "FILE" : "TEXT";
        boolean accepted = wechatMemoryService.acceptIncoming(
                sessionKey,
                message.messageId(),
                message.text(),
                contentType,
                java.time.Instant.now());
        if (accepted) {
            memories.put(sessionKey, wechatMemoryService.memoryFor(sessionKey));
        }
        return accepted;
    }

    private void persistWechatMemory(String sessionKey) {
        if (DEFAULT_SESSION_KEY.equals(sessionKey)) {
            return;
        }
        WechatConversationMemory memory = memories.remove(sessionKey);
        if (memory != null) {
            wechatMemoryService.saveMemory(sessionKey, memory, java.time.Instant.now());
        }
    }

    private void rememberAssistantReply(String sessionKey, String assistantReply) {
        if (!DEFAULT_SESSION_KEY.equals(sessionKey)) {
            wechatMemoryService.recordAssistantMessage(
                    sessionKey,
                    assistantReply,
                    "TEXT",
                    java.time.Instant.now());
        }
    }

    private static WechatMemoryService localMemoryService() {
        return new InMemoryWechatMemoryFallback(new WechatMemoryProperties(60, 30, 6, 20));
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
        private String lastWeatherCity;
        private String pendingClarificationUserText;
        private String pendingClarificationQuestion;
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

        synchronized void recordPendingClarification(String userText, String clarificationQuestion) {
            if (userText != null && !userText.isBlank() && clarificationQuestion != null && !clarificationQuestion.isBlank()) {
                record(userText, clarificationQuestion);
                pendingClarificationUserText = userText.strip();
                pendingClarificationQuestion = clarificationQuestion.strip();
            }
        }

        synchronized void recordUserImage(String userText, String imageDescription) {
            record(userText, imageDescription);
            if (imageDescription != null && !imageDescription.isBlank()) {
                lastImagePrompt = "用户上传图片的识别描述：" + imageDescription.strip();
                lastImagePromptTurnCount = turns.size();
            }
        }

        synchronized void recordWeatherCity(String city) {
            if (city != null && !city.isBlank()) {
                lastWeatherCity = city.strip();
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

        synchronized Optional<String> lastWeatherCity() {
            if (lastWeatherCity == null || lastWeatherCity.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(lastWeatherCity);
        }

        synchronized void clearPendingImagePrompt() {
            pendingImagePrompt = null;
        }

        synchronized void clearPendingClarification() {
            pendingClarificationUserText = null;
            pendingClarificationQuestion = null;
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

        synchronized Optional<String> pendingClarificationUserText() {
            if (pendingClarificationUserText == null || pendingClarificationUserText.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(pendingClarificationUserText);
        }

        synchronized Optional<String> pendingClarificationQuestion() {
            if (pendingClarificationQuestion == null || pendingClarificationQuestion.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(pendingClarificationQuestion);
        }

        synchronized boolean lastAssistantInvitedImageRefinement() {
            ConversationTurn latest = turns.peekLast();
            if (latest == null || latest.assistantText() == null || latest.assistantText().isBlank()) {
                return false;
            }

            String assistant = latest.assistantText();
            return (assistant.contains("重新生成")
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
