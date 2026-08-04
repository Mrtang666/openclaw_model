package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

@Component
public class ConservativeTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
        return text == null ? 0 : text.length();
    }
}
