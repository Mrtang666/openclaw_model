package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResult {
    private boolean connected;
    @JsonProperty("bot_token")
    private String botToken;
    @JsonProperty("bot_id")
    private String botId;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("user_id")
    private String userId;
    private String message;

    public LoginResult() {}

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getBotId() { return botId; }
    public void setBotId(String botId) { this.botId = botId; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
