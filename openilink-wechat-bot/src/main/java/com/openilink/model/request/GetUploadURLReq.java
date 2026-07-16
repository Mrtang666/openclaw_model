package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import com.openilink.model.UploadMediaType;

public class GetUploadURLReq {
    @JsonProperty("ilink_user_id")
    private String ilinkUserId;
    @JsonProperty("context_token")
    private String contextToken;
    @JsonProperty("upload_media_type")
    private UploadMediaType uploadMediaType;
    @JsonProperty("file_name")
    private String fileName;
    @JsonProperty("file_md5")
    private String fileMd5;
    @JsonProperty("file_size")
    private Long fileSize;
    @JsonProperty("base_info")
    private BaseInfo baseInfo;

    public GetUploadURLReq() {}

    public String getIlinkUserId() { return ilinkUserId; }
    public void setIlinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; }
    public String getContextToken() { return contextToken; }
    public void setContextToken(String contextToken) { this.contextToken = contextToken; }
    public UploadMediaType getUploadMediaType() { return uploadMediaType; }
    public void setUploadMediaType(UploadMediaType uploadMediaType) { this.uploadMediaType = uploadMediaType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public BaseInfo getBaseInfo() { return baseInfo; }
    public void setBaseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String ilinkUserId;
        private String contextToken;
        private UploadMediaType uploadMediaType;
        private String fileName;
        private String fileMd5;
        private Long fileSize;
        private BaseInfo baseInfo;

        public Builder ilinkUserId(String ilinkUserId) { this.ilinkUserId = ilinkUserId; return this; }
        public Builder contextToken(String contextToken) { this.contextToken = contextToken; return this; }
        public Builder uploadMediaType(UploadMediaType uploadMediaType) { this.uploadMediaType = uploadMediaType; return this; }
        public Builder fileName(String fileName) { this.fileName = fileName; return this; }
        public Builder fileMd5(String fileMd5) { this.fileMd5 = fileMd5; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder baseInfo(BaseInfo baseInfo) { this.baseInfo = baseInfo; return this; }

        public GetUploadURLReq build() {
            GetUploadURLReq req = new GetUploadURLReq();
            req.setIlinkUserId(ilinkUserId);
            req.setContextToken(contextToken);
            req.setUploadMediaType(uploadMediaType);
            req.setFileName(fileName);
            req.setFileMd5(fileMd5);
            req.setFileSize(fileSize);
            req.setBaseInfo(baseInfo);
            return req;
        }
    }
}
