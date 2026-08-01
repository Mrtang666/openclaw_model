package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.service.CarePlanService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CarePlanRequest(
        String planType,
        String title,
        String summary,
        String instructions,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String timezone,
        List<TaskTemplateRequest> tasks,
        String idempotencyKey) {

    CarePlanService.CreateCommand toCommand() {
        List<CarePlanService.TaskCommand> commands = tasks == null ? List.of() : tasks.stream()
                .map(TaskTemplateRequest::toCommand)
                .toList();
        return new CarePlanService.CreateCommand(
                planType, title, summary, instructions, effectiveFrom, effectiveTo, timezone,
                commands, idempotencyKey);
    }

    public record TaskTemplateRequest(
            String taskType,
            String title,
            String instructions,
            String scheduleType,
            LocalTime localTime,
            LocalDate scheduledDate,
            Integer dayOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            Integer followUpAfterMinutes,
            Integer gracePeriodMinutes,
            Integer escalationAfterMinutes) {

        CarePlanService.TaskCommand toCommand() {
            return new CarePlanService.TaskCommand(
                    taskType, title, instructions, scheduleType, localTime, scheduledDate,
                    dayOfWeek, startDate, endDate, followUpAfterMinutes, gracePeriodMinutes, escalationAfterMinutes);
        }
    }
}
