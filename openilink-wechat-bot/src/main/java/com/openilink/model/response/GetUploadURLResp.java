package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class GetUploadURLResp {
    private String url;
    @JsonProperty("auth_headers")
    private Map<String, String> authHeaders;
    private Long expiry;

    public GetUploadURLResp() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Map<String, String> getAuthHeaders() { return authHeaders; }
    public void setAuthHeaders(Map<String, String> authHeaders) { this.authHeaders = authHeaders; }
    public Long getExpiry() { return expiry; }
    public void setExpiry(Long expiry) { this.expiry = expiry; }
}
