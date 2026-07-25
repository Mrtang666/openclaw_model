package com.example.spring.wechat.netdisk.service;

import com.example.spring.wechat.netdisk.auth.BaiduNetdiskAuthService;
import com.example.spring.wechat.netdisk.auth.NetdiskOAuthClient;
import com.example.spring.wechat.netdisk.auth.NetdiskTokenCryptoService;
import com.example.spring.wechat.netdisk.client.BaiduNetdiskMcpClient;
import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.exception.NetdiskToolException;
import com.example.spring.wechat.netdisk.model.NetdiskAuthPrompt;
import com.example.spring.wechat.netdisk.model.NetdiskAuthorization;
import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;
import com.example.spring.wechat.netdisk.repository.NetdiskAuthorizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 百度网盘业务服务。
 *
 * <p>它把“当前微信用户”转换成“该用户自己的百度网盘 access token”，
 * 再调用 MCP 工具完成搜索、列目录、分享和保存文本。</p>
 */
@Service
public class BaiduNetdiskService implements NetdiskToolService {

    private final BaiduNetdiskAuthService authService;
    private final NetdiskAuthorizationRepository repository;
    private final NetdiskOAuthClient oauthClient;
    private final BaiduNetdiskMcpClient mcpClient;
    private final BaiduNetdiskProperties properties;

    public BaiduNetdiskService(
            BaiduNetdiskAuthService authService,
            NetdiskAuthorizationRepository repository,
            NetdiskOAuthClient oauthClient,
            BaiduNetdiskMcpClient mcpClient,
            BaiduNetdiskProperties properties) {
        this.authService = authService;
        this.repository = repository;
        this.oauthClient = oauthClient;
        this.mcpClient = mcpClient;
        this.properties = properties;
    }

    @Override
    public String auth(String userId, String operation) {
        String op = normalize(operation).isBlank() ? "status" : normalize(operation).toLowerCase();
        if ("status".equals(op)) {
            return authService.bindingStatus(userId);
        }
        if ("bind".equals(op) || "rebind".equals(op)) {
            NetdiskAuthPrompt prompt = authService.startBind(userId, op.toUpperCase(), "", null);
            return "请打开下面的链接授权百度网盘：\n" + prompt.authorizationUrl()
                    + "\n\n授权有效期到：" + prompt.expiresAt();
        }
        return "暂不支持该网盘授权操作：" + op + "。目前支持 status、bind、rebind。";
    }

    @Override
    public String search(String userId, String query, String mode, String dir, int limit) {
        if (normalize(query).isBlank()) {
            return "请告诉我你想在网盘里搜索什么。";
        }
        return withAccessToken(userId, "搜索网盘文件", accessToken -> {
            JsonNode result = "keyword".equalsIgnoreCase(mode)
                    ? mcpClient.keywordSearch(accessToken, query, dir, limit)
                    : mcpClient.semanticSearch(accessToken, query, dir, limit);
            return "百度网盘搜索结果：\n" + mcpClient.pretty(result);
        });
    }

    @Override
    public String list(String userId, String dir, int page) {
        return withAccessToken(userId, "列出网盘目录", accessToken -> {
            JsonNode result = mcpClient.list(accessToken, defaultText(dir, "/"), page);
            return "百度网盘目录列表：\n" + mcpClient.pretty(result);
        });
    }

    @Override
    public String share(String userId, String fsidList, int period, String pwd) {
        if (normalize(fsidList).isBlank()) {
            return "请提供要分享的百度网盘文件 fsid。";
        }
        return withAccessToken(userId, "分享网盘文件", accessToken -> {
            JsonNode result = mcpClient.share(accessToken, fsidList, period, pwd);
            return "百度网盘分享结果：\n" + mcpClient.pretty(result);
        });
    }

    @Override
    public String saveText(String userId, String content, String dir, String filename) {
        if (normalize(content).isBlank()) {
            return "请告诉我需要保存到网盘的文本内容。";
        }
        return withAccessToken(userId, "保存文本到网盘", accessToken -> {
            JsonNode result = mcpClient.uploadText(
                    accessToken,
                    content,
                    defaultText(dir, properties.defaultUploadPath()),
                    defaultText(filename, "openclaw-note.md"));
            return "已提交保存到百度网盘：\n" + mcpClient.pretty(result);
        });
    }

    private String withAccessToken(String userId, String operation, AccessTokenAction action) {
        try {
            Optional<NetdiskAuthorization> optional = authService.findActiveAuthorization(userId);
            if (optional.isEmpty()) {
                NetdiskAuthPrompt prompt = authService.startBind(userId, "BIND", "", null);
                return "执行“" + operation + "”前需要先授权百度网盘。\n请打开链接完成授权：\n"
                        + prompt.authorizationUrl();
            }
            String accessToken = ensureFreshAccessToken(optional.orElseThrow());
            return action.execute(accessToken);
        } catch (NetdiskToolException exception) {
            return "百度网盘工具执行失败：" + exception.getMessage();
        } catch (Exception exception) {
            return "百度网盘工具执行失败：" + rootMessage(exception);
        }
    }

    private String ensureFreshAccessToken(NetdiskAuthorization authorization) {
        NetdiskTokenCryptoService cryptoService = new NetdiskTokenCryptoService(properties.tokenEncryptionKey());
        if (authorization.expiresAt() == null || authorization.expiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return cryptoService.decrypt(authorization.accessTokenEncrypted());
        }
        String refreshToken = cryptoService.decrypt(authorization.refreshTokenEncrypted());
        if (refreshToken.isBlank()) {
            throw new NetdiskToolException("授权已过期且没有 refresh_token，请重新绑定百度网盘");
        }
        NetdiskOAuthToken token = oauthClient.refreshToken(refreshToken);
        Instant now = Instant.now();
        repository.saveOrUpdate(new NetdiskAuthorization(
                authorization.id(),
                authorization.userId(),
                authorization.provider(),
                cryptoService.encrypt(token.accessToken()),
                cryptoService.encrypt(token.refreshToken()),
                token.expiresAt() == null ? now.plusSeconds(Math.max(token.expiresInSeconds(), 0)) : token.expiresAt(),
                token.scope(),
                "ACTIVE",
                authorization.createdAt(),
                now));
        return token.accessToken();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @FunctionalInterface
    private interface AccessTokenAction {
        String execute(String accessToken);
    }
}
