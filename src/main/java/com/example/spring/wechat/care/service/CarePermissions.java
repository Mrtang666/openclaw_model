package com.example.spring.wechat.care.service;

import java.util.Set;

public final class CarePermissions {

    public static final String STATUS_READ = "PATIENT_STATUS_READ";
    public static final String MEMORY_READ = "PATIENT_MEMORY_READ";
    public static final String MEMORY_CONFIRM = "PATIENT_MEMORY_CONFIRM";
    public static final String CHECKIN_READ = "PATIENT_CHECKIN_READ";
    public static final String ALERT_READ = "PATIENT_ALERT_READ";
    public static final String ALERT_ACK = "PATIENT_ALERT_ACK";
    public static final String REPORT_READ = "PATIENT_REPORT_READ";
    public static final String PLAN_READ = "PATIENT_PLAN_READ";
    public static final String PLAN_MANAGE = "PATIENT_PLAN_MANAGE";
    public static final String PLAN_REVIEW = "PATIENT_PLAN_REVIEW";
    public static final String TASK_READ = "PATIENT_TASK_READ";
    public static final String TASK_UPDATE = "PATIENT_TASK_UPDATE";
    public static final String PATIENT_TASK_BACKFILL = "PATIENT_TASK_BACKFILL";

    public static final Set<String> ALL = Set.of(
            STATUS_READ, MEMORY_READ, MEMORY_CONFIRM, CHECKIN_READ, ALERT_READ, ALERT_ACK, REPORT_READ,
            PLAN_READ, PLAN_MANAGE, PLAN_REVIEW, TASK_READ, TASK_UPDATE, PATIENT_TASK_BACKFILL);

    private CarePermissions() {
    }
}
