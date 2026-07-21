package com.example.spring.wechat;

import com.example.spring.agent.AgentCoordinator;
import com.example.spring.agent.AgentPlan;
import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.agent.AgentRouter;
import com.example.spring.agent.AgentType;
import com.example.spring.agent.ImageAsset;
import com.example.spring.agent.IntentRoutingAgent;
import com.example.spring.agent.IntentRoutingDecision;
import com.example.spring.bailian.BailianProperties;
import com.example.spring.document.DocumentAsset;
import com.example.spring.document.DocumentMemoryService;
import com.example.spring.document.DocumentProcessingException;
import com.example.spring.document.GeneratedDocument;
import com.example.spring.document.PendingDocumentDelivery;
import com.example.spring.document.PendingDocumentDeliveryStore;
import com.example.spring.memory.ConversationMemoryService;
import com.example.spring.speech.Mp3AudioEncoder;
import com.example.spring.speech.PendingVoiceModeOfferService;
import com.example.spring.speech.PendingVoiceReply;
import com.example.spring.speech.PendingVoiceReplyStore;
import com.example.spring.speech.ReadAloudIntentParser;
import com.example.spring.speech.ReadAloudRequest;
import com.example.spring.speech.ReadAloudService;
import com.example.spring.speech.SilkAudioEncoder;
import com.example.spring.speech.SpeechRecognitionException;
import com.example.spring.speech.SpeechRecognitionResult;
import com.example.spring.speech.SpeechRecognitionService;
import com.example.spring.speech.SpeechSynthesisResult;
import com.example.spring.speech.SpeechSynthesisService;
import com.example.spring.speech.SpeechProperties;
import com.example.spring.speech.VoiceAsset;
import com.example.spring.speech.VoiceDeliveryAsset;
import com.example.spring.speech.VoiceSynthesisOptions;
import com.example.spring.speech.voice.VoicePreference;
import com.example.spring.speech.voice.VoiceSelectionResult;
import com.example.spring.speech.voice.VoiceSelectionService;
import com.example.spring.task.ImageTaskDecision;
import com.example.spring.task.ImageTaskOrchestrator;
import com.example.spring.task.TaskDecisionAction;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.ILinkClientBuilder;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WeChatILinkBot implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WeChatILinkBot.class);
    private static final int MAX_REMEMBERED_MESSAGES = 10_000;

    private final WeChatBotProperties properties;
    private final BailianProperties bailianProperties;
    private final AgentRouter agentRouter;
    private final IntentRoutingAgent intentRoutingAgent;
    private final AgentCoordinator agentCoordinator;
    private final ConversationMemoryService memoryService;
    private final DocumentMemoryService documentMemoryService;
    private final PendingDocumentDeliveryStore pendingDocumentDeliveryStore;
    private final ImageTaskOrchestrator imageTaskOrchestrator;
    private final SpeechRecognitionService speechRecognitionService;
    private final SpeechSynthesisService speechSynthesisService;
    private final SpeechProperties speechProperties;
    private final BotInstanceLock instanceLock;
    private final MessageDeliveryLedger deliveryLedger;
    private final VoiceSelectionService voiceSelectionService;
    private final ReadAloudService readAloudService;
    private final PendingVoiceModeOfferService voiceModeOfferService;
    private final PendingVoiceReplyStore pendingVoiceReplyStore;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean everLoggedIn = new AtomicBoolean(false);
    private final Object clientMonitor = new Object();
    private final Object sendMonitor = new Object();
    private long lastOutboundSendNanos;
    private final Set<Long> processedMessageIds = ConcurrentHashMap.newKeySet();

    private volatile Thread botThread;
    private volatile ILinkClient client;
    private volatile ResumeContext resumeContext;

    public WeChatILinkBot(
        WeChatBotProperties properties,
        BailianProperties bailianProperties,
        AgentRouter agentRouter,
        IntentRoutingAgent intentRoutingAgent,
        AgentCoordinator agentCoordinator,
        ConversationMemoryService memoryService,
        DocumentMemoryService documentMemoryService,
        PendingDocumentDeliveryStore pendingDocumentDeliveryStore,
        ImageTaskOrchestrator imageTaskOrchestrator,
        SpeechRecognitionService speechRecognitionService,
        SpeechSynthesisService speechSynthesisService,
        SpeechProperties speechProperties,
        BotInstanceLock instanceLock,
        MessageDeliveryLedger deliveryLedger,
        VoiceSelectionService voiceSelectionService,
        ReadAloudService readAloudService,
        PendingVoiceModeOfferService voiceModeOfferService,
        PendingVoiceReplyStore pendingVoiceReplyStore) {
        this.properties = properties;
        this.bailianProperties = bailianProperties;
        this.agentRouter = agentRouter;
        this.intentRoutingAgent = intentRoutingAgent;
        this.agentCoordinator = agentCoordinator;
        this.memoryService = memoryService;
        this.documentMemoryService = documentMemoryService;
        this.pendingDocumentDeliveryStore = pendingDocumentDeliveryStore;
        this.imageTaskOrchestrator = imageTaskOrchestrator;
        this.speechRecognitionService = speechRecognitionService;
        this.speechSynthesisService = speechSynthesisService;
        this.speechProperties = speechProperties;
        this.instanceLock = instanceLock;
        this.deliveryLedger = deliveryLedger;
        this.voiceSelectionService = voiceSelectionService;
        this.readAloudService = readAloudService;
        this.voiceModeOfferService = voiceModeOfferService;
        this.pendingVoiceReplyStore = pendingVoiceReplyStore;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("微信 iLink 机器人已禁用");
            return;
        }
        if (!bailianProperties.isConfigured()) {
            log.warn("BAILIAN_API_KEY 未配置，对话、图片识别和图片生成暂不可用，天气模块仍可独立使用");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!instanceLock.tryAcquire()) {
            running.set(false);
            log.error(
                "检测到另一个微信机器人进程正在运行。请先停止旧的 DemoApplication 或 JAR 进程，避免 iLink 会话互相挤掉");
            return;
        }

        Thread thread = new Thread(this::runBot, "wechat-ilink-bot");
        thread.setDaemon(false);
        botThread = thread;
        thread.start();
    }

    private void runBot() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ResumeContext currentResumeContext = resumeContext;
                ILinkClient activeClient = createClient(currentResumeContext);
                if (currentResumeContext == null) {
                    String loginUrl = activeClient.executeLogin();
                    openLoginUrl(loginUrl, !everLoggedIn.get());

                    LoginContext loginContext = activeClient.getLoginFuture().get();
                    everLoggedIn.set(true);
                    resumeContext = activeClient.exportResumeContext();
                    log.info("微信登录成功，botId={}", loginContext.getBotId());
                    log.info("微信多 Agent 机器人已启动");
                } else {
                    log.info("正在使用已有微信会话恢复连接，不需要重新扫码");
                }

                resumePendingVoiceReplies(activeClient);
                resumePendingDocumentDeliveries(activeClient);
                promptPendingRecovery(activeClient);

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    pollAndReply(activeClient);
                    resumeContext = activeClient.exportResumeContext();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                if (running.get()) {
                    if (containsSessionExpired(exception)) {
                        resumeContext = null;
                        if (everLoggedIn.get()) {
                            log.warn(
                                "微信会话凭证已失效，将生成新的登录链接，但登录成功后不会再自动弹出浏览器",
                                exception);
                        } else {
                            log.warn("微信登录会话已失效，准备重新生成登录链接", exception);
                        }
                    } else {
                        log.warn("微信连接异常，{} 后尝试恢复已有会话", properties.getRetryDelay(), exception);
                    }
                }
            } finally {
                closeClient();
            }

            if (!sleepBeforeRetry()) {
                break;
            }
        }
        running.set(false);
        closeClient();
        instanceLock.close();
    }

    private ILinkClient createClient(ResumeContext currentResumeContext) {
        ILinkClientBuilder builder = ILinkClient.builder().config(createILinkConfig());
        if (currentResumeContext != null) {
            builder.resumeContext(currentResumeContext);
        }
        ILinkClient newClient = builder.build();
        synchronized (clientMonitor) {
            client = newClient;
        }
        return newClient;
    }

    static ILinkConfig createILinkConfig() {
        return ILinkConfig.builder()
            .heartbeatEnabled(true)
            .heartbeatIntervalMs(30_000)
            .autoReconnectEnabled(true)
            .reconnectMaxAttempts(5)
            .reconnectBaseDelayMs(1_000)
            .reconnectMaxDelayMs(30_000)
            .build();
    }

    private void pollAndReply(ILinkClient activeClient) throws InterruptedException {
        try {
            replyToMessages(activeClient, activeClient.getUpdates());
        } catch (IOException exception) {
            if (running.get()) {
                log.warn("拉取微信消息失败，{} 后重试", properties.getRetryDelay(), exception);
                Thread.sleep(properties.getRetryDelay().toMillis());
            }
        }
    }

    void replyToMessages(ILinkClient activeClient, List<WeixinMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (WeixinMessage message : messages) {
            String userId = message == null ? null : message.getFrom_user_id();
            if (userId == null || userId.isBlank() || isDuplicate(message.getMessage_id())) {
                continue;
            }

            MessageSummary summary = summarize(message);
            long messageId = message.getMessage_id() == null ? 0L : message.getMessage_id();
            if (messageId != 0L && !deliveryLedger.register(
                userId, messageId, sourceType(summary), summary.text())) {
                log.debug("消息已存在于持久化投递台账，跳过重复处理，userId={}，messageId={}",
                    userId, messageId);
                continue;
            }
            log.info(
                "收到微信消息，userId={}，messageId={}，textLength={}，imageCount={}，voiceCount={}，fileCount={}，unsupportedTypes={}",
                userId,
                message.getMessage_id(),
                summary.text().length(),
                summary.imageItems().size(),
                summary.voiceItems().size(),
                summary.fileItems().size(),
                summary.unsupportedTypes());
            if (messageId != 0L
                && !summary.voiceItems().isEmpty()
                && !deliveryLedger.findWaitingForUser(userId).isEmpty()) {
                try {
                    String recoveryVoiceText = recognizeVoices(
                        activeClient, userId, message.getMessage_id(), summary.voiceItems());
                    summary = new MessageSummary(
                        mergeText(summary.text(), recoveryVoiceText),
                        summary.imageItems(),
                        List.of(),
                        summary.fileItems(),
                        summary.unsupportedTypes());
                    deliveryLedger.ready(userId, messageId, summary.text());
                } catch (SpeechRecognitionException | IOException exception) {
                    deliveryLedger.ready(userId, messageId, summary.text());
                    deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                    DeliveryResult result = sendTextSafely(
                        activeClient,
                        userId,
                        "没有听清你的语音确认，请用文字回复“需要”或“不需要”，也可以直接提出新问题。",
                        messageId);
                    completeDelivery(userId, messageId, result);
                    continue;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (messageId != 0L
                && handlePendingRecoveryIntent(activeClient, userId, messageId, summary.text())) {
                continue;
            }
            if (!summary.hasProcessableContent()) {
                if (messageId != 0L) {
                    deliveryLedger.ready(userId, messageId, summary.text());
                    deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                }
                DeliveryResult result = sendTextSafely(
                    activeClient, userId, unsupportedReply(summary), message.getMessage_id());
                completeDelivery(userId, messageId, result);
                continue;
            }
            if (messageId != 0L) {
                deliveryLedger.ready(userId, messageId, summary.text());
            }

            AgentRequest previewRequest = memoryService.prepare(new AgentRequest(
                userId,
                message.getMessage_id(),
                summary.text(),
                List.of(),
                summary.imageItems().size(),
                List.of(),
                List.of(),
                List.of(),
                summary.fileItems().size(),
                List.of()));
            previewRequest = previewRequest.withDocuments(
                List.of(), summary.fileItems().size(),
                documentMemoryService.resolve(userId, summary.text()));
            AgentPlan plan = agentRouter.route(previewRequest);
            boolean activeImageTask = imageTaskOrchestrator.hasActiveSession(userId);
            boolean recentImageContext = imageTaskOrchestrator.hasRecentImageContext(previewRequest);
            boolean voiceSelectionPending = voiceSelectionService.shouldHandle(
                userId, summary.text());
            boolean readAloudPending = ReadAloudIntentParser.isReadAloudIntent(summary.text());
            boolean voiceModeOfferPending = voiceModeOfferService.hasPendingOffer(userId);
            log.info(
                "微信消息已完成快速预路由，userId={}，messageId={}，steps={}",
                userId,
                message.getMessage_id(),
                plan.steps());
            sendTextSafely(
                activeClient,
                userId,
                processingMessage(
                    plan,
                    activeImageTask,
                    recentImageContext,
                    !summary.voiceItems().isEmpty(),
                    voiceSelectionPending,
                    readAloudPending,
                    voiceModeOfferPending),
                message.getMessage_id());

            startTypingSafely(activeClient, userId, message.getMessage_id());
            processMessage(activeClient, message, summary, previewRequest, plan);
        }
    }

    private void processMessage(
        ILinkClient activeClient,
        WeixinMessage message,
        MessageSummary summary,
        AgentRequest previewRequest,
        AgentPlan plan) {
        String userId = previewRequest.userId();
        try {
            List<ImageAsset> images = downloadImages(activeClient, summary.imageItems());
            List<DocumentAsset> documents = downloadDocuments(
                activeClient, userId, message.getMessage_id(), summary.fileItems());
            String recognizedVoice = recognizeVoices(
                activeClient, userId, message.getMessage_id(), summary.voiceItems());
            String combinedText = mergeText(summary.text(), recognizedVoice);
            long messageId = message.getMessage_id() == null ? 0L : message.getMessage_id();
            if (messageId != 0L) {
                deliveryLedger.ready(userId, messageId, combinedText);
                deliveryLedger.mark(userId, messageId, MessageReplyStatus.PROCESSING);
            }
            AgentRequest request = memoryService.prepare(new AgentRequest(
                userId,
                message.getMessage_id(),
                combinedText,
                images,
                summary.imageItems().size(),
                List.of(),
                List.of(),
                documents,
                summary.fileItems().size(),
                documentMemoryService.resolve(userId, combinedText)));
            if (!images.isEmpty()) {
                memoryService.rememberUserRequest(request);
            }
            AgentPlan fallbackPlan = agentRouter.route(request);
            PendingVoiceModeOfferService.OfferDecision offerDecision =
                voiceModeOfferService.consume(userId, combinedText);
            if (offerDecision == PendingVoiceModeOfferService.OfferDecision.ACCEPT
                || offerDecision == PendingVoiceModeOfferService.OfferDecision.DECLINE) {
                boolean enable = offerDecision
                    == PendingVoiceModeOfferService.OfferDecision.ACCEPT;
                if (enable) {
                    memoryService.setReplyMode(userId, ReplyMode.VOICE);
                }
                stopTypingSafely(activeClient, userId, message.getMessage_id());
                deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                DeliveryResult result = sendTextSafely(
                    activeClient,
                    userId,
                    enable
                        ? "已开启语音对话模式，后续回复将优先使用语音。"
                        : "好的，本次只进行朗读，继续保持文字对话模式。",
                    message.getMessage_id());
                completeDelivery(userId, messageId, result);
                return;
            }
            ReplyModeCommand modeCommand = ReplyModeCommand.parse(combinedText);
            if (modeCommand != ReplyModeCommand.NONE) {
                ReplyMode mode = modeCommand == ReplyModeCommand.ENABLE_VOICE
                    ? ReplyMode.VOICE : ReplyMode.TEXT;
                boolean updated = memoryService.setReplyMode(userId, mode);
                ReplyMode savedMode = memoryService.getReplyMode(userId);
                if (!updated || savedMode != mode) {
                    stopTypingSafely(activeClient, userId, message.getMessage_id());
                    DeliveryResult result = sendTextSafely(
                        activeClient,
                        userId,
                        "回复模式切换失败，请稍后重试。",
                        message.getMessage_id());
                    completeDelivery(userId, messageId, result);
                    return;
                }
                String confirmation = mode == ReplyMode.VOICE
                    ? "已开启语音对话模式，后续回复将优先使用语音。"
                    : "已关闭语音对话模式，后续回复将使用文字。";
                stopTypingSafely(activeClient, userId, message.getMessage_id());
                deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                DeliveryResult result = sendTextSafely(
                    activeClient, userId, confirmation, message.getMessage_id());
                completeDelivery(userId, messageId, result);
                return;
            }
            if (!voiceSelectionService.hasActiveSession(userId)) {
                ReadAloudRequest readAloud = readAloudService.resolve(userId, combinedText);
                if (readAloud.requested()) {
                    stopTypingSafely(activeClient, userId, message.getMessage_id());
                    deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                    DeliveryResult result = handleReadAloud(
                        activeClient, userId, readAloud, message.getMessage_id());
                    completeDelivery(userId, messageId, result);
                    return;
                }
            }
            VoiceSelectionResult voiceSelection = voiceSelectionService.handle(
                userId, combinedText);
            if (voiceSelection.consumed()) {
                stopTypingSafely(activeClient, userId, message.getMessage_id());
                deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                DeliveryResult result = sendTextSafely(
                    activeClient, userId, voiceSelection.reply(), message.getMessage_id());
                if (voiceSelection.preview() != null) {
                    VoicePreference preview = voiceSelection.preview();
                    VoiceReplyResult previewResult = sendVoiceResponse(
                        activeClient,
                        userId,
                        voicePreviewText(preview),
                        message.getMessage_id(),
                        new VoiceSynthesisOptions(
                            preview.voiceId(), preview.languageType()));
                    if (!previewResult.success()) {
                        result = sendTextSafely(
                            activeClient,
                            userId,
                            "音色试听失败，原因：" + previewResult.error()
                                + "。当前音色没有被修改，你可以重新试听或选择其他音色。",
                            message.getMessage_id());
                    } else {
                        result = DeliveryResult.sent();
                    }
                }
                completeDelivery(userId, messageId, result);
                return;
            }
            if (!summary.voiceItems().isEmpty()) {
                sendTextSafely(
                    activeClient,
                    userId,
                    "语音识别完成，" + processingMessage(
                        fallbackPlan,
                        imageTaskOrchestrator.hasActiveSession(userId),
                        imageTaskOrchestrator.hasRecentImageContext(request),
                        false,
                        false,
                        false,
                        false),
                    message.getMessage_id());
            }
            IntentRoutingDecision routingDecision = routeIntent(request, fallbackPlan);
            request = request.withDocumentTaskPlan(routingDecision.documentTaskPlan());
            AgentPlan effectivePlan = routingDecision.plan();
            log.info(
                "顶层意图路由完成，userId={}，messageId={}，semantic={}，steps={}，documentTask={}",
                userId,
                message.getMessage_id(),
                routingDecision.semantic(),
                effectivePlan.steps(),
                routingDecision.documentTaskPlan());
            if (imageTaskOrchestrator.requiresSourceImage(userId)
                || imageTaskOrchestrator.hasRecentImageContext(request)) {
                request = memoryService.attachLatestImage(request);
            }
            ImageTaskDecision taskDecision = imageTaskOrchestrator.decide(request, effectivePlan);
            log.info(
                "图片任务编排完成，userId={}，messageId={}，action={}，steps={}",
                userId,
                message.getMessage_id(),
                taskDecision.action(),
                taskDecision.plan().steps());
            if (taskDecision.action() == TaskDecisionAction.REPLY) {
                AgentResponse guidance = AgentResponse.text(taskDecision.reply());
                memoryService.rememberUserRequest(request);
                memoryService.rememberAgentResult(
                    AgentType.CHAT, request, guidance);
                stopTypingSafely(activeClient, userId, message.getMessage_id());
                deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
                DeliveryResult result = sendResponse(
                    activeClient, userId, guidance, message.getMessage_id());
                completeDelivery(userId, messageId, result);
                return;
            }
            if (taskDecision.action() == TaskDecisionAction.EXECUTE) {
                // Preserve the user's original wording before the text Agent's refined prompt.
                memoryService.rememberUserRequest(request);
            }
            AgentResponse response = agentCoordinator.execute(
                taskDecision.plan(), taskDecision.request());
            log.info(
                "Agent 处理完成，userId={}，messageId={}，textReplyLength={}，imageReplyCount={}，fileReplyCount={}",
                userId,
                message.getMessage_id(),
                response.text().length(),
                response.images().size(),
                response.files().size());
            stopTypingSafely(activeClient, userId, message.getMessage_id());
            deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
            DeliveryResult result = sendResponse(
                activeClient, userId, response, message.getMessage_id());
            completeDelivery(userId, messageId, result);
            if (taskDecision.action() == TaskDecisionAction.EXECUTE) {
                imageTaskOrchestrator.complete(userId);
            }
        } catch (DocumentProcessingException exception) {
            log.warn("文件处理失败，userId={}，messageId={}",
                userId, message.getMessage_id(), exception);
            stopTypingSafely(activeClient, userId, message.getMessage_id());
            DeliveryResult result = sendTextSafely(
                activeClient,
                userId,
                "文件处理失败：" + exception.getMessage(),
                message.getMessage_id());
            completeDelivery(userId,
                message.getMessage_id() == null ? 0L : message.getMessage_id(), result);
        } catch (SpeechRecognitionException exception) {
            log.warn("语音识别失败，userId={}，messageId={}",
                userId, message.getMessage_id(), exception);
            stopTypingSafely(activeClient, userId, message.getMessage_id());
            DeliveryResult result = sendTextSafely(
                activeClient,
                userId,
                "语音识别失败：" + exception.getMessage(),
                message.getMessage_id());
            completeDelivery(userId, message.getMessage_id() == null ? 0L : message.getMessage_id(), result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.error("消息处理失败，userId={}，messageId={}",
                userId, message.getMessage_id(), exception);
            stopTypingSafely(activeClient, userId, message.getMessage_id());
            DeliveryResult result = sendTextSafely(
                activeClient,
                userId,
                properties.getModelErrorReply(),
                message.getMessage_id());
            completeDelivery(userId, message.getMessage_id() == null ? 0L : message.getMessage_id(), result);
        } finally {
            stopTypingSafely(activeClient, userId, message.getMessage_id());
        }
    }

    private void promptPendingRecovery(ILinkClient activeClient) {
        Set<String> users = new LinkedHashSet<>();
        for (PendingMessageRecord record : deliveryLedger.findRecoverable()) {
            users.add(record.userId());
        }
        for (String userId : users) {
            DeliveryResult result = sendTextSafely(
                activeClient,
                userId,
                "当前识别到有历史对话未回复，是否需要回复？",
                null);
            if (result.success()) {
                deliveryLedger.markRecoverableWaiting(userId);
                log.info("已询问用户是否恢复历史未回复消息，userId={}", userId);
            } else {
                log.warn("历史未回复消息恢复询问发送失败，等待下次重连重试，userId={}，error={}",
                    userId, result.error());
            }
        }
        deliveryLedger.cleanup();
    }

    private boolean handlePendingRecoveryIntent(
        ILinkClient activeClient,
        String userId,
        long messageId,
        String text) {
        List<PendingMessageRecord> waiting = deliveryLedger.findWaitingForUser(userId);
        if (waiting.isEmpty()) {
            return false;
        }
        PendingReplyIntentParser.RecoveryIntent intent = PendingReplyIntentParser.parse(text);
        if (intent == PendingReplyIntentParser.RecoveryIntent.NEW_REQUEST) {
            deliveryLedger.resolveWaiting(userId, MessageReplyStatus.SUPERSEDED);
            log.info("用户提出了新问题，已跳过历史未回复消息，userId={}，count={}",
                userId, waiting.size());
            return false;
        }
        if (intent == PendingReplyIntentParser.RecoveryIntent.UNCLEAR) {
            deliveryLedger.ready(userId, messageId, text);
            deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
            DeliveryResult result = sendTextSafely(
                activeClient,
                userId,
                "请回复“需要”继续处理历史消息，或回复“不需要”忽略；也可以直接提出新问题。",
                messageId);
            completeDelivery(userId, messageId, result);
            return true;
        }
        if (intent == PendingReplyIntentParser.RecoveryIntent.DECLINE) {
            deliveryLedger.resolveWaiting(userId, MessageReplyStatus.DECLINED);
            deliveryLedger.ready(userId, messageId, text);
            deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
            DeliveryResult result = sendTextSafely(
                activeClient, userId, "好的，已忽略历史未回复消息。", messageId);
            completeDelivery(userId, messageId, result);
            return true;
        }

        deliveryLedger.resolveWaiting(userId, MessageReplyStatus.READY);
        deliveryLedger.ready(userId, messageId, text);
        deliveryLedger.mark(userId, messageId, MessageReplyStatus.REPLYING);
        DeliveryResult confirmation = sendTextSafely(
            activeClient,
            userId,
            "好的，正在按时间顺序处理历史未回复消息。",
            messageId);
        completeDelivery(userId, messageId, confirmation);
        for (PendingMessageRecord record : waiting) {
            processRecoveredMessage(activeClient, record);
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
        return true;
    }

    private void processRecoveredMessage(
        ILinkClient activeClient,
        PendingMessageRecord record) {
        String replayText = record.replayText();
        if (replayText.isBlank() && record.sourceType().contains("VOICE")) {
            try {
                replayText = recognizePendingVoices(record);
                deliveryLedger.ready(record.userId(), record.messageId(), replayText);
            } catch (SpeechRecognitionException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("恢复历史语音识别失败，userId={}，messageId={}",
                    record.userId(), record.messageId(), exception);
            }
        }
        if (replayText.isBlank() && !record.sourceType().contains("IMAGE")
            && !record.sourceType().contains("FILE")) {
            deliveryLedger.mark(
                record.userId(), record.messageId(), MessageReplyStatus.REPLYING);
            DeliveryResult result = sendTextSafely(
                activeClient,
                record.userId(),
                "这条历史消息的原始内容未能完整保留，暂时无法继续处理，请重新发送一次。",
                record.messageId());
            completeDelivery(record.userId(), record.messageId(), result);
            return;
        }
        try {
            deliveryLedger.mark(
                record.userId(), record.messageId(), MessageReplyStatus.PROCESSING);
            List<DocumentAsset> recoveredDocuments = record.sourceType().contains("FILE")
                ? documentMemoryService.findByMessage(record.userId(), record.messageId())
                : List.of();
            AgentRequest request = memoryService.prepare(new AgentRequest(
                record.userId(),
                record.messageId(),
                replayText.isBlank()
                    ? (record.sourceType().contains("FILE")
                        ? "请处理我之前发送的文件" : "请识别并回复我之前发送的图片")
                    : replayText,
                List.of(),
                record.sourceType().contains("IMAGE") ? 1 : 0,
                List.of(),
                List.of(),
                recoveredDocuments,
                recoveredDocuments.size(),
                List.of()));
            if (record.sourceType().contains("IMAGE")) {
                request = memoryService.attachLatestImage(request);
            }
            AgentPlan fallbackPlan = agentRouter.route(request);
            IntentRoutingDecision routingDecision = routeIntent(request, fallbackPlan);
            request = request.withDocumentTaskPlan(routingDecision.documentTaskPlan());
            AgentPlan plan = routingDecision.plan();
            ImageTaskDecision decision = imageTaskOrchestrator.decide(request, plan);
            AgentResponse response;
            if (decision.action() == TaskDecisionAction.REPLY) {
                response = AgentResponse.text(decision.reply());
                memoryService.rememberUserRequest(request);
                memoryService.rememberAgentResult(AgentType.CHAT, request, response);
            } else {
                response = agentCoordinator.execute(decision.plan(), decision.request());
            }
            deliveryLedger.mark(
                record.userId(), record.messageId(), MessageReplyStatus.REPLYING);
            DeliveryResult result = sendResponse(
                activeClient, record.userId(), response, record.messageId());
            completeDelivery(record.userId(), record.messageId(), result);
            if (decision.action() == TaskDecisionAction.EXECUTE) {
                imageTaskOrchestrator.complete(record.userId());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deliveryLedger.mark(record.userId(), record.messageId(), MessageReplyStatus.FAILED);
        } catch (Exception exception) {
            log.error("恢复历史未回复消息失败，userId={}，messageId={}",
                record.userId(), record.messageId(), exception);
            deliveryLedger.mark(
                record.userId(), record.messageId(), MessageReplyStatus.REPLYING);
            DeliveryResult result = sendTextSafely(
                activeClient,
                record.userId(),
                properties.getModelErrorReply(),
                record.messageId());
            completeDelivery(record.userId(), record.messageId(), result);
        }
    }

    private void completeDelivery(String userId, long messageId, DeliveryResult result) {
        if (messageId != 0L) {
            deliveryLedger.complete(userId, messageId, result);
        }
    }

    private IntentRoutingDecision routeIntent(
        AgentRequest request,
        AgentPlan fallbackPlan) {
        try {
            return intentRoutingAgent.route(request);
        } catch (Exception exception) {
            log.warn("顶层意图路由 Agent 调用失败，使用本地规则降级，userId={}，messageId={}",
                request.userId(), request.messageId(), exception);
            return new IntentRoutingDecision(fallbackPlan, null, false);
        }
    }

    private static String sourceType(MessageSummary summary) {
        List<String> types = new ArrayList<>();
        if (!summary.text().isBlank()) {
            types.add("TEXT");
        }
        if (!summary.imageItems().isEmpty()) {
            types.add("IMAGE");
        }
        if (!summary.voiceItems().isEmpty()) {
            types.add("VOICE");
        }
        if (!summary.fileItems().isEmpty()) {
            types.add("FILE");
        }
        return types.isEmpty() ? "UNSUPPORTED" : String.join("_", types);
    }

    private String processingMessage(
        AgentPlan plan,
        boolean activeImageTask,
        boolean recentImageContext,
        boolean voicePending,
        boolean voiceSelectionPending,
        boolean readAloudPending,
        boolean voiceModeOfferPending) {
        if (voicePending) {
            return "语音已收到，正在识别，请稍等。";
        }
        if (voiceModeOfferPending) {
            return "正在处理语音模式设置，请稍等。";
        }
        if (readAloudPending) {
            return "正在为你生成朗读语音，请稍等。";
        }
        if (voiceSelectionPending) {
            return "正在为你整理音色选择，请稍等。";
        }
        if (plan.primaryType() == AgentType.WEATHER) {
            return agentRouter.processingMessage(plan);
        }
        if (activeImageTask
            || plan.steps().contains(AgentType.IMAGE_GENERATION)) {
            return "我正在理解并整理你的图片需求，请稍等。";
        }
        if (recentImageContext) {
            return "我正在结合之前的内容理解你的需求，请稍等。";
        }
        return agentRouter.processingMessage(plan);
    }

    private DeliveryResult handleReadAloud(
        ILinkClient activeClient,
        String userId,
        ReadAloudRequest request,
        Long messageId) {
        if (request.targetText().isBlank()) {
            return sendTextSafely(activeClient, userId, request.errorReply(), messageId);
        }
        boolean offerVoiceMode = memoryService.getReplyMode(userId) == ReplyMode.TEXT;
        if (offerVoiceMode) {
            voiceModeOfferService.offer(userId);
        }
        VoiceReplyResult voiceResult = sendVoiceResponse(
            activeClient,
            userId,
            request.targetText(),
            messageId,
            offerVoiceMode
                ? "当前语音对话模式尚未开启，是否需要开启？"
                : "");
        if (!voiceResult.success()) {
            voiceModeOfferService.cancel(userId);
            return sendTextSafely(
                activeClient,
                userId,
                "朗读语音发送失败，原因：" + voiceResult.error()
                    + "。先将原文发给你：\n" + request.targetText(),
                messageId);
        }
        return DeliveryResult.sent();
    }

    private String recognizeVoices(
        ILinkClient activeClient,
        String userId,
        Long messageId,
        List<MessageItem> voiceItems)
        throws IOException, SpeechRecognitionException, InterruptedException {
        StringBuilder text = new StringBuilder();
        int sequence = 0;
        for (MessageItem item : voiceItems) {
            byte[] data = activeClient.downloadVoiceFromMessageItem(item);
            if (messageId != null) {
                deliveryLedger.saveVoicePayload(userId, messageId, sequence, data);
            }
            sequence++;
            var voice = item.getVoice_item();
            log.info(
                "收到微信语音媒体，bytes={}，encodeType={}，sampleRate={}，bitsPerSample={}，playtime={}，silkHeaderOffset={}",
                data == null ? 0 : data.length,
                voice == null ? null : voice.getEncode_type(),
                voice == null ? null : voice.getSample_rate(),
                voice == null ? null : voice.getBits_per_sample(),
                voice == null ? null : voice.getPlaytime(),
                findSilkHeader(data));
            VoiceAsset asset = new VoiceAsset(
                data,
                com.example.spring.speech.AudioFormatDetector.detect(
                    data, voice == null ? null : voice.getEncode_type()),
                voice == null ? null : voice.getSample_rate(),
                voice == null ? null : voice.getBits_per_sample(),
                voice == null ? null : voice.getPlaytime());
            SpeechRecognitionResult result = speechRecognitionService.recognize(asset);
            if (!result.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(result.text());
            }
        }
        if (!voiceItems.isEmpty() && text.isEmpty()) {
            throw new SpeechRecognitionException("没有识别出清晰语音，请重新录制");
        }
        return text.toString();
    }

    private String recognizePendingVoices(PendingMessageRecord record)
        throws SpeechRecognitionException, InterruptedException {
        StringBuilder text = new StringBuilder();
        for (byte[] data : deliveryLedger.loadVoicePayloads(
            record.userId(), record.messageId())) {
            VoiceAsset asset = new VoiceAsset(
                data,
                com.example.spring.speech.AudioFormatDetector.detect(data, null),
                null,
                null,
                null);
            SpeechRecognitionResult result = speechRecognitionService.recognize(asset);
            if (!result.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(result.text());
            }
        }
        if (text.isEmpty()) {
            throw new SpeechRecognitionException("历史语音文件不存在或没有识别出清晰内容");
        }
        return text.toString();
    }

    private static String mergeText(String original, String recognizedVoice) {
        String left = original == null ? "" : original.trim();
        String right = recognizedVoice == null ? "" : recognizedVoice.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + "\n" + right;
    }

    private List<ImageAsset> downloadImages(
        ILinkClient activeClient,
        List<MessageItem> imageItems) throws IOException {
        List<ImageAsset> images = new ArrayList<>();
        int index = 1;
        for (MessageItem item : imageItems) {
            byte[] data = activeClient.downloadImageFromMessageItem(item);
            String mediaType = detectImageMediaType(data);
            String extension = switch (mediaType) {
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
            images.add(new ImageAsset(data, mediaType, "wechat-image-" + index + extension));
            index++;
        }
        return images;
    }

    private List<DocumentAsset> downloadDocuments(
        ILinkClient activeClient,
        String userId,
        Long messageId,
        List<MessageItem> fileItems) throws DocumentProcessingException {
        List<DocumentAsset> documents = new ArrayList<>();
        for (MessageItem item : fileItems) {
            String fileName = item.getFile_item() == null
                ? "wechat-document" : item.getFile_item().getFile_name();
            try {
                byte[] data = activeClient.downloadFileFromMessageItem(item);
                documents.add(documentMemoryService.store(userId, messageId, data, fileName));
            } catch (IOException | RuntimeException exception) {
                throw new DocumentProcessingException(
                    "无法读取“" + (fileName == null ? "未命名文件" : fileName)
                        + "”：" + exception.getMessage(), exception);
            }
        }
        return documents;
    }

    private DeliveryResult sendResponse(
        ILinkClient activeClient,
        String userId,
        AgentResponse response,
        Long messageId) {
        boolean voiceMode = memoryService.getReplyMode(userId) == ReplyMode.VOICE;
        if (response.images().isEmpty() && response.files().isEmpty()) {
            if (!response.text().isBlank()) {
                VoiceReplyResult voiceResult = voiceMode
                    ? sendVoiceResponse(activeClient, userId, response.text(), messageId)
                    : VoiceReplyResult.sent();
                if (!voiceMode || !voiceResult.success()) {
                    if (voiceMode && !voiceResult.success()) {
                        return sendTextSafely(activeClient, userId,
                            "语音回复失败，原因：" + voiceResult.error()
                                + "。现改用文字回复：\n" + response.text(), messageId);
                    } else {
                        return sendTextSafely(activeClient, userId, response.text(), messageId);
                    }
                }
                return DeliveryResult.sent();
            }
            return DeliveryResult.sent();
        }

        DeliveryResult textResult = DeliveryResult.sent();
        if (!response.text().isBlank()) {
            VoiceReplyResult voiceResult = voiceMode
                ? sendVoiceResponse(activeClient, userId, response.text(), messageId)
                : VoiceReplyResult.sent();
            if (!voiceMode || !voiceResult.success()) {
                if (voiceMode && !voiceResult.success()) {
                    textResult = sendTextSafely(activeClient, userId,
                        "语音回复失败，原因：" + voiceResult.error()
                            + "。现改用文字回复：\n" + response.text(), messageId);
                } else {
                    textResult = sendTextSafely(
                        activeClient, userId, response.text(), messageId);
                }
            }
        }
        boolean allImagesSent = true;
        for (ImageAsset image : response.images()) {
            if (!sendImageSafely(activeClient, userId, image, messageId)) {
                allImagesSent = false;
            }
        }
        if (!allImagesSent) {
            sendTextSafely(
                activeClient,
                userId,
                "图片已经生成，但发送失败，请稍后再次发送刚才的修改要求。",
                messageId);
            return textResult.success()
                ? DeliveryResult.partial("图片发送失败")
                : DeliveryResult.failed("文字和图片均发送失败：" + textResult.error());
        }
        boolean allFilesSent = true;
        for (GeneratedDocument file : response.files()) {
            if (!sendFileSafely(activeClient, userId, file, messageId)) {
                allFilesSent = false;
                pendingDocumentDeliveryStore.save(userId, messageId, file);
            }
        }
        if (!allFilesSent) {
            DeliveryResult notice = sendTextSafely(
                activeClient,
                userId,
                "文件已经生成，但微信文件发送持续失败。文件已保留，重新连接后会再次尝试发送。",
                messageId);
            return notice.success() ? DeliveryResult.partial("文件发送失败")
                : DeliveryResult.failed("文件和失败提示均未发送成功");
        }
        if (!response.files().isEmpty()) {
            sendTextSafely(activeClient, userId,
                "文件发送完成：" + response.files().stream()
                    .map(GeneratedDocument::fileName)
                    .reduce((left, right) -> left + "、" + right).orElse(""),
                messageId);
        }
        return textResult;
    }

    private boolean sendFileSafely(
        ILinkClient activeClient,
        String userId,
        GeneratedDocument file,
        Long messageId) {
        int maxAttempts = Math.max(3, Math.min(5,
            speechProperties.getVoiceRetryMaxAttempts()));
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                synchronized (sendMonitor) {
                    if (!awaitOutboundSlot()) return false;
                    activeClient.sendFile(userId, file.data(), file.fileName(), file.description());
                    markOutboundSent();
                }
                log.info("微信文件发送成功，userId={}，messageId={}，fileName={}，bytes={}，attempt={}",
                    userId, messageId, file.fileName(), file.data().length, attempt);
                return true;
            } catch (IOException | RuntimeException exception) {
                log.warn("微信文件发送失败，userId={}，messageId={}，fileName={}，attempt={}/{}",
                    userId, messageId, file.fileName(), attempt, maxAttempts, exception);
                if (attempt < maxAttempts && !sleepVoiceGap(retryDelayMillis(attempt))) {
                    return false;
                }
            }
        }
        return false;
    }

    private void resumePendingDocumentDeliveries(ILinkClient activeClient) {
        Set<String> completedUsers = new LinkedHashSet<>();
        for (PendingDocumentDelivery pending : pendingDocumentDeliveryStore.loadPending()) {
            if (!sendFileSafely(activeClient, pending.userId(), pending.document(), pending.messageId())) {
                log.warn("待重试文件仍未发送成功，id={}，fileName={}",
                    pending.id(), pending.document().fileName());
                return;
            }
            pendingDocumentDeliveryStore.complete(pending);
            completedUsers.add(pending.userId());
        }
        for (String userId : completedUsers) {
            sendTextSafely(activeClient, userId, "之前发送失败的文件已经重新发送成功。", null);
        }
    }

    private VoiceReplyResult sendVoiceResponse(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId) {
        return sendVoiceResponse(activeClient, userId, text, messageId, "");
    }

    private VoiceReplyResult sendVoiceResponse(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId,
        String completionSuffix) {
        VoicePreference preference = memoryService.getVoicePreference(userId);
        VoiceSynthesisOptions options = preference == null
            ? VoiceSynthesisOptions.defaults(speechProperties)
            : new VoiceSynthesisOptions(
                preference.voiceId(), preference.languageType());
        return sendVoiceResponse(
            activeClient, userId, text, messageId, options, true, completionSuffix);
    }

    private VoiceReplyResult sendVoiceResponse(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId,
        VoiceSynthesisOptions options) {
        return sendVoiceResponse(activeClient, userId, text, messageId, options, false, "");
    }

    private VoiceReplyResult sendVoiceResponse(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId,
        VoiceSynthesisOptions options,
        boolean persistOnFailure,
        String completionSuffix) {
        sendTextSafely(activeClient, userId, "对方正在说话中...", messageId);
        try {
            List<PreparedVoiceSegment> segments = new ArrayList<>();
            for (String chunk : splitVoiceText(text)) {
                segments.addAll(synthesizeVoiceSegments(chunk, 0, options));
            }
            for (int index = 0; index < segments.size(); index++) {
                int segment = index + 1;
                PreparedVoiceSegment prepared = segments.get(index);
                SpeechSynthesisResult result = prepared.silk();
                log.info(
                    "准备发送微信语音，segment={}，bytes={}，durationMs={}，sampleRate=16000，encodeType=6，silkHeaderOffset={}",
                    segment, result.data().length, result.durationMs(),
                    findSilkHeader(result.data()));
                PacketSendResult voicePacket = sendVoicePacketWithRetry(
                    activeClient, userId, prepared, segment, messageId);
                if (!voicePacket.success()) {
                    return failedVoiceDelivery(
                        userId, messageId, segments, index, false,
                        voicePacket.error(), persistOnFailure);
                }
                if (!sleepVoiceGap(interPacketDelayMillis())) {
                    return VoiceReplyResult.failed("语音发送等待被中断，后续分段已暂停");
                }
                PacketSendResult filePacket = sendVoiceFileWithRetry(
                    activeClient, userId, prepared, segment, messageId);
                if (!filePacket.success()) {
                    return failedVoiceDelivery(
                        userId, messageId, segments, index, true,
                        filePacket.error(), persistOnFailure);
                }
                if (index < segments.size() - 1
                    && !sleepVoiceGap(voiceSendIntervalMillis())) {
                    return VoiceReplyResult.failed("语音发送等待被中断，后续分段已暂停");
                }
            }
            if (!sleepVoiceGap(voiceSendIntervalMillis())) {
                return VoiceReplyResult.failed("语音完成提示发送等待被中断");
            }
            DeliveryResult completionNotice = sendTextSafely(
                activeClient,
                userId,
                (segments.size() > 1
                    ? "语音内容已全部发送完成，共 " + segments.size()
                        + " 段。每段均已提供可播放音频文件。"
                    : "语音发送完成，已提供可播放音频文件。")
                    + (completionSuffix == null || completionSuffix.isBlank()
                        ? "" : "\n" + completionSuffix),
                messageId);
            if (!completionNotice.success()) {
                return VoiceReplyResult.failed(
                    "语音已发送，但完成提示发送失败：" + completionNotice.error());
            }
            log.info(
                "已向用户发送语音回复，userId={}，messageId={}，bytes={}，format={}",
                userId, messageId, segments.size());
            return VoiceReplyResult.sent();
        } catch (Exception exception) {
            log.warn("语音回复发送失败，将回退为文字，userId={}，messageId={}",
                userId, messageId, exception);
            String reason = exception.getMessage();
            return VoiceReplyResult.failed(
                reason == null || reason.isBlank() ? "语音文件生成或发送失败" : reason);
        }
    }

    private VoiceReplyResult failedVoiceDelivery(
        String userId,
        Long messageId,
        List<PreparedVoiceSegment> segments,
        int startIndex,
        boolean firstVoiceSent,
        String error,
        boolean persistOnFailure) {
        if (!persistOnFailure) {
            return VoiceReplyResult.failed(error);
        }
        boolean queued = pendingVoiceReplyStore.saveRemaining(
            userId,
            messageId == null ? 0L : messageId,
            segments.stream().map(WeChatILinkBot::deliveryAsset).toList(),
            startIndex,
            firstVoiceSent);
        return VoiceReplyResult.failed(
            error + (queued
                ? "。失败分段及后续内容已保存，将在重新连接后继续发送"
                : "。保存待重试语音失败"));
    }

    private static VoiceDeliveryAsset deliveryAsset(PreparedVoiceSegment segment) {
        return new VoiceDeliveryAsset(
            segment.text(),
            segment.silk().data(),
            segment.silk().durationMs(),
            segment.playable().data());
    }

    private void resumePendingVoiceReplies(ILinkClient activeClient) {
        List<PendingVoiceReply> pending = pendingVoiceReplyStore.loadPending();
        if (pending.isEmpty()) {
            return;
        }
        Set<String> completedUsers = new LinkedHashSet<>();
        for (int index = 0; index < pending.size(); index++) {
            PendingVoiceReply reply = pending.get(index);
            PreparedVoiceSegment prepared = new PreparedVoiceSegment(
                reply.asset().text(),
                new SpeechSynthesisResult(
                    reply.asset().mp3Data(), "mp3", 16_000, 16, 1,
                    reply.asset().durationMs()),
                new SpeechSynthesisResult(
                    reply.asset().silkData(), "silk", 16_000, 16, 1,
                    reply.asset().durationMs()));
            if (!reply.voiceSent()) {
                PacketSendResult voice = sendVoicePacketWithRetry(
                    activeClient, reply.userId(), prepared,
                    reply.sequence(), reply.messageId());
                if (!voice.success()) {
                    log.warn("待重试语音仍未发送成功，batchId={}，sequence={}",
                        reply.batchId(), reply.sequence());
                    return;
                }
                pendingVoiceReplyStore.markVoiceSent(reply.id());
            }
            if (!sleepVoiceGap(interPacketDelayMillis())) {
                return;
            }
            PacketSendResult file = sendVoiceFileWithRetry(
                activeClient, reply.userId(), prepared,
                reply.sequence(), reply.messageId());
            if (!file.success()) {
                log.warn("待重试音频文件仍未发送成功，batchId={}，sequence={}",
                    reply.batchId(), reply.sequence());
                return;
            }
            pendingVoiceReplyStore.complete(reply);
            completedUsers.add(reply.userId());
            if (index < pending.size() - 1 && !sleepVoiceGap(voiceSendIntervalMillis())) {
                return;
            }
        }
        for (String userId : completedUsers) {
            sendTextSafely(
                activeClient,
                userId,
                "之前发送失败的语音已经重新发送成功。",
                null);
        }
    }

    private PacketSendResult sendVoicePacketWithRetry(
        ILinkClient activeClient,
        String userId,
        PreparedVoiceSegment prepared,
        int segment,
        Long messageId) {
        return sendPacketWithRetry(
            activeClient,
            userId,
            segment,
            "语音气泡",
            messageId,
            () -> activeClient.sendVoice(
                userId,
                prepared.silk().data(),
                "wechat-reply-" + segment + ".silk",
                (int) Math.min(60_000, Math.max(1, prepared.silk().durationMs())),
                16_000,
                null,
                6,
                16,
                prepared.text()));
    }

    private PacketSendResult sendVoiceFileWithRetry(
        ILinkClient activeClient,
        String userId,
        PreparedVoiceSegment prepared,
        int segment,
        Long messageId) {
        return sendPacketWithRetry(
            activeClient,
            userId,
            segment,
            "可播放音频文件",
            messageId,
            () -> activeClient.sendFile(
                userId,
                prepared.playable().data(),
                "voice-reply-" + segment + ".mp3",
                ""));
    }

    private PacketSendResult sendPacketWithRetry(
        ILinkClient activeClient,
        String userId,
        int segment,
        String packetName,
        Long messageId,
        PacketSender sender) {
        int maxAttempts = Math.max(1, speechProperties.getVoiceRetryMaxAttempts());
        String lastError = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                synchronized (sendMonitor) {
                    if (!awaitOutboundSlot()) {
                        return PacketSendResult.failed("发送等待被中断");
                    }
                    sender.send();
                    markOutboundSent();
                }
                if (attempt > 1) {
                    sendTextSafely(
                        activeClient,
                        userId,
                        "第" + segment + "段" + packetName
                            + "已重新发送成功，继续发送后续内容。",
                        messageId);
                }
                return PacketSendResult.sent(attempt);
            } catch (IOException | RuntimeException exception) {
                lastError = exception.getMessage();
                log.warn(
                    "微信语音包发送失败，userId={}，messageId={}，segment={}，packet={}，attempt={}/{}",
                    userId, messageId, segment, packetName, attempt, maxAttempts, exception);
                if (attempt == 1) {
                    sendTextSafely(
                        activeClient,
                        userId,
                        "第" + segment + "段" + packetName
                            + "发送失败，正在自动重试，请稍等。",
                        messageId);
                }
                if (attempt < maxAttempts && !sleepVoiceGap(retryDelayMillis(attempt))) {
                    return PacketSendResult.failed(
                        "第" + segment + "段" + packetName + "重试等待被中断");
                }
            }
        }
        String detail = lastError == null || lastError.isBlank()
            ? "微信接口持续发送失败" : lastError;
        return PacketSendResult.failed(
            "第" + segment + "段" + packetName + "连续重试 " + maxAttempts
                + " 次仍未成功，后续语音已暂停：" + detail);
    }

    private long voiceSendIntervalMillis() {
        return safeDurationMillis(speechProperties.getVoiceSendInterval(), 2500);
    }

    private long interPacketDelayMillis() {
        return Math.min(600, Math.max(250, voiceSendIntervalMillis() / 3));
    }

    private long retryDelayMillis(int failedAttempt) {
        long base = safeDurationMillis(speechProperties.getVoiceRetryBaseDelay(), 1500);
        long maximum = safeDurationMillis(speechProperties.getVoiceRetryMaxDelay(), 10_000);
        int shift = Math.min(20, Math.max(0, failedAttempt - 1));
        long multiplier = 1L << shift;
        if (base > Long.MAX_VALUE / multiplier) {
            return maximum;
        }
        return Math.min(maximum, base * multiplier);
    }

    private static long safeDurationMillis(java.time.Duration value, long fallback) {
        if (value == null || value.isNegative()) {
            return fallback;
        }
        return value.toMillis();
    }

    private static boolean sleepVoiceGap(long milliseconds) {
        if (milliseconds <= 0) {
            return true;
        }
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean awaitOutboundSlot() {
        long intervalMillis = voiceSendIntervalMillis();
        if (intervalMillis <= 0 || lastOutboundSendNanos == 0L) {
            return true;
        }
        long intervalNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        long elapsed = System.nanoTime() - lastOutboundSendNanos;
        long remaining = intervalNanos - elapsed;
        if (remaining <= 0) {
            return true;
        }
        long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining);
        int nanos = (int) (remaining
            - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis));
        try {
            Thread.sleep(millis, nanos);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void markOutboundSent() {
        lastOutboundSendNanos = System.nanoTime();
    }

    private static String voicePreviewText(VoicePreference preference) {
        if (preference != null
            && "English".equalsIgnoreCase(preference.languageType())) {
            return "Hello, this is a preview of my current voice. Nice to meet you.";
        }
        return "你好，这是当前候选音色的试听效果。你可以继续试听，或者选择这个音色。";
    }

    private List<PreparedVoiceSegment> synthesizeVoiceSegments(
        String text,
        int depth,
        VoiceSynthesisOptions options)
        throws SpeechRecognitionException, InterruptedException {
        SpeechSynthesisResult generated = speechSynthesisService.synthesize(
            text, speechProperties.getTtsFormat(), options);
        SpeechSynthesisResult silk = SilkAudioEncoder.encode(
            generated, speechProperties.getSilkEncoderPath());
        if (silk.durationMs() <= 60_000 || text.length() <= 1 || depth >= 4) {
            if (silk.durationMs() > 60_000) {
                throw new SpeechRecognitionException("语音片段超过 60 秒，无法安全发送");
            }
            SpeechSynthesisResult playable = Mp3AudioEncoder.encode(
                generated, speechProperties.getSilkEncoderPath());
            if (playable.data().length == 0) {
                throw new SpeechRecognitionException("百炼返回的 MP3 语音文件为空");
            }
            return List.of(new PreparedVoiceSegment(text, playable, silk));
        }
        int split = findVoiceSplit(text);
        if (split <= 0 || split >= text.length()) {
            throw new SpeechRecognitionException("无法在 60 秒限制内分割语音内容");
        }
        List<PreparedVoiceSegment> results = new ArrayList<>();
        results.addAll(synthesizeVoiceSegments(
            text.substring(0, split).trim(), depth + 1, options));
        results.addAll(synthesizeVoiceSegments(
            text.substring(split).trim(), depth + 1, options));
        return results;
    }

    private static List<String> splitVoiceText(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= 300) {
            return List.of(value);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + 300);
            if (end < value.length()) {
                int boundary = findVoiceSplit(value.substring(start, end));
                if (boundary > 80) {
                    end = start + boundary;
                }
            }
            chunks.add(value.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private static int findVoiceSplit(String text) {
        int midpoint = Math.max(1, text.length() / 2);
        for (int index = midpoint; index < text.length(); index++) {
            char current = text.charAt(index - 1);
            if (current == '。' || current == '！' || current == '？'
                || current == '；' || current == '.' || current == '!'
                || current == '?' || current == ';' || current == '\n') {
                return index;
            }
        }
        return midpoint;
    }

    private static int findSilkHeader(byte[] data) {
        byte[] header = "#!SILK_V3".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (data == null) {
            return -1;
        }
        int limit = Math.min(8, data.length - header.length);
        for (int offset = 0; offset <= limit; offset++) {
            boolean matches = true;
            for (int index = 0; index < header.length; index++) {
                if (data[offset + index] != header[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return offset;
            }
        }
        return -1;
    }

    private record VoiceReplyResult(boolean success, String error) {
        static VoiceReplyResult sent() {
            return new VoiceReplyResult(true, "");
        }

        static VoiceReplyResult failed(String error) {
            return new VoiceReplyResult(false, error);
        }
    }

    private record PreparedVoiceSegment(
        String text,
        SpeechSynthesisResult playable,
        SpeechSynthesisResult silk) {
    }

    @FunctionalInterface
    private interface PacketSender {
        void send() throws IOException;
    }

    private record PacketSendResult(boolean success, int attempts, String error) {
        static PacketSendResult sent(int attempts) {
            return new PacketSendResult(true, attempts, "");
        }

        static PacketSendResult failed(String error) {
            return new PacketSendResult(false, 0, error);
        }
    }

    private boolean sendImageSafely(
        ILinkClient activeClient,
        String userId,
        ImageAsset image,
        Long messageId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                synchronized (sendMonitor) {
                    activeClient.sendImage(
                        userId, image.data(), image.fileName(), "");
                }
                log.info(
                    "已向用户发送图片，userId={}，messageId={}，attempt={}，bytes={}，mediaType={}",
                    userId,
                    messageId,
                    attempt,
                    image.data().length,
                    image.mediaType());
                return true;
            } catch (IOException | RuntimeException exception) {
                log.warn(
                    "微信图片发送失败，userId={}，messageId={}，attempt={}",
                    userId,
                    messageId,
                    attempt,
                    exception);
                if (attempt < 2 && !sleepImageRetry()) {
                    break;
                }
            }
        }
        return false;
    }

    private boolean sleepImageRetry() {
        try {
            Thread.sleep(800);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private DeliveryResult sendTextSafely(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId) {
        int maxAttempts = Math.max(3, Math.min(5,
            speechProperties.getVoiceRetryMaxAttempts()));
        String lastError = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                synchronized (sendMonitor) {
                    if (!awaitOutboundSlot()) {
                        return DeliveryResult.failed("文字发送等待被中断");
                    }
                    // Explicit start/stop typing calls are handled separately. Using plain text
                    // avoids the SDK issuing three tightly packed prepare requests here.
                    activeClient.sendText(userId, text);
                    markOutboundSent();
                }
                log.info("已回复用户，userId={}，messageId={}，attempt={}",
                    userId, messageId, attempt);
                return DeliveryResult.sent();
            } catch (IOException | RuntimeException exception) {
                lastError = exception.getMessage();
                log.warn(
                    "微信文字消息发送失败，userId={}，messageId={}，attempt={}/{}，准备重试普通文本发送",
                    userId, messageId, attempt, maxAttempts, exception);
                if (attempt < maxAttempts && !sleepVoiceGap(retryDelayMillis(attempt))) {
                    return DeliveryResult.failed("文字消息重试等待被中断");
                }
            }
        }
        return DeliveryResult.failed(
            lastError == null || lastError.isBlank()
                ? "微信文字消息连续重试仍失败" : lastError);
    }

    private void startTypingSafely(
        ILinkClient activeClient,
        String userId,
        Long messageId) {
        if (!properties.isTypingIndicatorEnabled()) {
            return;
        }
        try {
            synchronized (sendMonitor) {
                activeClient.startTyping(userId);
            }
            log.debug("已开启微信输入状态，userId={}，messageId={}", userId, messageId);
        } catch (IOException | RuntimeException exception) {
            log.warn("开启微信输入状态失败，将继续正常处理消息，userId={}，messageId={}",
                userId, messageId, exception);
        }
    }

    private void stopTypingSafely(
        ILinkClient activeClient,
        String userId,
        Long messageId) {
        if (!properties.isTypingIndicatorEnabled()) {
            return;
        }
        try {
            synchronized (sendMonitor) {
                activeClient.stopTyping(userId);
            }
            log.debug("已停止微信输入状态，userId={}，messageId={}", userId, messageId);
        } catch (IOException | RuntimeException exception) {
            log.warn("停止微信输入状态失败，userId={}，messageId={}",
                userId, messageId, exception);
        }
    }

    static MessageSummary summarize(WeixinMessage message) {
        if (message == null || message.getItem_list() == null) {
            return new MessageSummary("", List.of(), List.of(), List.of(), List.of());
        }
        StringBuilder text = new StringBuilder();
        List<MessageItem> images = new ArrayList<>();
        List<MessageItem> voices = new ArrayList<>();
        List<MessageItem> files = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (MessageItem item : message.getItem_list()) {
            if (item == null) {
                continue;
            }
            appendText(text, item);
            if (item.getImage_item() != null) {
                images.add(item);
            }
            if (item.getVoice_item() != null
                && (item.getVoice_item().getText() == null
                    || item.getVoice_item().getText().isBlank())) {
                voices.add(item);
            }
            if (item.getFile_item() != null) {
                files.add(item);
            }
            if (item.getVideo_item() != null) {
                unsupported.add("视频");
            }
            if (item.getVoice_item() != null
                && (item.getVoice_item().getText() == null
                    || item.getVoice_item().getText().isBlank())) {
                unsupported.add("未转写的语音");
            }
        }
        return new MessageSummary(text.toString(), images, voices, files, unsupported);
    }

    static String extractText(WeixinMessage message) {
        return summarize(message).text();
    }

    static boolean containsText(WeixinMessage message) {
        return !extractText(message).isBlank();
    }

    private static void appendText(StringBuilder text, MessageItem item) {
        String value = null;
        if (item.getText_item() != null) {
            value = item.getText_item().getText();
        } else if (item.getVoice_item() != null) {
            value = item.getVoice_item().getText();
        }
        if (value != null && !value.isBlank()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value.trim());
        }
    }

    private boolean isDuplicate(Long messageId) {
        if (messageId == null) {
            return false;
        }
        if (processedMessageIds.size() >= MAX_REMEMBERED_MESSAGES) {
            processedMessageIds.clear();
        }
        return !processedMessageIds.add(messageId);
    }

    private static String unsupportedReply(MessageSummary summary) {
        if (!summary.unsupportedTypes().isEmpty()) {
            return "已收到" + String.join("、", summary.unsupportedTypes())
                + "消息。当前版本支持文字、带转写文字的语音、图片、图片 URL 和常用办公文件；该内容暂时无法进一步识别。";
        }
        return "已收到消息，但没有识别到可处理的文字或图片内容。";
    }

    private static String detectImageMediaType(byte[] data) {
        if (data != null && data.length >= 8
            && data[0] == (byte) 0x89 && data[1] == 0x50
            && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        if (data != null && data.length >= 6
            && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data != null && data.length >= 12
            && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private void openLoginUrl(String loginUrl, boolean openBrowser) {
        log.info("微信 iLink 扫码登录入口：{}", loginUrl);
        if (!openBrowser) {
            log.warn("已登录过微信，本次不再自动打开浏览器；需要重新登录时请手动打开上面的链接");
            return;
        }
        try {
            BrowserLauncher.open(loginUrl);
            log.info("已调用系统默认浏览器打开微信登录入口");
        } catch (IOException | RuntimeException exception) {
            log.warn("浏览器自动打开失败，请复制上面的登录入口到浏览器", exception);
        }
    }

    private static boolean containsSessionExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SessionExpiredException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean sleepBeforeRetry() {
        if (!running.get() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            Thread.sleep(properties.getRetryDelay().toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void stop() {
        running.set(false);
        ILinkClient currentClient = client;
        if (currentClient != null) {
            currentClient.cancelLogin();
        }
        closeClient();
        Thread currentThread = botThread;
        botThread = null;
        if (currentThread != null && currentThread != Thread.currentThread()) {
            currentThread.interrupt();
        }
        instanceLock.close();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void closeClient() {
        ILinkClient currentClient;
        synchronized (clientMonitor) {
            currentClient = client;
            client = null;
        }
        if (currentClient != null) {
            currentClient.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    record MessageSummary(
        String text,
        List<MessageItem> imageItems,
        List<MessageItem> voiceItems,
        List<MessageItem> fileItems,
        List<String> unsupportedTypes) {
        MessageSummary {
            text = text == null ? "" : text.trim();
            imageItems = imageItems == null ? List.of() : List.copyOf(imageItems);
            voiceItems = voiceItems == null ? List.of() : List.copyOf(voiceItems);
            fileItems = fileItems == null ? List.of() : List.copyOf(fileItems);
            unsupportedTypes = unsupportedTypes == null ? List.of() : List.copyOf(unsupportedTypes);
        }

        boolean hasProcessableContent() {
            return !text.isBlank() || !imageItems.isEmpty()
                || !voiceItems.isEmpty() || !fileItems.isEmpty();
        }
    }
}
