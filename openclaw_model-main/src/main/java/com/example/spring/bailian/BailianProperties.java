package com.example.spring.bailian;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bailian")
public class BailianProperties {
    private String apiKey = "";
    private String compatibleBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String chatModel = "qwen-plus";
    private String visionModel = "qwen3-vl-plus";
    private String imageModel = "wanx-v1";
    private String imageEditModel = "qwen-image-2.0";
    private String imageEditUrl = "";
    private String imageSynthesisUrl =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private String taskUrl = "https://dashscope.aliyuncs.com/api/v1/tasks";
    private String imageSize = "1024*1024";
    private String systemPrompt =
        "你是一个通过微信与用户交流的智能助手。请理解用户发来的全部信息，并使用简洁、准确、自然的中文回答。"
            + "用户使用“这段、这个、上面、它”等指代时，应结合最近对话历史理解。"
            + "如果历史中没有被引用内容，应请用户重新发送，不得假装已经看到。"
            + "如果当前调用没有返回真实图片，不得声称图片已经生成、制作或发送。";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(90);
    private Duration imageTimeout = Duration.ofMinutes(3);
    private Duration imagePollInterval = Duration.ofSeconds(2);

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCompatibleBaseUrl() {
        return compatibleBaseUrl;
    }

    public void setCompatibleBaseUrl(String compatibleBaseUrl) {
        this.compatibleBaseUrl = compatibleBaseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getImageEditModel() {
        return imageEditModel;
    }

    public void setImageEditModel(String imageEditModel) {
        this.imageEditModel = imageEditModel;
    }

    public String getImageEditUrl() {
        return imageEditUrl;
    }

    public void setImageEditUrl(String imageEditUrl) {
        this.imageEditUrl = imageEditUrl;
    }

    public String getImageSynthesisUrl() {
        return imageSynthesisUrl;
    }

    public void setImageSynthesisUrl(String imageSynthesisUrl) {
        this.imageSynthesisUrl = imageSynthesisUrl;
    }

    public String getTaskUrl() {
        return taskUrl;
    }

    public void setTaskUrl(String taskUrl) {
        this.taskUrl = taskUrl;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getImageTimeout() {
        return imageTimeout;
    }

    public void setImageTimeout(Duration imageTimeout) {
        this.imageTimeout = imageTimeout;
    }

    public Duration getImagePollInterval() {
        return imagePollInterval;
    }

    public void setImagePollInterval(Duration imagePollInterval) {
        this.imagePollInterval = imagePollInterval;
    }

}
