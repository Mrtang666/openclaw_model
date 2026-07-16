package com.example.spring.wechat;

import org.springframework.stereotype.Component;

@Component
public class IlinkWechatClientFactory implements WechatClientFactory {

    @Override
    public WechatClient create() {
        return new IlinkWechatClient();
    }
}
