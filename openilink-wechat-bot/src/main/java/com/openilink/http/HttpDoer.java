package com.openilink.http;

import java.io.IOException;
import java.util.Map;

public interface HttpDoer {
    byte[] doPost(String url, byte[] body, Map<String, String> headers, long timeoutMs) throws IOException;
    byte[] doGet(String url, Map<String, String> headers, long timeoutMs) throws IOException;
}
