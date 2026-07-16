package com.openilink.exception;

public class APIError extends ILinkException {
    private final int ret;
    private final int errCode;
    private final String errMsg;

    public APIError(int ret, int errCode, String errMsg) {
        super("API error ret=" + ret + " errCode=" + errCode + " msg=" + errMsg);
        this.ret = ret;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    public int getRet() { return ret; }
    public int getErrCode() { return errCode; }
    public String getErrMsg() { return errMsg; }

    public boolean isSessionExpired() {
        return (ret == 9999 || errCode == 9999 || errCode == 1004 || errCode == 401);
    }
}
