package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GetConfigResp {
    @JsonProperty("context_token")
    private String contextToken;

    public GetConfigResp() {}

    public String getContextToken() { return contextToken; }
    public void setContextToken(String contextToken) { this.contextToken = contextToken; }
}
