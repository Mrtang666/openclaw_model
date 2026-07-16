package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;

public class GetConfigReq {
    @JsonProperty("ilink_user_id")
    private String ilinkUserId;
    @JsonProperty("context_token")
    private String contextToken;
    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public GetConfigReq() {}

    public String getIlinkUserId() { return ilinkUserId; }
    public void setIlinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; }
    public String getContextToken() { return contextToken; }
    public void setContextToken(String contextToken) { this.contextToken = contextToken; }
    public BaseInfo getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String ilinkUserId;
        private String contextToken;
        private BaseInfo baseInfo;

        public Builder ilinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; return this; }
        public Builder contextToken(String contextToken) { this.contextToken = contextToken; return this; }
        public Builder baseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; return this; }

        public GetConfigReq build() {
            GetConfigReq req = new GetConfigReq();
            req.setIlinkUserId(ilinkUserId);
            req.setContextToken(contextToken);
            req.setBaseInfo(baseInfo);
            return req;
        }
    }
}
