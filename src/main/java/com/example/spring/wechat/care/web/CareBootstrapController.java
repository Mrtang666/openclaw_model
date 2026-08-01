package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.service.CareSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/care/v1")
public class CareBootstrapController {

    private final CareSessionService sessionService;
    private final CareApiSupport apiSupport;

    public CareBootstrapController(CareSessionService sessionService, CareApiSupport apiSupport) {
        this.sessionService = sessionService;
        this.apiSupport = apiSupport;
    }

    @PostMapping("/bootstrap/users")
    public ResponseEntity<CareApiResponse<CareSessionService.IssuedSession>> bootstrap(
            @RequestHeader(value = "X-Care-Bootstrap-Key", required = false) String bootstrapKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody BootstrapRequest request) {
        String traceId = apiSupport.traceId(requestId);
        CareSessionService.IssuedSession session = sessionService.bootstrap(
                new CareSessionService.BootstrapCommand(
                        request.connectionId(), request.fromUserId(), request.displayName(), request.role()),
                bootstrapKey);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(CareApiResponse.success(session, traceId));
    }

    @GetMapping("/auth/me")
    public CareApiResponse<?> me(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String traceId = apiSupport.traceId(requestId);
        return CareApiResponse.success(apiSupport.authenticated(authorization), traceId);
    }

    @PostMapping("/auth/logout")
    public CareApiResponse<Void> logout(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String traceId = apiSupport.traceId(requestId);
        sessionService.logout(authorization);
        return CareApiResponse.success(null, traceId);
    }

    public record BootstrapRequest(String connectionId, String fromUserId, String displayName, String role) {
    }
}
