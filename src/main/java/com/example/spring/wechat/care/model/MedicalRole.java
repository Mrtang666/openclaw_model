package com.example.spring.wechat.care.model;

import java.util.Locale;

public enum MedicalRole {
    PATIENT,
    CAREGIVER,
    FAMILY,
    DOCTOR,
    NURSE,
    THERAPIST,
    DIETITIAN,
    ADMIN;

    public static MedicalRole from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的医疗照护角色: " + value);
        }
    }

    public boolean isClinical() {
        return this == DOCTOR || this == NURSE || this == THERAPIST || this == DIETITIAN;
    }

    public boolean isFamily() {
        return this == CAREGIVER || this == FAMILY;
    }
}
