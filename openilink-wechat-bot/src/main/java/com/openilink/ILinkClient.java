package com.openilink;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openilink.auth.LoginCallbacks;
import com.openilink.exception.APIError;
import com.openilink.exception.ILinkException;
import com.openilink.exception.NoContextTokenException;
import com.openilink.http.DefaultHttpClient;
import com.openilink.http.HttpDoer;
import com.openilink.model.*;
import com.openilink.model.request.*;
import com.openilink.model.response.*;
import com.openilink.monitor.MessageHandler;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.SleepHelper;
import com.openilink.util.URLHelper;
import com.openilink.util.WechatHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ILinkClient {

    private static final Logger log = LoggerFactory.getLogger(ILinkClient.class);

    public static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    public static final String DEFAULT_CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c";
    public static final String DEFAULT_BOT_TYPE = "3";
    public static final String DEFAULT_VERSION = "1.0.0";

    private static final long LONG_POLL_TIMEOUT_MS = 35_000L;
    private static final long API_TIMEOUT_MS = 15_000L;
    private static final long CONFIG_TIMEOUT_MS = 10_000L;
    private static final long QR_LONG_POLL_TIMEOUT_MS = 35_000L;
    private static final long DEFAULT_LOGIN_TIMEOUT_MS = 8L * 60 * 1000;
    private static final int MAX_QR_REFRESH_COUNT = 3;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long BACKOFF_DELAY_MS = 30_000L;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final long SESSION_EXPIRED_DELAY_MS = 5L * 60 * 1000;

    private volatile String baseUrl;
    private final String cdnBaseUrl;
    private volatile String token;
    private final String botType;
    private final String version;
    private final HttpDoer httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, String> contextTokens = new ConcurrentHashMap<>();

    private ILinkClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.cdnBaseUrl = builder.cdnBaseUrl;
        this.token = builder.token;
        this.botType = builder.botType;
        this.version = builder.version;
        this.httpClient = builder.httpClient;
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Builder builder() { return new Builder(); }

    public static ILinkClient create(String token) { return builder().token(token).build(); }

    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String cdnBaseUrl = DEFAULT_CDN_BASE_URL;
        private String token = "";
        private String botType = DEFAULT_BOT_TYPE;
        private String version = DEFAULT_VERSION;
        private HttpDoer httpClient = new DefaultHttpClient();

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder cdnBaseUrl(String cdnBaseUrl) { this.cdnBaseUrl = cdnBaseUrl; return this; }
        public Builder token(String token) { this.token = token; return this; }
        public Builder botType(String botType) { this.botType = botType; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder httpClient(HttpDoer httpClient) { this.httpClient = httpClient; return this; }
        public ILinkClient build() { return new ILinkClient(this); }
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    private BaseInfo buildBaseInfo() {
        BaseInfo info = new BaseInfo();
        info.setChannelVersion(version);
        return info;
    }

    private Map<String, String> buildHeaders(byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("AuthorizationType", "ilink_bot_token");
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("X-WECHAT-UIN", WechatHelper.randomWechatUIN());
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private byte[] doPost(String endpoint, Object body, long timeoutMs) throws IOException {
        byte[] data = objectMapper.writeValueAsBytes(body);
        String url = URLHelper.joinPath(baseUrl, endpoint);
        Map<String, String> headers = buildHeaders(data);
        return httpClient.doPost(url, data, headers, timeoutMs);
    }

    private byte[] doGet(String rawUrl, Map<String, String> extraHeaders, long timeoutMs) throws IOException {
        return httpClient.doGet(rawUrl, extraHeaders, timeoutMs);
    }

    private static String urlEncode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (UnsupportedEncodingException e) { throw new ILinkException("ilink: url encode failed", e); }
    }

    public GetUpdatesResp getUpdates(String getUpdatesBuf) {
        GetUpdatesReq reqBody = GetUpdatesReq.builder()
                .getUpdatesBuf(getUpdatesBuf)
                .baseInfo(buildBaseInfo())
                .build();
        long timeout = LONG_POLL_TIMEOUT_MS + 5_000L;
        try {
            byte[] data = doPost("ilink/bot/getupdates", reqBody, timeout);
            return objectMapper.readValue(data, GetUpdatesResp.class);
        } catch (Exception e) {
            log.debug("getUpdates timeout or error: {}", e.getMessage());
            GetUpdatesResp resp = new GetUpdatesResp();
            resp.setRet(0);
            resp.setGetUpdatesBuf(getUpdatesBuf);
            return resp;
        }
    }

    public void sendMessage(SendMessageReq msg) {
        msg.setBaseInfo(buildBaseInfo());
        try {
            byte[] resp = doPost("ilink/bot/sendmessage", msg, API_TIMEOUT_MS);
            String respStr = new String(resp, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("sendMessage response: {}", respStr);
        } catch (IOException e) { throw new ILinkException("ilink: sendMessage failed", e); }
    }

    public String sendText(String to, String text, String contextToken) {
        String clientId = "sdk-" + System.currentTimeMillis();
        WeixinMessage msg = new WeixinMessage();
        msg.setToUserId(to);
        msg.setClientId(clientId);
        msg.setContextToken(contextToken);
        MessageItem item = new MessageItem();
        item.setType(MessageItemType.TEXT);
        TextItem textItem = new TextItem();
        textItem.setText(text);
        item.setTextItem(textItem);
        msg.setItemList(Collections.singletonList(item));
        SendMessageReq req = new SendMessageReq();
        req.setMsg(msg);
        sendMessage(req);
        return clientId;
    }

    public GetConfigResp getConfig(String userId, String contextToken) {
        GetConfigReq reqBody = GetConfigReq.builder()
                .ilinkUserId(userId)
                .contextToken(contextToken)
                .baseInfo(buildBaseInfo())
                .build();
        try {
            byte[] data = doPost("ilink/bot/getconfig", reqBody, CONFIG_TIMEOUT_MS);
            return objectMapper.readValue(data, GetConfigResp.class);
        } catch (IOException e) { throw new ILinkException("ilink: getConfig failed", e); }
    }

    public void sendTyping(String userId, String typingTicket, TypingStatus status) {
        SendTypingReq reqBody = SendTypingReq.builder()
                .ilinkUserId(userId)
                .typingTicket(typingTicket)
                .status(status)
                .baseInfo(buildBaseInfo())
                .build();
        try { doPost("ilink/bot/sendtyping", reqBody, CONFIG_TIMEOUT_MS); }
        catch (IOException e) { throw new ILinkException("ilink: sendTyping failed", e); }
    }

    public GetUploadURLResp getUploadUrl(GetUploadURLReq req) {
        req.setBaseInfo(buildBaseInfo());
        try {
            byte[] data = doPost("ilink/bot/getuploadurl", req, API_TIMEOUT_MS);
            return objectMapper.readValue(data, GetUploadURLResp.class);
        } catch (IOException e) { throw new ILinkException("ilink: getUploadUrl failed", e); }
    }

    public void setContextToken(String userId, String ctxToken) { contextTokens.put(userId, ctxToken); }

    public Optional<String> getContextToken(String userId) { return Optional.ofNullable(contextTokens.get(userId)); }

    public String push(String to, String text) {
        String ctxToken = contextTokens.get(to);
        if (ctxToken == null) throw new NoContextTokenException(to);
        return sendText(to, text, ctxToken);
    }

    public QRCodeResponse fetchQRCode() {
        String bt = (botType != null && !botType.isEmpty()) ? botType : DEFAULT_BOT_TYPE;
        String url = URLHelper.joinPath(baseUrl, "ilink/bot/get_bot_qrcode")
                + "?bot_type=" + urlEncode(bt);
        try {
            byte[] data = doGet(url, null, API_TIMEOUT_MS);
            String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("fetchQRCode raw response: {}", raw);
            QRCodeResponse resp = objectMapper.readValue(data, QRCodeResponse.class);
            if (resp.getQrCodeImgContent() == null && raw.contains("http")) {
                int start = raw.indexOf("http");
                int end = raw.indexOf('"', start);
                if (end < 0) end = raw.indexOf('}', start);
                if (end < 0) end = raw.indexOf(',', start);
                if (end < 0) end = raw.length() - 1;
                String extracted = raw.substring(start, end).replace("\\/", "/");
                resp.setQrCodeImgContent(extracted);
            }
            if (resp.getQrCode() == null && raw.contains("\"qrcode\"")) {
                int start = raw.indexOf("\"qrcode\"") + 9;
                start = raw.indexOf('"', start) + 1;
                int end = raw.indexOf('"', start);
                if (start > 0 && end > start) {
                    resp.setQrCode(raw.substring(start, end));
                }
            }
            return resp;
        } catch (IOException e) { throw new ILinkException("ilink: fetch QR code failed", e); }
    }

    public QRStatusResponse pollQRStatus(String qrCode) {
        String url = URLHelper.joinPath(baseUrl, "ilink/bot/get_qrcode_status")
                + "?qrcode=" + urlEncode(qrCode);
        Map<String, String> headers = new HashMap<>();
        headers.put("iLink-App-ClientVersion", "1");
        try {
            byte[] data = doGet(url, headers, QR_LONG_POLL_TIMEOUT_MS + 5_000L);
            return objectMapper.readValue(data, QRStatusResponse.class);
        } catch (Exception e) {
            log.debug("pollQRStatus timeout or error: {}", e.getMessage());
            QRStatusResponse resp = new QRStatusResponse();
            resp.setStatus("wait");
            return resp;
        }
    }

    public LoginResult loginWithQR(LoginCallbacks callbacks) {
        if (callbacks == null) callbacks = new LoginCallbacks() {};
        long deadline = System.currentTimeMillis() + DEFAULT_LOGIN_TIMEOUT_MS;
        QRCodeResponse qr = fetchQRCode();
        String qrcode = qr.getQrCode();
        callbacks.onQRCode(qr.getQrCodeImgContent());
        if (qrcode == null || qrcode.isEmpty()) {
            LoginResult r = new LoginResult();
            r.setMessage("server did not return QR code");
            return r;
        }
        boolean scannedNotified = false;
        int refreshCount = 1;
        String currentQR = qrcode;

        while (System.currentTimeMillis() < deadline) {
            QRStatusResponse status = pollQRStatus(currentQR);
            switch (status.getStatus()) {
                case "wait": break;
                case "scaned":
                    if (!scannedNotified) { scannedNotified = true; callbacks.onScanned(); }
                    break;
                case "expired":
                    refreshCount++;
                    if (refreshCount > MAX_QR_REFRESH_COUNT) {
                        LoginResult r = new LoginResult();
                        r.setMessage("QR code expired too many times");
                        return r;
                    }
                    callbacks.onExpired(refreshCount, MAX_QR_REFRESH_COUNT);
                    QRCodeResponse newQR = fetchQRCode();
                    currentQR = newQR.getQrCode();
                    scannedNotified = false;
                    callbacks.onQRCode(newQR.getQrCodeImgContent());
                    break;
                case "confirmed":
                    if (status.getIlinkBotId() == null || status.getIlinkBotId().isEmpty()) {
                        LoginResult r = new LoginResult();
                        r.setMessage("server did not return bot ID");
                        return r;
                    }
                    setToken(status.getBotToken());
                    if (status.getBaseUrl() != null && !status.getBaseUrl().isEmpty()) {
                        setBaseUrl(status.getBaseUrl());
                    }
                    LoginResult r = new LoginResult();
                    r.setConnected(true);
                    r.setBotToken(status.getBotToken());
                    r.setBotId(status.getIlinkBotId());
                    r.setBaseUrl(status.getBaseUrl());
                    r.setUserId(status.getIlinkUserId());
                    r.setMessage("connected");
                    return r;
                default: log.warn("Unknown QR status: {}", status.getStatus());
            }
            SleepHelper.sleepInterruptibly(1_000L);
        }
        LoginResult r = new LoginResult();
        r.setMessage("login timeout");
        return r;
    }

    public void monitor(MessageHandler handler, MonitorOptions options, AtomicBoolean stopFlag) {
        if (options == null) options = MonitorOptions.builder().build();
        java.util.function.Consumer<Exception> onError = options.getOnError() != null
                ? options.getOnError() : e -> {};
        String buf = options.getInitialBuf() != null ? options.getInitialBuf() : "";
        int failures = 0;

        while (!stopFlag.get()) {
            GetUpdatesResp resp;
            try { resp = getUpdates(buf); }
            catch (Exception e) {
                if (stopFlag.get()) return;
                failures++;
                onError.accept(new ILinkException(
                        String.format("getUpdates (%d/%d): %s", failures, MAX_CONSECUTIVE_FAILURES, e.getMessage()), e));
                if (failures >= MAX_CONSECUTIVE_FAILURES) { failures = 0; if (SleepHelper.sleepInterruptibly(BACKOFF_DELAY_MS)) return; }
                else { if (SleepHelper.sleepInterruptibly(RETRY_DELAY_MS)) return; }
                continue;
            }

            int ret = resp.getRet() != null ? resp.getRet() : 0;
            int errCode = resp.getErrCode() != null ? resp.getErrCode() : 0;

            if (ret != 0 || errCode != 0) {
                APIError apiErr = new APIError(ret, errCode, resp.getErrMsg());
                if (apiErr.isSessionExpired()) {
                    if (options.getOnSessionExpired() != null) options.getOnSessionExpired().run();
                    onError.accept(apiErr);
                    if (SleepHelper.sleepInterruptibly(SESSION_EXPIRED_DELAY_MS)) return;
                    continue;
                }
                failures++;
                onError.accept(new ILinkException(
                        String.format("getUpdates (%d/%d): %s", failures, MAX_CONSECUTIVE_FAILURES, apiErr.getMessage()), apiErr));
                if (failures >= MAX_CONSECUTIVE_FAILURES) { failures = 0; if (SleepHelper.sleepInterruptibly(BACKOFF_DELAY_MS)) return; }
                else { if (SleepHelper.sleepInterruptibly(RETRY_DELAY_MS)) return; }
                continue;
            }

            failures = 0;
            if (resp.getGetUpdatesBuf() != null && !resp.getGetUpdatesBuf().isEmpty()) {
                buf = resp.getGetUpdatesBuf();
                if (options.getOnBufUpdate() != null) options.getOnBufUpdate().accept(buf);
            }
            if (resp.getMsgs() != null) {
                for (WeixinMessage msg : resp.getMsgs()) {
                    if (msg.getContextToken() != null && !msg.getContextToken().isEmpty()
                            && msg.getFromUserId() != null && !msg.getFromUserId().isEmpty()) {
                        setContextToken(msg.getFromUserId(), msg.getContextToken());
                    }
                    handler.handle(msg);
                }
            }
        }
    }
}
