package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QRCodeResponse {
    @JsonProperty("qrcode")
    private String qrCode;
    @JsonProperty("qr_code_img_content")
    private String qrCodeImgContent;

    public QRCodeResponse() {}

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public String getQrCodeImgContent() { return qrCodeImgContent; }
    public void setQrCodeImgContent(String qrCodeImgContent) { this.qrCodeImgContent = qrCodeImgContent; }
}
