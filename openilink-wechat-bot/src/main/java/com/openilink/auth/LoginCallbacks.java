package com.openilink.auth;

public interface LoginCallbacks {
    default void onQRCode(String url) {}
    default void onScanned() {}
    default void onExpired(int attempt, int maxAttempts) {}
}
