package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VoiceItem {
    @JsonProperty("voice_url")
    private String voiceUrl;
    @JsonProperty("aes_key")
    private String aesKey;
    @JsonProperty("file_id")
    private String fileId;
    @JsonProperty("file_md5")
    private String fileMd5;
    @JsonProperty("voice_size")
    private Long voiceSize;
    @JsonProperty("voice_play_length")
    private Long voicePlayLength;
    @JsonProperty("voice_text")
    private String voiceText;

    public VoiceItem() {}

    public String getVoiceUrl() { return voiceUrl; }
    public void setVoiceUrl(String voiceUrl) { this.voiceUrl = voiceUrl; }
    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public Long getVoiceSize() { return voiceSize; }
    public void setVoiceSize(Long voiceSize) { this.voiceSize = voiceSize; }
    public Long getVoicePlayLength() { return voicePlayLength; }
    public void setVoicePlayLength(Long voicePlayLength) { this.voicePlayLength = voicePlayLength; }
    public String getVoiceText() { return voiceText; }
    public void setVoiceText(String voiceText) { this.voiceText = voiceText; }
}
