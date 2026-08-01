package com.example.spring.wechat.care.model;

import java.time.Instant;
import java.time.LocalDate;

public record CarePlanVersion(
        long id,
        long planId,
        int revision,
        String summary,
        String instructions,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String timezone,
        long authoredByUserId,
        Instant createdAt) {
}
