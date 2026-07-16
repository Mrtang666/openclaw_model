package com.openilink.exception;

public class HTTPError extends ILinkException {
    private final int statusCode;
    private final byte[] body;

    public HTTPError(int statusCode, byte[] body) {
        super("HTTP " + statusCode);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public byte[] getBody() { return body; }
}
