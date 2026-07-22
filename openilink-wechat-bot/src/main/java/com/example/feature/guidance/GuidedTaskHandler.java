package com.example.feature.guidance;

import com.example.application.ReplyOrchestrator;
import com.example.feature.image.ImageHandler;
import com.example.feature.route.RouteMapHandler;
import com.example.feature.weather.WeatherHandler;
import com.example.guidance.GuidedConversationService;
import com.github.wechat.ilink.sdk.ILinkClient;

/** Executes completed guided tasks and delegates each result to its feature handler. */
public class GuidedTaskHandler {

    private final WeatherHandler weather;
    private final RouteMapHandler routes;
    private final ImageHandler images;
    private final ReplyOrchestrator replies;
    public GuidedTaskHandler(WeatherHandler weather,
                             RouteMapHandler routes,
                             ImageHandler images,
                             ReplyOrchestrator replies) {
        this.weather = weather;
        this.routes = routes;
        this.images = images;
        this.replies = replies;
    }

    public boolean handle(ILinkClient client, String userId, String userText,
                          GuidedConversationService.Result result) {
        if (result == null || result.getAction() == GuidedConversationService.Action.NONE) {
            return false;
        }
        switch (result.getAction()) {
            case ASK:
            case SATISFIED:
                replies.reply(client, userId, result.getMessage());
                return true;
            case WEATHER_READY:
                replies.reply(client, userId,
                        weather.queryAndRemember(userId, userText,
                                result.getCity(), result.getDayOffset()));
                return true;
            case ROUTE_READY:
                routes.rememberPendingGeneration(userId, userText);
                replies.reply(client, userId, "信息已收齐，正在生成路线图，请稍候...");
                routes.generateAndSend(client, userId, result.getPrompt());
                return true;
            case POSTER_READY:
                replies.reply(client, userId, "信息已收齐，正在生成海报，请稍候...");
                images.generateAndSend(client, userId, result.getPrompt(), true);
                return true;
            case IMAGE_READY:
                replies.reply(client, userId, "信息已收齐，正在生成图片，请稍候...");
                images.generateAndSend(client, userId, result.getPrompt());
                return true;
            default:
                return false;
        }
    }
}
