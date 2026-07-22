package com.example.feature.route;

import com.example.adapter.wechat.WechatMessageSender;
import com.example.application.ReplyOrchestrator;
import com.example.context.ConversationContextService;
import com.example.intent.BotIntent;
import com.example.routegen.RouteMapService;
import com.example.feature.weather.WeatherHandler;
import com.github.wechat.ilink.sdk.ILinkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.regex.Pattern;

/** Coordinates structured route planning, optional weather context, rendering, and delivery. */
public class RouteMapHandler {

    private static final Logger log = LoggerFactory.getLogger(RouteMapHandler.class);
    private static final Pattern ROUTE_MAP_KEYWORD = Pattern.compile(
            "路线图|行程图|导览图|攻略图|旅游规划图|旅行规划图|旅游路线|旅行路线|行程规划|旅游规划|旅行规划|三日游|两日游|一日游");
    private static final Pattern ROUTE_MAP_IMAGE_INTENT = Pattern.compile(
            "路线图|行程图|导览图|攻略图|规划图|生成.*图|画.*图|做.*图|出.*图");

    private final RouteMapService routeMapService;
    private final WeatherHandler weatherHandler;
    private final WechatMessageSender sender;
    private final ReplyOrchestrator replies;
    private final ConversationContextService context;

    public RouteMapHandler(RouteMapService routeMapService,
                           WeatherHandler weatherHandler,
                           WechatMessageSender sender,
                           ReplyOrchestrator replies,
                           ConversationContextService context) {
        this.routeMapService = routeMapService;
        this.weatherHandler = weatherHandler;
        this.sender = sender;
        this.replies = replies;
        this.context = context;
    }

    public boolean isRouteMapRequest(String text) {
        return text != null && !text.isBlank()
                && ROUTE_MAP_KEYWORD.matcher(text).find()
                && ROUTE_MAP_IMAGE_INTENT.matcher(text).find();
    }

    public void generateAndSend(ILinkClient client, String userId, String text) {
        log.info("检测到路线图请求: user={}, text={}", userId, text);
        replies.reply(client, userId, "正在生成路线图，请稍候...");

        String weatherContext = weatherHandler.isWeatherRequest(text)
                ? weatherHandler.tryQuery(text) : null;
        if (weatherContext != null && !weatherContext.isBlank()) {
            replies.reply(client, userId, weatherContext);
        }

        RouteMapService.RouteMapResult result = routeMapService.generate(text, weatherContext);
        if (!result.isSuccess()) {
            replies.reply(client, userId, "路线图生成失败: " + result.getMessage());
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(result.getFilePath());
            sender.sendImage(client, userId, imageBytes,
                    result.getFilePath().getFileName().toString(), null);
            log.info("已发送路线图给用户 [{}]: {}", userId, result.getFilePath());
            replies.reply(client, userId, "路线图已经生成并发给你了。");
        } catch (Exception e) {
            log.warn("发送路线图失败: {}", e.getMessage());
            replies.reply(client, userId,
                    "路线图已生成到本地，但发送失败，请查看 downloads/routegen 目录。");
        }
    }

    public void rememberPendingGeneration(String userId, String userText) {
        context.remember(userId, BotIntent.ROUTE_MAP, userText, "正在生成路线图，请稍候...");
    }
}
