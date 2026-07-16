package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FileItem {
    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("file_size")
    private Long fileSize;
    @JsonProperty("file_md5")
    private String fileMd5;
    @JsonProperty("file_id")
    private String fileId;
    @JsonProperty("aes_key")
    private String aesKey;

    public FileItem() {}

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }
}
