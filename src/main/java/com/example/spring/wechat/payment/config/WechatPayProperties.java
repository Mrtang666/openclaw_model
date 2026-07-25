package com.example.spring.wechat.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.pay")
public record WechatPayProperties(
        boolean enabled,
        String appId,
        String mchId,
        String apiV3Key,
        String privateKeyPath,
        String merchantSerialNumber,
        String platformCertificatePath,
        String notifyUrl) {
    public WechatPayProperties {
        appId = value(appId); mchId = value(mchId); apiV3Key = value(apiV3Key);
        privateKeyPath = value(privateKeyPath); merchantSerialNumber = value(merchantSerialNumber); platformCertificatePath = value(platformCertificatePath);
        notifyUrl = value(notifyUrl);
    }
    private static String value(String v) { return v == null ? "" : v.strip(); }
}
