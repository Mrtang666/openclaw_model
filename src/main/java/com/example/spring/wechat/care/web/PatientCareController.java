package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.service.CareMemoryService;
import com.example.spring.wechat.care.service.CarePlanService;
import com.example.spring.wechat.care.service.CareReportService;
import com.example.spring.wechat.care.service.CareTaskService;
import com.example.spring.wechat.care.service.DailyCheckInService;
import com.example.spring.wechat.care.service.HealthRecordService;
import com.example.spring.wechat.care.service.SafetyAlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/care/v1/patient")
public class PatientCareController {

    private final CareApiSupport apiSupport;
    private final CareMemoryService memoryService;
    private final DailyCheckInService checkInService;
    private final HealthRecordService healthRecordService;
    private final SafetyAlertService alertService;
    private final CareReportService reportService;
    private final CarePlanService planService;
    private final CareTaskService taskService;

    public PatientCareController(
            CareApiSupport apiSupport,
            CareMemoryService memoryService,
            DailyCheckInService checkInService,
            HealthRecordService healthRecordService,
            SafetyAlertService alertService,
            CareReportService reportService,
            CarePlanService planService,
            CareTaskService taskService) {
        this.apiSupport = apiSupport;
        this.memoryService = memoryService;
        this.checkInService = checkInService;
        this.healthRecordService = healthRecordService;
        this.alertService = alertService;
        this.reportService = reportService;
        this.planService = planService;
        this.taskService = taskService;
    }

    @GetMapping("/status")
    public CareApiResponse<?> status(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                reportService.status(context.actor(), context.actor().userId(), context.traceId()), context.traceId());
    }

    @PostMapping("/memories")
    public CareApiResponse<?> recordMemory(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody MemoryRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(memoryService.record(context.actor(), new CareMemoryService.RecordCommand(
                request.originalText(), request.normalizedText(), request.occurredAt(), request.peopleJson(),
                request.placeText(), request.visibility(), request.sourceMessageId(), request.idempotencyKey())),
                context.traceId());
    }

    @GetMapping("/memories")
    public CareApiResponse<?> memories(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                memoryService.list(context.actor(), context.actor().userId(), limit, context.traceId()),
                context.traceId());
    }

    @PostMapping("/checkins")
    public CareApiResponse<?> submitCheckIn(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CheckInRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(checkInService.submit(context.actor(), new DailyCheckInService.SubmitCommand(
                request.checkinDate(), request.sleepStatus(), request.mealStatus(), request.hydrationStatus(),
                request.moodStatus(), request.activityStatus(), request.medicationConfirmed(), request.incidentType(),
                request.originalText(), request.idempotencyKey())), context.traceId());
    }

    @GetMapping("/checkins")
    public CareApiResponse<?> checkIns(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(checkInService.list(
                context.actor(), context.actor().userId(), from, to, context.traceId()), context.traceId());
    }

    @GetMapping("/alerts")
    public CareApiResponse<?> alerts(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(alertService.list(
                context.actor(), context.actor().userId(), limit, context.traceId()), context.traceId());
    }

    @PostMapping("/health-records")
    public CareApiResponse<?> recordHealth(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody HealthRecordRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(healthRecordService.record(
                context.actor(), context.actor().userId(), request.toCommand(context.traceId()), "PATIENT_WEB"),
                context.traceId());
    }

    @GetMapping("/health-records")
    public CareApiResponse<?> healthRecords(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(healthRecordService.list(
                context.actor(), context.actor().userId(), limit, context.traceId()), context.traceId());
    }

    @GetMapping("/plans")
    public CareApiResponse<?> plans(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.list(context.actor(), context.actor().userId(), context.traceId()), context.traceId());
    }

    @GetMapping("/plans/{planId}")
    public CareApiResponse<?> plan(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.details(context.actor(), planId, context.traceId()), context.traceId());
    }

    @GetMapping("/tasks")
    public CareApiResponse<?> tasks(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.list(
                context.actor(), context.actor().userId(), from, to, context.traceId()), context.traceId());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public CareApiResponse<?> completeTask(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId,
            @RequestBody TaskActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.complete(context.actor(), taskId,
                new CareTaskService.ActionCommand(request.version(), request.note(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/tasks/{taskId}/late-complete")
    public CareApiResponse<?> lateCompleteTask(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId,
            @RequestBody TaskActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.backfill(context.actor(), taskId,
                new CareTaskService.ActionCommand(request.version(), request.note(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/tasks/{taskId}/missed")
    public CareApiResponse<?> missedTask(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId,
            @RequestBody TaskActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.reportMissed(context.actor(), taskId,
                new CareTaskService.ActionCommand(request.version(), request.note(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/tasks/{taskId}/postpone")
    public CareApiResponse<?> postponeTask(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId,
            @RequestBody TaskPostponeRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.postpone(context.actor(), taskId,
                new CareTaskService.PostponeCommand(
                        request.version(), request.minutes(), request.note(), context.traceId())), context.traceId());
    }

    private Context context(String authorization, String requestId) {
        return new Context(apiSupport.patient(authorization), apiSupport.traceId(requestId));
    }

    private record Context(CareActor actor, String traceId) {
    }

    public record MemoryRequest(
            String originalText,
            String normalizedText,
            Instant occurredAt,
            String peopleJson,
            String placeText,
            String visibility,
            String sourceMessageId,
            String idempotencyKey) {
    }

    public record CheckInRequest(
            LocalDate checkinDate,
            String sleepStatus,
            String mealStatus,
            String hydrationStatus,
            String moodStatus,
            String activityStatus,
            Boolean medicationConfirmed,
            String incidentType,
            String originalText,
            String idempotencyKey) {
    }

    public record HealthRecordRequest(
            String category,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            String unit,
            String recordText,
            Instant occurredAt,
            String idempotencyKey) {
        HealthRecordService.RecordCommand toCommand(String requestId) {
            return new HealthRecordService.RecordCommand(category, primaryValue, secondaryValue, unit,
                    recordText, occurredAt, idempotencyKey, requestId);
        }
    }

    public record TaskActionRequest(long version, String note) {
    }

    public record TaskPostponeRequest(long version, int minutes, String note) {
    }
}
