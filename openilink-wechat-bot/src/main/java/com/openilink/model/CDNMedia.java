package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CDNMedia {
    @JsonProperty("aes_key")
    private String aesKey;
    @JsonProperty("file_id")
    private String fileId;
    @JsonProperty("file_md5")
    private String fileMd5;
    @JsonProperty("file_size")
    private Long fileSize;
    @JsonProperty("file_type")
    private Integer fileType;

    public CDNMedia() {}

    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getFileType() { return fileType; }
    public void setFileType(Integer fileType) { this.fileType = fileType; }
}
