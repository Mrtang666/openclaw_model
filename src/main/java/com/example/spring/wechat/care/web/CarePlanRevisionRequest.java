package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.service.CarePlanService;

import java.time.LocalDate;
import java.util.List;

public record CarePlanRevisionRequest(
        String summary,
        String instructions,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String timezone,
        List<CarePlanRequest.TaskTemplateRequest> tasks,
        long version) {

    CarePlanService.RevisionCommand toCommand(String requestId) {
        List<CarePlanService.TaskCommand> commands = tasks == null ? List.of() : tasks.stream()
                .map(CarePlanRequest.TaskTemplateRequest::toCommand)
                .toList();
        return new CarePlanService.RevisionCommand(
                summary, instructions, effectiveFrom, effectiveTo, timezone, commands, version, requestId);
    }
}
