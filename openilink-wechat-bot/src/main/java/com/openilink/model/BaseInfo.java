package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseInfo {
    @JsonProperty("channel_version")
    private String channelVersion;

    public BaseInfo() {}

    public String getChannelVersion() { return channelVersion; }
    public void setChannelVersion(String channelVersion) { this.channelVersion = channelVersion; }
}
