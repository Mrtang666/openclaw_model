package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImageItem {
    @JsonProperty("image_url")
    private String imageUrl;
    @JsonProperty("aes_key")
    private String aesKey;
    @JsonProperty("file_id")
    private String fileId;
    @JsonProperty("file_md5")
    private String fileMd5;
    private Long height;
    private Long width;

    public ImageItem() {}

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public Long getHeight() { return height; }
    public void setHeight(Long height) { this.height = height; }
    public Long getWidth() { return width; }
    public void setWidth(Long width) { this.width = width; }
}
