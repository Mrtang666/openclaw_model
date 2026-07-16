package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;

public class GetUpdatesReq {
    @JsonProperty("get_updates_buf")
    private String getUpdatesBuf;
    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public GetUpdatesReq() {}

    public String getGetUpdatesBuf() { return getUpdatesBuf; }
    public void setGetUpdatesBuf(String getUpdatesBuf) { this.getUpdatesBuf = getUpdatesBuf; }
    public BaseInfo getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String getUpdatesBuf;
        private BaseInfo baseInfo;

        public Builder getUpdatesBuf(String getUpdatesBuf) { this.getUpdatesBuf = getUpdatesBuf; return this; }
        public Builder baseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; return this; }

        public GetUpdatesReq build() {
            GetUpdatesReq req = new GetUpdatesReq();
            req.setGetUpdatesBuf(getUpdatesBuf);
            req.setBaseInfo(baseInfo);
            return req;
        }
    }
}
