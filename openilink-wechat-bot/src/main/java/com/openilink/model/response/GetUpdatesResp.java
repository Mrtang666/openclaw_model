package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.WeixinMessage;
import java.util.List;

public class GetUpdatesResp {
    private Integer ret;
    @JsonProperty("err_code")
    private Integer errCode;
    @JsonProperty("err_msg")
    private String errMsg;
    @JsonProperty("get_updates_buf")
    private String getUpdatesBuf;
    private List<WeixinMessage> msgs;

    public GetUpdatesResp() {}

    public Integer getRet() { return ret; }
    public void setRet(Integer ret) { this.ret = ret; }
    public Integer getErrCode() { return errCode; }
    public void setErrCode(Integer errCode) { this.errCode = errCode; }
    public String getErrMsg() { return errMsg; }
    public void setErrMsg(String errMsg) { this.errMsg = errMsg; }
    public String getGetUpdatesBuf() { return getUpdatesBuf; }
    public void setGetUpdatesBuf(String getUpdatesBuf) { this.getUpdatesBuf = getUpdatesBuf; }
    public List<WeixinMessage> getMsgs() { return msgs; }
    public void setMsgs(List<WeixinMessage> msgs) { this.msgs = msgs; }
}
