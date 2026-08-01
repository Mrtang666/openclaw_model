package com.example.spring.wechat.care.model;

import java.util.List;

public record CarePlanDetails(
        CarePlan plan,
        CarePlanVersion version,
        List<CareTaskTemplate> tasks) {
}
