package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.service.CareAuthorizationService;
import com.example.spring.wechat.care.service.CareMemoryService;
import com.example.spring.wechat.care.service.CarePlanDraftService;
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

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;

@RestController
@RequestMapping("/api/care/v1/clinical")
public class ClinicalCareController {

    private final CareApiSupport apiSupport;
    private final CareAuthorizationService authorizationService;
    private final CareReportService reportService;
    private final CareMemoryService memoryService;
    private final DailyCheckInService checkInService;
    private final HealthRecordService healthRecordService;
    private final SafetyAlertService alertService;
    private final CarePlanService planService;
    private final CarePlanDraftService draftService;
    private final CareTaskService taskService;

    public ClinicalCareController(
            CareApiSupport apiSupport,
            CareAuthorizationService authorizationService,
            CareReportService reportService,
            CareMemoryService memoryService,
            DailyCheckInService checkInService,
            HealthRecordService healthRecordService,
            SafetyAlertService alertService,
            CarePlanService planService,
            CarePlanDraftService draftService,
            CareTaskService taskService) {
        this.apiSupport = apiSupport;
        this.authorizationService = authorizationService;
        this.reportService = reportService;
        this.memoryService = memoryService;
        this.checkInService = checkInService;
        this.healthRecordService = healthRecordService;
        this.alertService = alertService;
        this.planService = planService;
        this.draftService = draftService;
        this.taskService = taskService;
    }

    @PostMapping("/bindings")
    public CareApiResponse<?> bindPatient(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PatientBindRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(authorizationService.bindPatientForViewer(context.actor(),
                new CareAuthorizationService.ViewerBindCommand(
                        request.patientUserCode(), request.relationLabel(), request.permissions(),
                        request.expiresAt(), context.traceId())), context.traceId());
    }

    @PostMapping("/patients/{patientId}/doctor-transfer")
    public CareApiResponse<?> transferDoctor(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestBody DoctorTransferRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(authorizationService.transferDoctor(context.actor(), patientId,
                new CareAuthorizationService.DoctorTransferCommand(
                        request.targetDoctorUserCode(), request.relationLabel(), request.expiresAt(),
                        context.traceId())), context.traceId());
    }

    @PostMapping("/patients/{patientId}/unbind")
    public CareApiResponse<?> unbindPatient(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                authorizationService.unbindPatientForViewer(context.actor(), patientId, context.traceId()),
                context.traceId());
    }

    @GetMapping("/patients")
    public CareApiResponse<?> patients(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(reportService.listPatientOverviews(context.actor()), context.traceId());
    }

    @GetMapping("/patients/{patientId}/status")
    public CareApiResponse<?> status(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(reportService.status(context.actor(), patientId, context.traceId()), context.traceId());
    }

    @GetMapping("/patients/{patientId}/memories")
    public CareApiResponse<?> memories(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId, @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(memoryService.list(context.actor(), patientId, limit, context.traceId()), context.traceId());
    }

    @PatchMapping("/memories/{memoryId}/confirmation")
    public CareApiResponse<?> confirmMemory(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long memoryId, @RequestBody MemoryConfirmationRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(memoryService.confirm(context.actor(), memoryId,
                new CareMemoryService.ConfirmCommand(
                        request.status(), request.correctedText(), request.version(), context.traceId())), context.traceId());
    }

    @GetMapping("/patients/{patientId}/checkins")
    public CareApiResponse<?> checkIns(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(checkInService.list(context.actor(), patientId, from, to, context.traceId()), context.traceId());
    }

    @GetMapping("/patients/{patientId}/alerts")
    public CareApiResponse<?> alerts(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId, @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(alertService.list(context.actor(), patientId, limit, context.traceId()), context.traceId());
    }

    @PostMapping("/patients/{patientId}/health-records")
    public CareApiResponse<?> recordHealth(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestBody HealthRecordRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(healthRecordService.record(
                context.actor(), patientId, request.toCommand(context.traceId()), "CLINICAL_WEB"), context.traceId());
    }

    @GetMapping("/patients/{patientId}/health-records")
    public CareApiResponse<?> healthRecords(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestParam(defaultValue = "50") int limit) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(healthRecordService.list(
                context.actor(), patientId, limit, context.traceId()), context.traceId());
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public CareApiResponse<?> acknowledge(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long alertId, @RequestBody AlertActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(alertService.acknowledge(context.actor(), alertId,
                new SafetyAlertService.ActionCommand(request.version(), request.note(), context.traceId())), context.traceId());
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public CareApiResponse<?> resolve(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long alertId, @RequestBody AlertResolveRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(alertService.resolve(context.actor(), alertId,
                new SafetyAlertService.ResolveCommand(
                        request.version(), request.falseAlarm(), request.note(), context.traceId())), context.traceId());
    }

    @GetMapping("/patients/{patientId}/plans")
    public CareApiResponse<?> plans(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.list(context.actor(), patientId, context.traceId()), context.traceId());
    }

    @GetMapping("/plans/{planId}")
    public CareApiResponse<?> plan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.details(context.actor(), planId, context.traceId()), context.traceId());
    }

    @GetMapping("/plan-drafts")
    public CareApiResponse<?> planDrafts(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(draftService.list(context.actor(), context.traceId()), context.traceId());
    }

    @GetMapping("/plan-drafts/{draftId}")
    public CareApiResponse<?> planDraft(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String draftId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(draftService.get(context.actor(), draftId, context.traceId()), context.traceId());
    }

    @PatchMapping("/plan-drafts/{draftId}")
    public CareApiResponse<?> updatePlanDraft(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String draftId,
            @RequestBody PlanDraftUpdateRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(draftService.update(context.actor(), draftId,
                new CarePlanDraftService.DraftUpdateCommand(request.title(), request.editedPlan(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/plan-drafts/{draftId}/confirm")
    public CareApiResponse<?> confirmPlanDraft(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String draftId) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(draftService.confirm(context.actor(), draftId, context.traceId()),
                context.traceId());
    }

    @PostMapping("/patients/{patientId}/plans")
    public CareApiResponse<?> createPlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestBody CarePlanRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.create(context.actor(), patientId, request.toCommand(), context.traceId()),
                context.traceId());
    }

    @PostMapping("/plans/{planId}/submit")
    public CareApiResponse<?> submitPlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId,
            @RequestBody PlanVersionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.submit(context.actor(), planId,
                new CarePlanService.VersionCommand(request.version(), context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/revisions")
    public CareApiResponse<?> revisePlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId,
            @RequestBody CarePlanRevisionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                planService.revise(context.actor(), planId, request.toCommand(context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/review")
    public CareApiResponse<?> reviewPlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId,
            @RequestBody PlanReviewRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.review(context.actor(), planId,
                new CarePlanService.ReviewCommand(
                        request.decision(), request.note(), request.version(), context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/activate")
    public CareApiResponse<?> activatePlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId, @RequestBody PlanVersionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.activate(context.actor(), planId,
                version(request, context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/pause")
    public CareApiResponse<?> pausePlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId, @RequestBody PlanVersionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.pause(context.actor(), planId,
                version(request, context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/resume")
    public CareApiResponse<?> resumePlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId, @RequestBody PlanVersionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.resume(context.actor(), planId,
                version(request, context.traceId())), context.traceId());
    }

    @PostMapping("/plans/{planId}/complete")
    public CareApiResponse<?> completePlan(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long planId, @RequestBody PlanVersionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(planService.complete(context.actor(), planId,
                version(request, context.traceId())), context.traceId());
    }

    @GetMapping("/patients/{patientId}/tasks")
    public CareApiResponse<?> tasks(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long patientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(
                taskService.list(context.actor(), patientId, from, to, context.traceId()), context.traceId());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public CareApiResponse<?> completeTask(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId, @RequestBody TaskActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.complete(context.actor(), taskId,
                new CareTaskService.ActionCommand(request.version(), request.note(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/tasks/{taskId}/correct-missed")
    public CareApiResponse<?> correctMissedTask(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId,
            @RequestBody TaskActionRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.correctMissed(context.actor(), taskId,
                new CareTaskService.ActionCommand(request.version(), request.note(), context.traceId())),
                context.traceId());
    }

    @PostMapping("/tasks/{taskId}/postpone")
    public CareApiResponse<?> postponeTask(@RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long taskId, @RequestBody TaskPostponeRequest request) {
        Context context = context(authorization, requestId);
        return CareApiResponse.success(taskService.postpone(context.actor(), taskId,
                new CareTaskService.PostponeCommand(
                        request.version(), request.minutes(), request.note(), context.traceId())), context.traceId());
    }

    private CarePlanService.VersionCommand version(PlanVersionRequest request, String traceId) {
        return new CarePlanService.VersionCommand(request.version(), traceId);
    }

    private Context context(String authorization, String requestId) {
        return new Context(apiSupport.clinical(authorization), apiSupport.traceId(requestId));
    }

    private record Context(CareActor actor, String traceId) {
    }

    public record MemoryConfirmationRequest(String status, String correctedText, long version) {
    }

    public record AlertActionRequest(long version, String note) {
    }

    public record AlertResolveRequest(long version, boolean falseAlarm, String note) {
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

    public record PlanVersionRequest(long version) {
    }

    public record PlanReviewRequest(String decision, String note, long version) {
    }

    public record TaskActionRequest(long version, String note) {
    }

    public record TaskPostponeRequest(long version, int minutes, String note) {
    }

    public record PatientBindRequest(
            String patientUserCode,
            String relationLabel,
            Set<String> permissions,
            Instant expiresAt) {
    }

    public record DoctorTransferRequest(
            String targetDoctorUserCode,
            String relationLabel,
            Instant expiresAt) {
    }

    public record PlanDraftUpdateRequest(String title, String editedPlan) {
    }
}
