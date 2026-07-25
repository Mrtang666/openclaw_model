package com.example.spring.wechat.netdisk.auth;

import com.example.spring.wechat.netdisk.exception.NetdiskToolException;
import com.example.spring.wechat.netdisk.model.NetdiskAuthCallbackResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 百度网盘 OAuth 回调入口。
 *
 * <p>用户在微信里点击授权链接后，会跳转到百度登录授权页。
 * 百度授权完成后再回调到这里，项目拿到 code 和 state，然后交给授权服务完成 token 落库。</p>
 */
@RestController
@RequestMapping("/api/netdisk/baidu")
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class BaiduNetdiskAuthController {

    private final BaiduNetdiskAuthService authService;

    public BaiduNetdiskAuthController(BaiduNetdiskAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {
        if (error != null && !error.isBlank()) {
            return html("百度网盘授权失败", "百度返回错误：" + error + " " + normalize(errorDescription));
        }
        try {
            NetdiskAuthCallbackResult result = authService.completeAuthorization(state, code);
            String pendingTip = result.pendingActionId() == null
                    ? "现在可以回到微信继续使用网盘功能。"
                    : "系统会继续处理你授权前未完成的网盘任务。";
            return html(result.message(), pendingTip);
        } catch (NetdiskToolException exception) {
            return html("百度网盘授权失败", exception.getMessage());
        }
    }

    private ResponseEntity<String> html(String title, String message) {
        String body = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                </head>
                <body>
                  <h2>%s</h2>
                  <p>%s</p>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(body);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private String escapeHtml(String value) {
        return normalize(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
