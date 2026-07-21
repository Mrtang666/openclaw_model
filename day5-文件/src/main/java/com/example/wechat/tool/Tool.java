package com.example.wechat.tool;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> getParametersSchema();
    String execute(Map<String, Object> params);
}