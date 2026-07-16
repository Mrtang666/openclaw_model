package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefMessage {
    @JsonProperty("ref_msg_id")
    private String refMsgId;
    @JsonProperty("ref_user_id")
    private String refUserId;

    public RefMessage() {}

    public String getRefMsgId() { return refMsgId; }
    public void setRefMsgId(String refMsgId) { this.refMsgId = refMsgId; }
    public String getRefUserId() { return refUserId; }
    public void setRefUserId(String refUserId) { this.refUserId = refUserId; }
}
