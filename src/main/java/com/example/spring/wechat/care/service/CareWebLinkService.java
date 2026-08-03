package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CareWebLinkService {

    private final MedicalIdentityRepository identityRepository;
    private final CareSessionService sessionService;
    private final Clock clock;
    private final String configuredBaseUrl;
    private volatile int localServerPort;

    public CareWebLinkService(
            MedicalIdentityRepository identityRepository,
            CareSessionService sessionService,
            Clock clock,
            @Value("${care.web.public-base-url:${wechat.report.public-base-url:${wechat.login-page.base-url:}}}") String configuredBaseUrl) {
        this.identityRepository = identityRepository;
        this.sessionService = sessionService;
        this.clock = clock;
        this.configuredBaseUrl = configuredBaseUrl == null ? "" : configuredBaseUrl.strip();
    }

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        if (event != null && event.getWebServer() != null) {
            localServerPort = event.getWebServer().getPort();
        }
    }

    public CareWebSessionLink createForWechatSession(String sessionKey, String route) {
        return createForWechatSession(sessionKey, route, Map.of());
    }

    public CareWebSessionLink createForWechatSession(String sessionKey, String route, Map<String, String> extraParams) {
        MedicalRole role = identityRepository.findCurrentRoleBySessionKey(sessionKey).orElse(null);
        MedicalUser user = role == null
                ? identityRepository.findUserBySessionKey(sessionKey)
                        .orElseThrow(() -> new CareException(
                                CareErrorCode.UNAUTHORIZED, "当前微信账号尚未完成医疗身份登录"))
                : identityRepository.findUserBySessionKeyAndRole(sessionKey, role)
                        .orElseThrow(() -> new CareException(
                                CareErrorCode.UNAUTHORIZED, "当前微信账号尚未完成医疗身份登录"));
        if (role == null) {
            role = firstActiveRole(user.id());
        }
        CareSessionService.IssuedSession session = sessionService.issue(user, role, clock.instant());
        return new CareWebSessionLink(url(route, session.accessToken(), role, extraParams), session.actor());
    }

    public String url(String route, String accessToken, MedicalRole role) {
        return url(route, accessToken, role, Map.of());
    }

    public String url(String route, String accessToken, MedicalRole role, Map<String, String> extraParams) {
        String normalizedRoute = route == null || route.isBlank() ? "/caregiver/status" : route.strip();
        if (!normalizedRoute.startsWith("/")) {
            normalizedRoute = "/" + normalizedRoute;
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (extraParams != null) {
            extraParams.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    params.put(key, value);
                }
            });
        }
        params.put("token", accessToken);
        params.put("role", role == null ? "" : role.name());
        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/medical-console/")
                .fragment(normalizedRoute + "?" + buildQuery(params))
                .build(true)
                .toUriString();
    }

    /** Builds a short-lived task action URL. The token stays in the URL fragment. */
    public String taskActionUrl(String actionToken) {
        String token = actionToken == null ? "" : actionToken.strip();
        if (token.isBlank()) {
            throw new IllegalArgumentException("task action token cannot be blank");
        }
        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/medical-console/")
                .fragment("/task-action?actionToken=" + encode(token))
                .build(true)
                .toUriString();
    }

    public String baseUrl() {
        if (!configuredBaseUrl.isBlank()) {
            return stripTrailingSlash(configuredBaseUrl);
        }
        int port = localServerPort <= 0 ? 8080 : localServerPort;
        return "http://127.0.0.1:" + port;
    }

    public boolean hasPublicBaseUrl() {
        return !configuredBaseUrl.isBlank();
    }

    private MedicalRole firstActiveRole(long userId) {
        List<MedicalRole> roles = identityRepository.listActiveRoles(userId);
        if (roles.isEmpty()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "当前医疗用户没有有效身份");
        }
        return roles.get(0);
    }

    private String stripTrailingSlash(String value) {
        String text = value == null ? "" : value.strip();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    public record CareWebSessionLink(String url, CareActor actor) {
    }
}
