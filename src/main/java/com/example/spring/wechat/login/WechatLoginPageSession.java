package com.example.spring.wechat.login;

import com.example.spring.wechat.model.WechatLoginState;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public record WechatLoginPageSession(
        String id,
        List<String> matrix,
        int matrixSize,
        Instant createdAt,
        Supplier<WechatLoginState> liveStatus) {

    public WechatLoginPageSession {
        matrix = matrix == null ? List.of() : List.copyOf(matrix);
    }
}
