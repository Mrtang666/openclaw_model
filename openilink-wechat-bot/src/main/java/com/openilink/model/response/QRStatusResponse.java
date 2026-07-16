package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QRStatusResponse {
    private String status;
    @JsonProperty("ilink_bot_id")
    private String ilinkBotId;
    @JsonProperty("bot_token")
    private String botToken;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("ilink_user_id")
    private String ilinkUserId;

    public QRStatusResponse() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIlinkBotId() { return ilinkBotId; }
    public void setIlinkBotId(String ilinkBotId) { this.ilinkBotId = ilinkBotId; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getIlinkUserId() { return ilinkUserId; }
    public void setIlinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; }
}
