package com.example.spring.wechat.netdisk.model;

/**
 * 百度网盘 OAuth 回调处理结果。
 *
 * <p>Controller 会根据这个结果给浏览器页面展示“授权成功/失败”的提示，
 * 后续 pending action 自动恢复也会基于其中的 userId 和 pendingActionId 继续处理。</p>
 */
public record NetdiskAuthCallbackResult(
        boolean success,
        String userId,
        Long pendingActionId,
        String message) {
}
