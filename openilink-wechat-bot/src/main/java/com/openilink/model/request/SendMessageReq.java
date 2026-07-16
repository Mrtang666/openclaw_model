package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import com.openilink.model.WeixinMessage;

public class SendMessageReq {
    private WeixinMessage msg;
    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public SendMessageReq() {}

    public WeixinMessage getMsg() { return msg; }
    public void setMsg(WeixinMessage msg) { this.msg = msg; }
    public BaseInfo getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }
}
