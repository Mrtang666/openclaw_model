package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.reminder.model.ReminderRepeatType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ReminderScheduleCalculator {

    public Instant nextExecution(Instant scheduledAt, ReminderRepeatType repeatType, ZoneId zoneId, Instant now) {
        if (repeatType == null || repeatType == ReminderRepeatType.ONCE) {
            return null;
        }
        ZonedDateTime next = ZonedDateTime.ofInstant(scheduledAt, zoneId);
        do {
            next = repeatType == ReminderRepeatType.DAILY ? next.plusDays(1) : next.plusWeeks(1);
        } while (!next.toInstant().isAfter(now));
        return next.toInstant();
    }
}
