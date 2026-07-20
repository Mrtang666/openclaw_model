package com.example.spring.wechat.adapter.ilink;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
import org.springframework.stereotype.Component;
import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.adapter.WechatClientFactory;

@Component
public class IlinkWechatClientFactory implements WechatClientFactory {

    @Override
    public WechatClient create() {
        return new IlinkWechatClient();
    }
}

