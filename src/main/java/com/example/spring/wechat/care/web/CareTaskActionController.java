package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.service.CareTaskActionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/care/v1/task-actions")
public class CareTaskActionController {

    private final CareTaskActionService actionService;
    private final CareApiSupport apiSupport;

    public CareTaskActionController(CareTaskActionService actionService, CareApiSupport apiSupport) {
        this.actionService = actionService;
        this.apiSupport = apiSupport;
    }

    @GetMapping("/current")
    public CareApiResponse<?> current(
            @RequestHeader("X-Care-Task-Token") String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String traceId = apiSupport.traceId(requestId);
        return CareApiResponse.success(actionService.current(token, traceId), traceId);
    }

    @PostMapping("/complete")
    public CareApiResponse<?> complete(
            @RequestHeader("X-Care-Task-Token") String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody(required = false) TaskActionRequest request) {
        String traceId = apiSupport.traceId(requestId);
        return CareApiResponse.success(actionService.complete(token,
                request == null ? "" : request.note(), traceId), traceId);
    }

    @PostMapping("/missed")
    public CareApiResponse<?> missed(
            @RequestHeader("X-Care-Task-Token") String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody(required = false) TaskActionRequest request) {
        String traceId = apiSupport.traceId(requestId);
        return CareApiResponse.success(actionService.missed(token,
                request == null ? "" : request.note(), traceId), traceId);
    }

    public record TaskActionRequest(String note) {
    }
}
