package com.example.spring.wechat.taxi.model;

public record RideAppLink(String appLink, String miniProgramLink, String browserLink) {
    public RideAppLink {
        appLink = appLink == null ? "" : appLink.strip();
        miniProgramLink = miniProgramLink == null ? "" : miniProgramLink.strip();
        browserLink = browserLink == null ? "" : browserLink.strip();
    }
}
