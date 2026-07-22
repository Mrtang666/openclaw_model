package com.example.spring.wechat.login;

import com.example.spring.wechat.model.WechatLoginState;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class WechatLoginPageSessionService {

    private final WechatLoginPageProperties properties;
    private final Map<String, WechatLoginPageSession> sessions = new ConcurrentHashMap<>();

    public WechatLoginPageSessionService(WechatLoginPageProperties properties) {
        this.properties = properties;
    }

    public WechatLoginPageSession create(String loginUrl, Supplier<WechatLoginState> liveStatus) {
        validateLoginUrl(loginUrl);
        purgeStaleSessions();
        List<String> matrix = encode(loginUrl);
        WechatLoginPageSession session = new WechatLoginPageSession(
                UUID.randomUUID().toString(),
                matrix,
                matrix.size(),
                Instant.now(),
                liveStatus == null ? () -> WechatLoginState.WAITING : liveStatus);
        sessions.put(session.id(), session);
        return session;
    }

    public WechatLoginPageSession find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return sessions.get(id.strip());
    }

    public WechatLoginState status(WechatLoginPageSession session) {
        if (session == null) {
            return WechatLoginState.ERROR;
        }
        if (isExpired(session)) {
            return WechatLoginState.EXPIRED;
        }
        try {
            WechatLoginState status = session.liveStatus().get();
            return status == null ? WechatLoginState.WAITING : status;
        } catch (RuntimeException exception) {
            return WechatLoginState.ERROR;
        }
    }

    private boolean isExpired(WechatLoginPageSession session) {
        return session.createdAt().plus(properties.getSessionTtl()).isBefore(Instant.now());
    }

    private void purgeStaleSessions() {
        Duration retention = properties.getSessionTtl().multipliedBy(2);
        Instant cutoff = Instant.now().minus(retention);
        sessions.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private static List<String> encode(String loginUrl) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 4);
            BitMatrix bitMatrix = new QRCodeWriter().encode(loginUrl, BarcodeFormat.QR_CODE, 0, 0, hints);
            List<String> rows = new ArrayList<>(bitMatrix.getHeight());
            for (int y = 0; y < bitMatrix.getHeight(); y++) {
                StringBuilder row = new StringBuilder(bitMatrix.getWidth());
                for (int x = 0; x < bitMatrix.getWidth(); x++) {
                    row.append(bitMatrix.get(x, y) ? '1' : '0');
                }
                rows.add(row.toString());
            }
            return rows;
        } catch (WriterException exception) {
            throw new IllegalStateException("无法生成微信登录二维码矩阵", exception);
        }
    }

    private static void validateLoginUrl(String loginUrl) {
        URI uri;
        try {
            uri = URI.create(loginUrl == null ? "" : loginUrl.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("微信登录地址无效", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"liteapp.weixin.qq.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("微信登录地址必须来自 liteapp.weixin.qq.com");
        }
    }
}
