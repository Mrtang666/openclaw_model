package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CarePlan;
import com.example.spring.wechat.care.model.CarePlanDetails;
import com.example.spring.wechat.care.model.CarePlanStatus;
import com.example.spring.wechat.care.model.CarePlanType;
import com.example.spring.wechat.care.model.CarePlanVersion;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CarePlanRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CarePlanService {

    private final CarePlanRepository planRepository;
    private final CareTaskRepository taskRepository;
    private final CareAuthorizationService authorizationService;
    private final ReminderProperties reminderProperties;
    private final Clock clock;

    public CarePlanService(
            CarePlanRepository planRepository,
            CareTaskRepository taskRepository,
            CareAuthorizationService authorizationService,
            ReminderProperties reminderProperties,
            Clock clock) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.authorizationService = authorizationService;
        this.reminderProperties = reminderProperties;
        this.clock = clock;
    }

    @Transactional
    public CarePlanDetails create(CareActor actor, long patientUserId, CreateCommand command, String requestId) {
        if (actor.role() == MedicalRole.PATIENT) {
            throw new CareException(CareErrorCode.FORBIDDEN, "患者端不能直接制定照护计划");
        }
        authorizationService.require(actor, patientUserId, CarePermissions.PLAN_MANAGE,
                "CREATE_CARE_PLAN", "CARE_PLAN", null, requestId);
        if (command == null) throw invalid("缺少照护计划参数");
        CarePlanType planType = enumValue(CarePlanType.class, command.planType(), "不支持的照护计划类型");
        String title = required(command.title(), "计划标题不能为空", 255);
        Instant now = clock.instant();
        PreparedRevision revision = prepareRevision(
                actor.userId(), command.summary(), command.instructions(), command.effectiveFrom(),
                command.effectiveTo(), command.timezone(), command.tasks(), now);
        String idempotencyKey = idempotency(patientUserId, command.idempotencyKey());
        CarePlan existing = planRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return planRepository.findDetails(existing.id()).orElseThrow();
        CarePlan plan = new CarePlan(
                0L, patientUserId, planType, title, CarePlanStatus.DRAFT, true, 1,
                actor.userId(), null, null, null, "", null, null, idempotencyKey, 0L, now, now);
        return planRepository.create(plan, revision.version(), revision.tasks());
    }

    public List<CarePlan> list(CareActor actor, long patientUserId, String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.PLAN_READ,
                "READ_CARE_PLANS", "CARE_PLAN", null, requestId);
        return planRepository.listByPatient(patientUserId);
    }

    public CarePlanDetails details(CareActor actor, long planId, String requestId) {
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_READ,
                "READ_CARE_PLAN", "CARE_PLAN", Long.toString(planId), requestId);
        return planRepository.findDetails(planId).orElseThrow();
    }

    public CarePlan submit(CareActor actor, long planId, VersionCommand command) {
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_MANAGE,
                "SUBMIT_CARE_PLAN", "CARE_PLAN", Long.toString(planId), requestId(command));
        requireCommand(command);
        if (!planRepository.submit(planId, command.version(), clock.instant())) {
            throw conflict("计划不是草稿状态，或版本已经变化");
        }
        return findPlan(planId);
    }

    public CarePlanDetails revise(CareActor actor, long planId, RevisionCommand command) {
        if (command == null) throw invalid("缺少计划修订参数");
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_MANAGE,
                "REVISE_CARE_PLAN", "CARE_PLAN", Long.toString(planId), command.requestId());
        Instant now = clock.instant();
        PreparedRevision revision = prepareRevision(
                actor.userId(), command.summary(), command.instructions(), command.effectiveFrom(),
                command.effectiveTo(), command.timezone(), command.tasks(), now);
        return planRepository.revise(planId, command.version(), revision.version(), revision.tasks(), now)
                .orElseThrow(() -> conflict("只有草稿计划可以修订，或版本已经变化"));
    }

    public CarePlan review(CareActor actor, long planId, ReviewCommand command) {
        if (command == null) throw invalid("缺少计划审核参数");
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_REVIEW,
                "REVIEW_CARE_PLAN", "CARE_PLAN", Long.toString(planId), command.requestId());
        CarePlanDetails details = planRepository.findDetails(planId).orElseThrow();
        requireReviewer(actor.role(), plan.planType(), details.tasks());
        String decision = clean(command.decision()).toUpperCase(Locale.ROOT);
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw invalid("审核决定必须是 APPROVE 或 REJECT");
        }
        String note = limit(clean(command.note()), 1000);
        if (decision.equals("REJECT") && note.isBlank()) throw invalid("驳回计划时必须填写原因");
        if (!planRepository.review(
                planId, actor.userId(), command.version(), decision.equals("APPROVE"), note, clock.instant())) {
            throw conflict("计划不是待审核状态，或版本已经变化");
        }
        return findPlan(planId);
    }

    public CarePlan activate(CareActor actor, long planId, VersionCommand command) {
        requireClinical(actor);
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_REVIEW,
                "ACTIVATE_CARE_PLAN", "CARE_PLAN", Long.toString(planId), requestId(command));
        requireCommand(command);
        if (!planRepository.activate(planId, command.version(), clock.instant())) {
            throw conflict("计划尚未批准，或版本已经变化");
        }
        return findPlan(planId);
    }

    @Transactional
    public CarePlan pause(CareActor actor, long planId, VersionCommand command) {
        CarePlan plan = manageable(actor, planId, "PAUSE_CARE_PLAN", requestId(command));
        requireCommand(command);
        Instant now = clock.instant();
        if (!planRepository.pause(planId, command.version(), now)) {
            throw conflict("只有执行中的计划可以暂停，或版本已经变化");
        }
        taskRepository.cancelOpenForPlan(planId, actor.userId(), "照护计划已暂停", now);
        return findPlan(planId);
    }

    @Transactional
    public CarePlan resume(CareActor actor, long planId, VersionCommand command) {
        manageable(actor, planId, "RESUME_CARE_PLAN", requestId(command));
        requireCommand(command);
        Instant now = clock.instant();
        if (!planRepository.resume(planId, command.version(), now)) {
            throw conflict("只有暂停的计划可以恢复，或版本已经变化");
        }
        taskRepository.reactivateFutureCancelledForPlan(planId, actor.userId(), now);
        return findPlan(planId);
    }

    @Transactional
    public CarePlan complete(CareActor actor, long planId, VersionCommand command) {
        manageable(actor, planId, "COMPLETE_CARE_PLAN", requestId(command));
        requireCommand(command);
        Instant now = clock.instant();
        if (!planRepository.complete(planId, command.version(), now)) {
            throw conflict("只有执行中或暂停的计划可以完成，或版本已经变化");
        }
        taskRepository.cancelOpenForPlan(planId, actor.userId(), "照护计划已结束", now);
        return findPlan(planId);
    }

    private CarePlan manageable(CareActor actor, long planId, String action, String requestId) {
        CarePlan plan = findPlan(planId);
        authorizationService.require(actor, plan.patientUserId(), CarePermissions.PLAN_MANAGE,
                action, "CARE_PLAN", Long.toString(planId), requestId);
        return plan;
    }

    private CareTaskTemplate validateTask(
            TaskCommand command,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String timezone,
            Instant now,
            int index) {
        if (command == null) throw invalid("任务模板不能为空");
        CareTaskType type = enumValue(CareTaskType.class, command.taskType(), "不支持的照护任务类型");
        CareTaskScheduleType schedule = enumValue(
                CareTaskScheduleType.class, command.scheduleType(), "不支持的任务重复方式");
        LocalTime localTime = command.localTime();
        if (localTime == null) throw invalid("任务提醒时间不能为空");
        LocalDate startDate = command.startDate() == null ? effectiveFrom : command.startDate();
        LocalDate endDate = command.endDate() == null ? effectiveTo : command.endDate();
        if (startDate.isBefore(effectiveFrom)
                || (effectiveTo != null && (endDate == null || endDate.isAfter(effectiveTo)))
                || (endDate != null && endDate.isBefore(startDate))) {
            throw invalid("任务执行日期必须位于计划有效期内");
        }
        LocalDate scheduledDate = command.scheduledDate();
        Integer dayOfWeek = command.dayOfWeek();
        if (schedule == CareTaskScheduleType.ONCE) {
            if (scheduledDate == null || scheduledDate.isBefore(startDate)
                    || (endDate != null && scheduledDate.isAfter(endDate))) {
                throw invalid("一次性任务必须设置计划有效期内的 scheduledDate");
            }
            dayOfWeek = null;
        } else if (schedule == CareTaskScheduleType.WEEKLY) {
            if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                throw invalid("每周任务的 dayOfWeek 必须在 1 至 7 之间");
            }
            scheduledDate = null;
        } else {
            scheduledDate = null;
            dayOfWeek = null;
        }
        int grace = range(command.gracePeriodMinutes(), 60, 0, 1_440, "任务宽限时间不合法");
        int escalation = range(
                command.escalationAfterMinutes(), Math.max(120, grace), grace, 10_080,
                "任务升级提醒时间不合法");
        return new CareTaskTemplate(
                0L, 0L, 0L, 0L, type, required(command.title(), "任务标题不能为空", 255),
                limit(clean(command.instructions()), 4000), schedule, localTime, scheduledDate, dayOfWeek,
                startDate, endDate, grace, escalation, true, index, timezone, now);
    }

    private void requireReviewer(
            MedicalRole role,
            CarePlanType type,
            List<CareTaskTemplate> tasks) {
        boolean containsMedication = tasks.stream()
                .anyMatch(task -> task.taskType() == CareTaskType.MEDICATION_CONFIRMATION);
        boolean containsRehabilitation = tasks.stream()
                .anyMatch(task -> task.taskType() == CareTaskType.REHABILITATION);
        boolean allowed;
        if (type == CarePlanType.MEDICATION || containsMedication) {
            allowed = role == MedicalRole.DOCTOR;
        } else if (type == CarePlanType.NUTRITION) {
            allowed = role == MedicalRole.DOCTOR || role == MedicalRole.DIETITIAN;
        } else if (type == CarePlanType.REHABILITATION || containsRehabilitation) {
            allowed = role == MedicalRole.DOCTOR || role == MedicalRole.THERAPIST;
        } else {
            allowed = role.isClinical();
        }
        if (!allowed) throw new CareException(CareErrorCode.FORBIDDEN, "当前专业角色不能审核此类照护计划");
    }

    private PreparedRevision prepareRevision(
            long actorUserId,
            String summary,
            String instructions,
            LocalDate requestedFrom,
            LocalDate effectiveTo,
            String requestedTimezone,
            List<TaskCommand> requestedTasks,
            Instant now) {
        String timezone = timezone(requestedTimezone);
        ZoneId zone = ZoneId.of(timezone);
        LocalDate effectiveFrom = requestedFrom == null
                ? LocalDate.now(clock.withZone(zone)) : requestedFrom;
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw invalid("计划结束日期不能早于开始日期");
        }
        if (effectiveTo != null && effectiveTo.isAfter(effectiveFrom.plusYears(2))) {
            throw invalid("单个照护计划最长不能超过两年");
        }
        List<TaskCommand> commands = requestedTasks == null ? List.of() : requestedTasks;
        if (commands.isEmpty() || commands.size() > 20) {
            throw invalid("照护计划必须包含 1 至 20 个任务模板");
        }
        List<CareTaskTemplate> tasks = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            tasks.add(validateTask(commands.get(index), effectiveFrom, effectiveTo, timezone, now, index));
        }
        CarePlanVersion version = new CarePlanVersion(
                0L, 0L, 1, limit(clean(summary), 2000), limit(clean(instructions), 20_000),
                effectiveFrom, effectiveTo, timezone, actorUserId, now);
        return new PreparedRevision(version, List.copyOf(tasks));
    }

    private void requireClinical(CareActor actor) {
        if (!actor.role().isClinical()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "只有医护人员可以激活照护计划");
        }
    }

    private CarePlan findPlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "照护计划不存在"));
    }

    private void requireCommand(VersionCommand command) {
        if (command == null) throw invalid("缺少计划版本参数");
    }

    private String requestId(VersionCommand command) {
        return command == null ? "" : command.requestId();
    }

    private String timezone(String value) {
        String timezone = clean(value);
        if (timezone.isBlank()) timezone = reminderProperties.defaultTimezone();
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (Exception exception) {
            throw invalid("无效的时区");
        }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, clean(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(message);
        }
    }

    private int range(Integer value, int fallback, int min, int max, String message) {
        int result = value == null ? fallback : value;
        if (result < min || result > max) throw invalid(message);
        return result;
    }

    private String idempotency(long patientUserId, String value) {
        String key = clean(value);
        if (key.isBlank()) key = java.util.UUID.randomUUID().toString();
        return limit("care-plan:" + patientUserId + ":" + limit(key, 96), 128);
    }

    private String required(String value, String message, int max) {
        String text = clean(value);
        if (text.isBlank()) throw invalid(message);
        return limit(text, max);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private CareException invalid(String message) {
        return new CareException(CareErrorCode.INVALID_ARGUMENT, message);
    }

    private CareException conflict(String message) {
        return new CareException(CareErrorCode.CONFLICT, message);
    }

    public record CreateCommand(
            String planType,
            String title,
            String summary,
            String instructions,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String timezone,
            List<TaskCommand> tasks,
            String idempotencyKey) {
    }

    public record TaskCommand(
            String taskType,
            String title,
            String instructions,
            String scheduleType,
            LocalTime localTime,
            LocalDate scheduledDate,
            Integer dayOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            Integer gracePeriodMinutes,
            Integer escalationAfterMinutes) {
    }

    public record VersionCommand(long version, String requestId) {
    }

    public record ReviewCommand(String decision, String note, long version, String requestId) {
    }

    public record RevisionCommand(
            String summary,
            String instructions,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String timezone,
            List<TaskCommand> tasks,
            long version,
            String requestId) {
    }

    private record PreparedRevision(CarePlanVersion version, List<CareTaskTemplate> tasks) {
    }
}
