package com.example.spring.wechat.login;

import com.example.spring.wechat.model.WechatLoginState;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/wechat-login")
public class WechatLoginPageController {

    private final WechatLoginPageSessionService sessionService;

    public WechatLoginPageController(WechatLoginPageSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<LoginPageResponse> getSession(@PathVariable String sessionId) {
        WechatLoginPageSession session = sessionService.find(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        WechatLoginState status = sessionService.status(session);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new LoginPageResponse(
                        session.id(),
                        session.matrix(),
                        session.matrixSize(),
                        status.name(),
                        statusMessage(status),
                        session.createdAt()));
    }

    private static String statusMessage(WechatLoginState status) {
        return switch (status) {
            case WAITING -> "请使用微信扫描二维码";
            case SCANNED -> "已扫码，请在微信中确认登录";
            case LOGGED_IN -> "微信登录成功";
            case EXPIRED -> "二维码已过期，请重新启动机器人";
            case ERROR -> "登录状态获取失败，请重新启动机器人";
        };
    }

    public record LoginPageResponse(
            String sessionId,
            List<String> matrix,
            int matrixSize,
            String status,
            String message,
            Instant createdAt) {
    }
}
