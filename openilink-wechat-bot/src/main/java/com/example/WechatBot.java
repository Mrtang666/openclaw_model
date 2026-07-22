package com.example;

import com.example.adapter.wechat.WechatMessageSender;
import com.example.application.ReplyOrchestrator;
import com.example.context.ConversationContextService;
import com.example.feature.file.FileHandler;
import com.example.feature.guidance.GuidedTaskHandler;
import com.example.feature.image.ImageHandler;
import com.example.feature.media.IncomingMediaHandler;
import com.example.feature.route.RouteMapHandler;
import com.example.feature.weather.WeatherHandler;
import com.example.feature.voice.VoiceModeHandler;
import com.example.file.FileDocumentService;
import com.example.file.FileMediaDownloadService;
import com.example.file.FileTaskService;
import com.example.guidance.GuidedConversationService;
import com.example.imagegen.ImageGenService;
import com.example.intent.BotIntent;
import com.example.intent.IntentClassifier;
import com.example.routegen.RouteMapService;
import com.example.speech.SpeechRecognitionService;
import com.example.tts.TextToSpeechService;
import com.example.voice.SilkVoiceService;
import com.example.voice.VoiceProfileService;
import com.example.vision.VisionService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thin application entry point: lifecycle, message demultiplexing, and top-level text routing. */
public class WechatBot {

    private static final Logger log = LoggerFactory.getLogger(WechatBot.class);

    private static final LocalLLMService llmService = new LocalLLMService();
    private static final WeatherService weatherService = new WeatherService();
    private static final VisionService visionService = new VisionService();
    private static final ImageGenService imageGenService = new ImageGenService();
    private static final RouteMapService routeMapService = new RouteMapService();
    private static final SpeechRecognitionService speechRecognitionService = new SpeechRecognitionService();
    private static final TextToSpeechService textToSpeechService = new TextToSpeechService();
    private static final SilkVoiceService silkVoiceService = new SilkVoiceService();
    private static final IntentClassifier intentClassifier = new IntentClassifier();
    private static final ConversationContextService conversationContextService = new ConversationContextService();
    private static final GuidedConversationService guidedConversationService = new GuidedConversationService();
    private static final VoiceProfileService voiceProfileService = new VoiceProfileService();
    private static final FileDocumentService fileDocumentService = new FileDocumentService();
    private static final FileMediaDownloadService fileMediaDownloadService = new FileMediaDownloadService();
    private static final FileTaskService fileTaskService = new FileTaskService();
    private static final ConcurrentHashMap<String, Boolean> voiceReplyModes = new ConcurrentHashMap<>();
    private static final WechatMessageSender sender = new WechatMessageSender();
    private static final ReplyOrchestrator replies = new ReplyOrchestrator(
            sender, textToSpeechService, silkVoiceService, voiceProfileService, voiceReplyModes);
    private static final WeatherHandler weatherHandler = new WeatherHandler(
            weatherService, conversationContextService);
    private static final ImageHandler imageHandler = new ImageHandler(
            imageGenService, sender, replies, guidedConversationService);
    private static final RouteMapHandler routeMapHandler = new RouteMapHandler(
            routeMapService, weatherHandler, sender, replies, conversationContextService);
    private static final FileHandler fileHandler = new FileHandler(
            fileMediaDownloadService, fileDocumentService, fileTaskService,
            llmService, sender, replies);
    private static final IncomingMediaHandler mediaHandler = new IncomingMediaHandler(
            visionService, speechRecognitionService, replies);
    private static final VoiceModeHandler voiceModeHandler = new VoiceModeHandler(
            voiceReplyModes, replies, conversationContextService);
    private static final GuidedTaskHandler guidedTaskHandler = new GuidedTaskHandler(
            weatherHandler, routeMapHandler, imageHandler, replies);

    private static final Pattern IMAGE_GEN_MARKER = Pattern.compile(
            "\\[\\s*IMAGE_GEN\\s*[:：]\\s*(.*?)]", Pattern.DOTALL);
    private static final Pattern LOOSE_IMAGE_GEN_MARKER = Pattern.compile(
            "(?is)(?:\\*+\\s*)?IMAGE_GEN\\s*[:：]\\s*(.+)$");
    private static final Pattern OUTGOING_IMAGE_GEN_MARKER = Pattern.compile(
            "(?is)\\[?\\s*\\**\\s*IMAGE_GEN\\s*[:：].*");
    private static final Pattern REQUEST_SEPARATOR = Pattern.compile(
            "(?:[，,。；;！!？?]\\s*)?(?:然后|接着|另外|还有|顺便|并且|同时|再帮我|再给我|再查|再画|再说|再讲|再|其次|最后)");
    private static final int MAX_SUB_REQUESTS = 5;

    public static void main(String[] args) throws Exception {
        ILinkConfig config = ILinkConfig.builder().heartbeatEnabled(false).build();
        ILinkClient client = ILinkClient.builder().config(config).build();
        String qrUrl = client.executeLogin();
        log.info("请扫码登录微信: {}", qrUrl);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(qrUrl, BarcodeFormat.QR_CODE, 400, 400);
            Path qrFile = Paths.get("wechat_qrcode.png");
            MatrixToImageWriter.writeToPath(matrix, "PNG", qrFile);
            log.info("二维码已保存至: {}", qrFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("生成二维码图片失败: {}", e.getMessage());
        }
        while (!client.isLoggedIn()) Thread.sleep(1000);
        log.info("已连接成功!");

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在停止机器人...");
            stopFlag.set(true);
            client.close();
        }));
        while (!stopFlag.get()) {
            try {
                List<WeixinMessage> messages = client.getUpdates();
                if (messages != null) for (WeixinMessage message : messages) handleMessage(client, message);
            } catch (Exception e) {
                if (!stopFlag.get()) log.warn("处理消息失败: {}", e.getMessage());
            }
        }
    }

    private static void handleMessage(ILinkClient client, WeixinMessage message) {
        String userId = message.getFrom_user_id();
        String messageId = message.getMessage_id() == null ? null : String.valueOf(message.getMessage_id());
        if (message.getItem_list() == null) return;
        try {
            sender.startTyping(client, userId);
            log.debug("已开始显示用户 [{}] 正在输入", userId);
        } catch (Exception e) {
            log.debug("显示正在输入状态失败: {}", e.getMessage());
        }
        try {
            for (MessageItem item : message.getItem_list()) {
                if (item.getType() == 2 && item.getImage_item() != null) {
                    mediaHandler.handleImage(client, item, userId, messageId);
                    return;
                }
                if (item.getType() == 3 && item.getVoice_item() != null) {
                    String text = mediaHandler.transcribeVoice(client, item, userId, messageId);
                    if (text != null && !text.isBlank()) handleTextContent(client, userId, text);
                    return;
                }
                if (item.getType() == 5 && item.getVideo_item() != null) {
                    mediaHandler.handleVideo(client, userId);
                    return;
                }
                if (item.getType() == 4 && item.getFile_item() != null) {
                    fileHandler.receive(client, item, userId);
                    return;
                }
                if (item.getType() == 1 && item.getText_item() != null) {
                    String text = item.getText_item().getText();
                    if (text != null && !text.isBlank()) handleTextContent(client, userId, text);
                    return;
                }
            }
        } finally {
            try {
                sender.stopTyping(client, userId);
                log.debug("已停止显示用户 [{}] 正在输入", userId);
            } catch (Exception e) {
                log.debug("停止正在输入状态失败: {}", e.getMessage());
            }
        }
    }

    private static void handleTextContent(ILinkClient client, String userId, String text) {
        List<String> requests = splitUserRequests(text);
        if (requests.size() > 1) {
            log.info("检测到复合需求: user={}, count={}, requests={}", userId, requests.size(), requests);
            for (String request : requests) handleSingleTextContent(client, userId, request);
            return;
        }
        handleSingleTextContent(client, userId, text);
    }

    private static void handleSingleTextContent(ILinkClient client, String userId, String text) {
        BotIntent rawIntent = intentClassifier.classify(text);
        if (voiceModeHandler.handle(client, userId, text)) {
            log.info("功能路由判断: user={}, intent={}, package={}, text={}",
                    userId, rawIntent.getDescription(), rawIntent.getPackageName(), text);
            return;
        }
        if (voiceProfileService.isAwaitingSelection(userId)
                && !voiceProfileService.shouldContinueSelection(userId, rawIntent)) {
            voiceProfileService.cancelSelection(userId);
        } else {
            VoiceProfileService.SelectionResult selection = voiceProfileService.handle(userId, text);
            if (selection.getAction() != VoiceProfileService.Action.NONE) {
                safeReply(client, userId, selection.getMessage());
                return;
            }
        }
        if (fileHandler.hasPendingGeneration(userId)) {
            if (fileHandler.continuePendingGeneration(client, userId, text)) return;
            fileHandler.cancelPendingGeneration(userId);
        }
        if (!fileHandler.hasPending(userId)
                && fileHandler.continueLatestGeneration(client, userId, text)) {
            return;
        }
        boolean hasFileTask = fileHandler.hasPending(userId)
                || (fileHandler.hasLatest(userId)
                    && (fileHandler.isFileOperationRequest(text)
                        || fileHandler.hasRequestedOperation(userId)
                        && text.trim().matches("(?i)^(pdf|word|docx|excel|xlsx|csv|json|markdown|md|txt)$")));
        if (hasFileTask) {
            if (fileHandler.shouldContinue(userId, rawIntent, text)) {
                FileTaskService.Result result = fileHandler.accept(userId, text);
                if (fileHandler.handleTask(client, userId, result)) return;
            } else if (fileHandler.hasPending(userId)) {
                fileHandler.cancel(userId);
            }
        }
        if (guidedConversationService.hasPending(userId)) {
            boolean continuePending = guidedConversationService.shouldContinuePending(userId, rawIntent, text);
            log.info("任务上下文判断: user={}, pending={}, continue={}, incomingIntent={}, text={}",
                    userId, true, continuePending, rawIntent.getDescription(), text);
            if (continuePending) {
                GuidedConversationService.Result result = guidedConversationService.acceptPending(userId, text);
                if (guidedTaskHandler.handle(client, userId, text, result)) return;
            } else {
                guidedConversationService.cancelPending(userId);
            }
        }

        String routedText = conversationContextService.resolveFollowUp(userId, text);
        BotIntent intent = intentClassifier.classify(routedText);
        log.info("功能路由判断: user={}, intent={}, package={}, text={}",
                userId, intent.getDescription(), intent.getPackageName(),
                text.equals(routedText) ? text : text + " -> " + routedText);
        if (intent == BotIntent.FILE_GENERATION) {
            fileHandler.generateStandalone(client, userId, routedText);
            return;
        }
        if (intent == BotIntent.IMAGE_GENERATION) {
            String prompt = intentClassifier.buildImagePrompt(routedText);
            if (routedText.contains("海报")) {
                GuidedConversationService.Result result = guidedConversationService.startPoster(userId, routedText);
                if (guidedTaskHandler.handle(client, userId, text, result)) return;
            }
            GuidedConversationService.Result result = guidedConversationService.startImage(userId, prompt);
            if (guidedTaskHandler.handle(client, userId, text, result)) return;
            conversationContextService.remember(userId, intent, text, "正在生成图片，请稍候...");
            safeReply(client, userId, "正在生成图片，请稍候...");
            imageHandler.generateAndSend(client, userId, prompt);
            return;
        }
        if (routeMapHandler.isRouteMapRequest(routedText)) {
            GuidedConversationService.Result result = guidedConversationService.startRouteMap(userId, routedText);
            if (guidedTaskHandler.handle(client, userId, text, result)) return;
            routeMapHandler.rememberPendingGeneration(userId, text);
            routeMapHandler.generateAndSend(client, userId, routedText);
            return;
        }
        if (weatherHandler.isWeatherRequest(routedText)) {
            WeatherHandler.Query query = weatherHandler.parse(routedText);
            GuidedConversationService.Result result = guidedConversationService.startWeather(
                    userId, query.getCity(), query.getDayOffset());
            if (guidedTaskHandler.handle(client, userId, text, result)) return;
            safeReply(client, userId, weatherHandler.queryAndRemember(
                    userId, text, result.getCity(), result.getDayOffset()));
            return;
        }

        String reply = llmService.chat(userId, conversationContextService.buildChatInput(userId, routedText));
        conversationContextService.remember(userId, BotIntent.CHAT, text, reply);
        ImageGenRequest imageRequest = extractImageGenRequest(reply);
        if (imageRequest != null && intent != BotIntent.IMAGE_GENERATION) {
            safeReply(client, userId, imageRequest.visibleText.isBlank()
                    ? sanitizeOutgoingText(reply) : imageRequest.visibleText);
            return;
        }
        if (imageRequest != null) {
            safeReply(client, userId, imageRequest.visibleText.isEmpty()
                    ? "正在生成图片，请稍候..." : imageRequest.visibleText);
            imageHandler.generateAndSend(client, userId, imageRequest.prompt);
            return;
        }
        safeReply(client, userId, reply);
    }

    private static ImageGenRequest extractImageGenRequest(String reply) {
        if (reply == null || reply.isBlank()) return null;
        Matcher strict = IMAGE_GEN_MARKER.matcher(reply);
        if (strict.find()) {
            String prompt = cleanupImagePrompt(strict.group(1));
            String visible = sanitizeOutgoingText(reply.replace(strict.group(0), "").trim());
            return prompt.isBlank() ? null : new ImageGenRequest(prompt, visible);
        }
        Matcher loose = LOOSE_IMAGE_GEN_MARKER.matcher(reply);
        if (loose.find()) {
            String prompt = cleanupImagePrompt(loose.group(1));
            String visible = sanitizeOutgoingText(reply.substring(0, loose.start()).trim());
            return prompt.isBlank() ? null : new ImageGenRequest(prompt, visible);
        }
        return null;
    }

    private static String cleanupImagePrompt(String prompt) {
        if (prompt == null) return "";
        return prompt.replaceAll("^\\[+", "")
                .replaceAll("]+$", "")
                .replaceAll("^\\*+", "")
                .replaceAll("\\*+$", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    private static String sanitizeOutgoingText(String text) {
        if (text == null) return "";
        return OUTGOING_IMAGE_GEN_MARKER.matcher(text)
                .replaceAll("")
                .replaceAll("\\*+$", "")
                .trim();
    }

    private static List<String> splitUserRequests(String text) {
        List<String> requests = new ArrayList<>();
        if (text == null || text.isBlank()) return requests;
        Matcher matcher = REQUEST_SEPARATOR.matcher(text);
        int start = 0;
        while (matcher.find() && requests.size() < MAX_SUB_REQUESTS - 1) {
            addRequestPart(requests, text.substring(start, matcher.start()));
            start = matcher.end();
        }
        addRequestPart(requests, text.substring(start));
        if (requests.isEmpty()) requests.add(text.trim());
        return requests;
    }

    private static void addRequestPart(List<String> requests, String part) {
        if (part == null) return;
        String cleaned = part.trim()
                .replaceAll("^[，,。；;！!？?、\\s]+", "")
                .replaceAll("[，,。；;！!？?、\\s]+$", "");
        if (!cleaned.isBlank()) requests.add(cleaned);
    }

    private static void safeReply(ILinkClient client, String userId, String text) {
        replies.reply(client, userId, text);
    }

    private static class ImageGenRequest {
        private final String prompt;
        private final String visibleText;

        private ImageGenRequest(String prompt, String visibleText) {
            this.prompt = prompt;
            this.visibleText = visibleText;
        }
    }
}
