package com.example.spring.wechat.care.model;

public record CareActor(long userId, String userCode, String displayName, MedicalRole role) {
}
