package com.example.spring.wechat.voice.style.model;

import java.util.List;

/**
 * TTS 官方音色资料。
 * voice 是调用接口时真正传给 qwen3-tts-flash 的参数，其他字段用于微信端筛选和展示。
 */
public record VoiceProfile(
        String voice,
        String displayName,
        String gender,
        List<String> languages,
        List<String> styles,
        List<String> scenes,
        String description) {

    public VoiceProfile {
        voice = voice == null ? "" : voice.strip();
        displayName = displayName == null ? "" : displayName.strip();
        gender = gender == null ? "" : gender.strip();
        languages = languages == null ? List.of() : List.copyOf(languages);
        styles = styles == null ? List.of() : List.copyOf(styles);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        description = description == null ? "" : description.strip();
    }
}
