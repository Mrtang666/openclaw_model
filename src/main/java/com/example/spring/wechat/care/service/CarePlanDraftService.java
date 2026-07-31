package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CarePlan;
import com.example.spring.wechat.care.model.CarePlanDetails;
import com.example.spring.wechat.care.model.CarePlanStatus;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CarePlanDraftService {

    private final MedicalIdentityRepository identityRepository;
    private final CareAuthorizationService authorizationService;
    private final CarePlanService planService;
    private final CareTaskRepository taskRepository;
    private final CareTaskProperties taskProperties;
    private final CarePlanTimeParser timeParser;
    private final CareNotificationRepository notificationRepository;
    private final ObjectProvider<ReminderNotificationSender> notificationSenderProvider;
    private final Clock clock;
    private final Map<String, DraftRecord> drafts = new ConcurrentHashMap<>();

    public CarePlanDraftService(
            MedicalIdentityRepository identityRepository,
            CareAuthorizationService authorizationService,
            CarePlanService planService,
            CareTaskRepository taskRepository,
            CareTaskProperties taskProperties,
            CarePlanTimeParser timeParser,
            CareNotificationRepository notificationRepository,
            ObjectProvider<ReminderNotificationSender> notificationSenderProvider,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.authorizationService = authorizationService;
        this.planService = planService;
        this.taskRepository = taskRepository;
        this.taskProperties = taskProperties;
        this.timeParser = timeParser;
        this.notificationRepository = notificationRepository;
        this.notificationSenderProvider = notificationSenderProvider;
        this.clock = clock;
    }

    public CarePlanDraftDetails createDraft(
            CareActor actor,
            long patientUserId,
            String patientName,
            String patientCode,
            String title,
            String doctorInput,
            String refinedPlan,
            String requestId) {
        requireClinical(actor);
        authorizationService.require(actor, patientUserId, CarePermissions.PLAN_MANAGE,
                "CREATE_PLAN_DRAFT", "CARE_PLAN_DRAFT", null, requestId);
        Instant now = clock.instant();
        DraftRecord record = new DraftRecord(
                UUID.randomUUID().toString(),
                actor.userId(),
                patientUserId,
                clean(patientName),
                clean(patientCode),
                clean(title),
                clean(doctorInput),
                clean(refinedPlan),
                clean(refinedPlan),
                null,
                now,
                now);
        drafts.put(record.id(), record);
        return toDetails(record);
    }

    public List<CarePlanDraftSummary> list(CareActor actor, String requestId) {
        requireClinical(actor);
        Set<Long> allowedPatientIds = new LinkedHashSet<>();
        for (MedicalUser patient : authorizationService.listAccessiblePatients(actor, CarePermissions.PLAN_MANAGE)) {
            allowedPatientIds.add(patient.id());
        }
        return drafts.values().stream()
                .filter(draft -> allowedPatientIds.contains(draft.patientUserId()))
                .sorted(Comparator.comparing(DraftRecord::updatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    public CarePlanDraftDetails get(CareActor actor, String draftId, String requestId) {
        return toDetails(requireDraft(actor, draftId, requestId));
    }

    public CarePlanDraftDetails update(CareActor actor, String draftId, DraftUpdateCommand command) {
        if (command == null) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "缂哄皯鐓ф姢鑽夌淇敼鍙傛暟");
        }
        DraftRecord record = requireDraft(actor, draftId, command.requestId());
        if (record.confirmedAt() != null) {
            throw new CareException(CareErrorCode.CONFLICT, "鏂规宸茶繑鍥炴垨宸叉彁浜わ紝涓嶈兘鍐嶄慨鏀?");
        }
        DraftRecord updated = record.withEdit(command.title(), command.editedPlan(), clock.instant());
        drafts.put(draftId, updated);
        return toDetails(updated);
    }

    public DraftSendResult confirm(CareActor actor, String draftId, String requestId) {
        DraftRecord record = requireDraft(actor, draftId, requestId);
        if (record.confirmedAt() != null) {
            publishPlan(actor, record, firstNonBlank(record.editedPlan(), record.refinedPlan()), requestId);
            return new DraftSendResult(toDetails(record), 0, 0);
        }
        authorizationService.require(actor, record.patientUserId(), CarePermissions.PLAN_MANAGE,
                "CONFIRM_PLAN_DRAFT", "CARE_PLAN_DRAFT", draftId, requestId);
        String finalText = firstNonBlank(record.editedPlan(), record.refinedPlan());
        List<NotificationTarget> targets = identityRepository.listUserNotificationTargets(record.patientUserId());
        if (targets.isEmpty()) {
            throw new CareException(CareErrorCode.CONFLICT, "鎮ｈ€呭綋鍓嶆病鏈夊彲鐢ㄧ殑寰俊鎺ユ敹閫氶亾锛岃璁╂偅鑰呭厛閫氳繃 /patient 鐧诲綍銆?");
        }
        CarePlan activePlan = publishPlan(actor, record, finalText, requestId);
        String content = """
                【新的照护方案】
                医生：%s
                患者：%s（%s）
                计划编号：#%d

                %s
                """.formatted(actor.displayName(), record.patientName(), record.patientCode(), activePlan.id(), finalText).strip();
        int delivered = 0;
        for (NotificationTarget target : targets) {
            if (trySendNow(target, content)) {
                delivered++;
            } else {
                enqueue(record.patientUserId(), target, "CARE_PLAN_TO_PATIENT", content);
            }
        }
        DraftRecord confirmed = record.confirmed(clock.instant(), finalText);
        drafts.put(draftId, confirmed);
        return new DraftSendResult(toDetails(confirmed), delivered, Math.max(0, targets.size() - delivered));
    }

    private CarePlan publishPlan(CareActor actor, DraftRecord record, String finalText, String requestId) {
        CarePlanDetails details = planService.create(actor, record.patientUserId(), new CarePlanService.CreateCommand(
                planType(finalText),
                clean(record.title()).isBlank() ? record.patientName() + "照护方案" : record.title(),
                "由医生审核草稿确认生成。",
                finalText,
                null,
                null,
                null,
                taskCommands(finalText),
                "draft:" + record.id()), requestId);
        CarePlan plan = details.plan();
        if (plan.status() == CarePlanStatus.ACTIVE) {
            return plan;
        }
        if (plan.status() == CarePlanStatus.DRAFT) {
            plan = planService.submit(actor, plan.id(), new CarePlanService.VersionCommand(plan.version(), requestId));
        }
        if (plan.status() == CarePlanStatus.WAITING_REVIEW) {
            plan = planService.review(actor, plan.id(),
                    new CarePlanService.ReviewCommand("APPROVE", "医生在方案审核页确认发送", plan.version(), requestId));
        }
        if (plan.status() == CarePlanStatus.APPROVED) {
            plan = planService.activate(actor, plan.id(), new CarePlanService.VersionCommand(plan.version(), requestId));
        }
        if (plan.status() == CarePlanStatus.ACTIVE) {
            materializeTasks(details.tasks());
        }
        return plan;
    }

    private void materializeTasks(List<CareTaskTemplate> templates) {
        Instant now = clock.instant();
        for (CareTaskTemplate template : templates) {
            ZoneId zone = ZoneId.of(template.timezone());
            LocalDate localToday = ZonedDateTime.ofInstant(now, zone).toLocalDate();
            for (int day = 0; day <= taskProperties.generationHorizonDays(); day++) {
                LocalDate date = localToday.plusDays(day);
                if (!isScheduledFor(template, date)) {
                    continue;
                }
                Instant dueAt = date.atTime(template.localTime()).atZone(zone).toInstant();
                taskRepository.createInstanceIfAbsent(template, date, dueAt, now);
            }
        }
    }

    private boolean isScheduledFor(CareTaskTemplate template, LocalDate date) {
        if (date.isBefore(template.startDate())
                || (template.endDate() != null && date.isAfter(template.endDate()))) {
            return false;
        }
        if (template.scheduleType() == CareTaskScheduleType.ONCE) {
            return date.equals(template.scheduledDate());
        }
        if (template.scheduleType() == CareTaskScheduleType.WEEKLY) {
            return DayOfWeek.of(template.dayOfWeek()) == date.getDayOfWeek();
        }
        return template.scheduleType() == CareTaskScheduleType.DAILY;
    }

    private String planType(String text) {
        if (containsAny(text, "服药", "吃药", "药")) {
            return "MEDICATION";
        }
        if (containsAny(text, "散步", "康复", "训练")) {
            return "REHABILITATION";
        }
        if (containsAny(text, "睡眠", "入睡", "起床")) {
            return "SLEEP_ROUTINE";
        }
        return "DAILY_CHECKIN";
    }

    private List<CarePlanService.TaskCommand> taskCommands(String text) {
        List<CarePlanService.TaskCommand> tasks = new ArrayList<>();
        String source = clean(text);
        if (containsAny(source, "喝水", "饮水")) {
            addHydrationTasks(tasks, source);
        }
        if (containsAny(source, "服药", "吃药", "药")) {
            addMedicationTasks(tasks, source);
        }
        if (containsAny(source, "安全确认", "确认安全", "安全打卡", "打卡")) {
            addSafetyTasks(tasks, source);
        }
        if (containsAny(source, "散步", "康复", "训练", "锻炼", "活动")) {
            addRehabilitationTasks(tasks, source);
        }
        if (containsAny(source, "早餐", "午餐", "晚餐", "饮食", "进食")) {
            addMealTasks(tasks, source);
        }
        if (containsAny(source, "认知训练", "记忆训练", "益智")) {
            addCognitiveTasks(tasks, source);
        }
        if (containsAny(source, "睡眠", "入睡", "起床")) {
            addSleepTasks(tasks, source);
        }
        if (tasks.isEmpty()) {
            for (LocalTime time : timeParser.resolveDailyTimes(source, LocalTime.of(9, 0))) {
                addDailyTask(tasks, "CUSTOM", "照护任务 " + time, source, time, 30, 30);
            }
        }
        return List.copyOf(tasks);
    }

    private void addHydrationTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "喝水", "饮水");
        String instructions = "喝水提醒。医生方案要求：" + frequencyText(scoped, "喝水", "饮水");
        List<LocalTime> explicit = timeParser.extractTimePoints(scoped);
        if (explicit.isEmpty() && containsAny(scoped, "每半小时", "每 30", "每30", "半小时")) {
            for (LocalTime time : List.of(
                    LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(10, 0),
                    LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(16, 0),
                    LocalTime.of(18, 0), LocalTime.of(20, 0))) {
                addDailyTask(tasks, "HYDRATION", "喝水提醒 " + time, instructions, time, 30, 30);
            }
            return;
        }
        for (LocalTime time : timeParser.resolveDailyTimes(scoped, LocalTime.of(10, 0))) {
            addDailyTask(tasks, "HYDRATION", "喝水提醒 " + time, instructions, time, 30, 30);
        }
    }

    private void addMedicationTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "服药", "吃药");
        String instructions = "服药确认。请按医生确认的药物、剂量和注意事项执行。医生方案要求："
                + frequencyText(scoped, "服药", "吃药");
        List<LocalTime> explicit = timeParser.extractTimePoints(scoped);
        if (!explicit.isEmpty()) {
            for (LocalTime time : explicit) {
                addDailyTask(tasks, "MEDICATION_CONFIRMATION", "服药确认 " + time, instructions, time, 30, 30);
            }
            return;
        }
        boolean morning = containsAny(scoped, "早上", "早晨", "上午");
        boolean noon = containsAny(scoped, "中午", "午间");
        boolean evening = containsAny(scoped, "晚上", "晚间", "睡前");
        if (!morning && !noon && !evening) {
            morning = true;
        }
        if (morning) addDailyTask(tasks, "MEDICATION_CONFIRMATION", "早间服药确认", instructions, LocalTime.of(8, 0), 30, 30);
        if (noon) addDailyTask(tasks, "MEDICATION_CONFIRMATION", "午间服药确认", instructions, LocalTime.of(12, 0), 30, 30);
        if (evening) addDailyTask(tasks, "MEDICATION_CONFIRMATION", "晚间服药确认", instructions, LocalTime.of(20, 0), 30, 30);
    }

    private void addSafetyTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "安全确认", "确认安全", "安全打卡", "打卡");
        String instructions = "安全确认打卡。医生方案要求：" + frequencyText(scoped, "安全确认", "确认安全", "打卡");
        for (LocalTime time : timeParser.resolveDailyTimes(scoped, LocalTime.of(9, 0))) {
            addDailyTask(tasks, "DAILY_CHECKIN", "安全确认 " + time, instructions, time, 30, 30);
        }
    }

    private void addRehabilitationTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "散步", "康复", "训练", "锻炼", "活动");
        String instructions = "康复或活动任务。医生方案要求：" + clean(scoped);
        for (LocalTime time : timeParser.resolveDailyTimes(scoped, LocalTime.of(16, 0))) {
            addDailyTask(tasks, "REHABILITATION", "康复训练 " + time, instructions, time, 30, 30);
        }
    }

    private void addMealTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "早餐", "午餐", "晚餐", "饮食", "进食");
        String instructions = "饮食执行确认。医生方案要求：" + clean(scoped);
        List<LocalTime> explicit = timeParser.extractTimePoints(scoped);
        if (explicit.isEmpty()) {
            if (containsAny(scoped, "早餐")) explicit = List.of(LocalTime.of(8, 0));
            else if (containsAny(scoped, "晚餐")) explicit = List.of(LocalTime.of(18, 0));
            else explicit = List.of(LocalTime.of(12, 0));
        }
        for (LocalTime time : explicit) {
            addDailyTask(tasks, "MEAL", "饮食确认 " + time, instructions, time, 30, 30);
        }
    }

    private void addCognitiveTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "认知训练", "记忆训练", "益智");
        for (LocalTime time : timeParser.resolveDailyTimes(scoped, LocalTime.of(10, 0))) {
            addDailyTask(tasks, "COGNITIVE_TRAINING", "认知训练 " + time,
                    "认知训练任务。医生方案要求：" + clean(scoped), time, 30, 30);
        }
    }

    private void addSleepTasks(List<CarePlanService.TaskCommand> tasks, String source) {
        String scoped = relevantText(source, "睡眠", "入睡", "起床");
        for (LocalTime time : timeParser.resolveDailyTimes(scoped, LocalTime.of(21, 0))) {
            addDailyTask(tasks, "SLEEP", "睡眠任务 " + time,
                    "睡眠作息任务。医生方案要求：" + clean(scoped), time, 30, 30);
        }
    }

    private void addDailyTask(
            List<CarePlanService.TaskCommand> tasks,
            String taskType,
            String title,
            String instructions,
            LocalTime localTime,
            int gracePeriodMinutes,
            int escalationAfterMinutes) {
        if (tasks.size() >= 20) {
            return;
        }
        tasks.add(new CarePlanService.TaskCommand(
                taskType,
                title,
                instructions,
                "DAILY",
                localTime,
                null,
                null,
                null,
                null,
                gracePeriodMinutes,
                escalationAfterMinutes));
    }

    private String relevantText(String source, String... anchors) {
        String cleanSource = clean(source);
        String[] clauses = cleanSource.split("[\\r\\n。；;]");
        List<String> matches = new ArrayList<>();
        for (String clause : clauses) {
            if (containsAny(clause, anchors)) {
                matches.add(clean(clause));
            }
        }
        return matches.isEmpty() ? cleanSource : String.join("\n", matches);
    }

    private String frequencyText(String text, String... anchors) {
        if (text == null || text.isBlank()) {
            return "待医生确认";
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (containsAny(line, anchors)) {
                return clean(line);
            }
        }
        return clean(text);
    }

    private boolean trySendNow(NotificationTarget target, String content) {
        ReminderNotificationSender sender = notificationSenderProvider.getIfAvailable();
        if (sender == null) {
            return false;
        }
        try {
            sender.sendText(target.connectionId(), target.recipientId(), content);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void enqueue(long patientUserId, NotificationTarget target, String type, String content) {
        Instant now = clock.instant();
        notificationRepository.enqueue(new MedicalNotification(
                0L, target.userId(), patientUserId, target.connectionId(), target.recipientId(),
                type, "WECHAT", content, "PENDING", now, null, 0,
                3, "", null, idempotency(type, patientUserId, target.userId(), content), now, now));
    }

    private DraftRecord requireDraft(CareActor actor, String draftId, String requestId) {
        requireClinical(actor);
        DraftRecord record = Optional.ofNullable(drafts.get(draftId))
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "未找到待审核方案"));
        authorizationService.require(actor, record.patientUserId(), CarePermissions.PLAN_MANAGE,
                "READ_PLAN_DRAFT", "CARE_PLAN_DRAFT", draftId, requestId);
        return record;
    }

    private void requireClinical(CareActor actor) {
        if (actor == null || !actor.role().isClinical()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "只有医生/医护身份可以处理照护方案");
        }
    }

    private CarePlanDraftSummary toSummary(DraftRecord record) {
        return new CarePlanDraftSummary(
                record.id(),
                record.patientUserId(),
                record.patientName(),
                record.patientCode(),
                record.title(),
                record.confirmedAt() == null ? "待审核" : "已发送",
                record.createdAt(),
                record.updatedAt());
    }

    private CarePlanDraftDetails toDetails(DraftRecord record) {
        return new CarePlanDraftDetails(
                record.id(),
                record.patientUserId(),
                record.patientName(),
                record.patientCode(),
                record.title(),
                record.doctorInput(),
                record.refinedPlan(),
                firstNonBlank(record.editedPlan(), record.refinedPlan()),
                record.confirmedAt() == null ? "待审核" : "已发送",
                record.confirmedAt() == null,
                record.createdAt(),
                record.updatedAt(),
                record.confirmedAt());
    }

    private String idempotency(String type, long patientUserId, long targetUserId, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((type + ":" + patientUserId + ":" + targetUserId + ":" + content)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            return UUID.randomUUID().toString();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }

    private record DraftRecord(
            String id,
            long createdByUserId,
            long patientUserId,
            String patientName,
            String patientCode,
            String title,
            String doctorInput,
            String refinedPlan,
            String editedPlan,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt) {

        DraftRecord withEdit(String newTitle, String newEditedPlan, Instant now) {
            return new DraftRecord(
                    id, createdByUserId, patientUserId, patientName, patientCode,
                    cleanIfBlank(newTitle, title), doctorInput, refinedPlan,
                    cleanIfBlank(newEditedPlan, editedPlan), confirmedAt, createdAt, now);
        }

        DraftRecord confirmed(Instant now, String finalPlan) {
            return new DraftRecord(
                    id, createdByUserId, patientUserId, patientName, patientCode, title,
                    doctorInput, refinedPlan, finalPlan, now, createdAt, now);
        }

        private static String cleanIfBlank(String candidate, String fallback) {
            return candidate == null || candidate.isBlank() ? fallback : candidate.strip();
        }
    }

    public record CarePlanDraftSummary(
            String id,
            long patientUserId,
            String patientName,
            String patientCode,
            String title,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CarePlanDraftDetails(
            String id,
            long patientUserId,
            String patientName,
            String patientCode,
            String title,
            String doctorInput,
            String refinedPlan,
            String editedPlan,
            String status,
            boolean editable,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt) {
    }

    public record DraftUpdateCommand(String title, String editedPlan, String requestId) {
    }

    public record DraftSendResult(CarePlanDraftDetails draft, int deliveredCount, int queuedCount) {
    }
}
