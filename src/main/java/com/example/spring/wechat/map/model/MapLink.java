package com.example.spring.wechat.map.model;

public record MapLink(String label, String url) {

    public MapLink {
        label = label == null ? "" : label.strip();
        url = url == null ? "" : url.strip();
    }
}
