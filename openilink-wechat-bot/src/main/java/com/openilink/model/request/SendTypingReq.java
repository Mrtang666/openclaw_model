package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import com.openilink.model.TypingStatus;

public class SendTypingReq {
    @JsonProperty("ilink_user_id")
    private String ilinkUserId;
    @JsonProperty("typing_ticket")
    private String typingTicket;
    private TypingStatus status;
    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public SendTypingReq() {}

    public String getIlinkUserId() { return ilinkUserId; }
    public void setIlinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; }
    public String getTypingTicket() { return typingTicket; }
    public void setTypingTicket(String typingTicket) { this.typingTicket = typingTicket; }
    public TypingStatus getStatus() { return status; }
    public void setStatus(TypingStatus status) { this.status = status; }
    public BaseInfo getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String ilinkUserId;
        private String typingTicket;
        private TypingStatus status;
        private BaseInfo baseInfo;

        public Builder ilinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; return this; }
        public Builder typingTicket(String typingTicket) { this.typingTicket = typingTicket; return this; }
        public Builder status(TypingStatus status) { this.status = status; return this; }
        public Builder baseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; return this; }

        public SendTypingReq build() {
            SendTypingReq req = new SendTypingReq();
            req.setIlinkUserId(ilinkUserId);
            req.setTypingTicket(typingTicket);
            req.setStatus(status);
            req.setBaseInfo(baseInfo);
            return req;
        }
    }
}
