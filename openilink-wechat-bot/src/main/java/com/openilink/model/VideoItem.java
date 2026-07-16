package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VideoItem {
    @JsonProperty("video_url")
    private String videoUrl;
    @JsonProperty("aes_key")
    private String aesKey;
    @JsonProperty("file_id")
    private String fileId;
    @JsonProperty("file_md5")
    private String fileMd5;
    @JsonProperty("video_size")
    private Long videoSize;
    @JsonProperty("video_play_length")
    private Long videoPlayLength;

    public VideoItem() {}

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public Long getVideoSize() { return videoSize; }
    public void setVideoSize(Long videoSize) { this.videoSize = videoSize; }
    public Long getVideoPlayLength() { return videoPlayLength; }
    public void setVideoPlayLength(Long videoPlayLength) { this.videoPlayLength = videoPlayLength; }
}
