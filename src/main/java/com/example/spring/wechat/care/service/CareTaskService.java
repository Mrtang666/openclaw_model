package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareTaskProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class CareTaskService {

    private final CareTaskRepository repository;
    private final CareAuthorizationService authorizationService;
    private final CareTaskProperties properties;
    private final ReminderProperties reminderProperties;
    private final Clock clock;

    public CareTaskService(
            CareTaskRepository repository,
            CareAuthorizationService authorizationService,
            CareTaskProperties properties,
            ReminderProperties reminderProperties,
            Clock clock) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.properties = properties;
        this.reminderProperties = reminderProperties;
        this.clock = clock;
    }

    public List<CareTaskInstance> list(
            CareActor actor,
            long patientUserId,
            LocalDate from,
            LocalDate to,
            String requestId) {
        authorizationService.require(actor, patientUserId, CarePermissions.TASK_READ,
                "READ_CARE_TASKS", "CARE_TASK", null, requestId);
        LocalDate today = LocalDate.now(clock.withZone(
                java.time.ZoneId.of(reminderProperties.defaultTimezone())));
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? start : to;
        if (start.isAfter(end) || end.isAfter(start.plusDays(31))) {
            throw new CareException(CareErrorCode.INVALID_ARGUMENT, "任务查询范围无效或超过 31 天");
        }
        return deduplicate(repository.listByPatient(patientUserId, start, end));
    }

    public CareTaskInstance complete(CareActor actor, long taskId, ActionCommand command) {
        if (command == null) throw invalid("缺少任务完成参数");
        CareTaskInstance task = find(taskId);
        authorizationService.require(actor, task.patientUserId(), CarePermissions.TASK_UPDATE,
                "COMPLETE_CARE_TASK", "CARE_TASK", Long.toString(taskId), command.requestId());
        if (task.status() != com.example.spring.wechat.care.model.CareTaskStatus.PENDING) {
            throw new CareException(CareErrorCode.CONFLICT, "任务已进入补卡窗口，请使用补卡操作");
        }
        if (!repository.complete(
                taskId, actor.userId(), command.version(), limit(command.note(), 1000), clock.instant())) {
            throw conflict();
        }
        return find(taskId);
    }

    public CareTaskInstance backfill(CareActor actor, long taskId, ActionCommand command) {
        if (command == null) throw invalid("缺少任务补卡参数");
        CareTaskInstance task = find(taskId);
        authorizationService.require(actor, task.patientUserId(), CarePermissions.PATIENT_TASK_BACKFILL,
                "BACKFILL_CARE_TASK", "CARE_TASK", Long.toString(taskId), command.requestId());
        if (task.status() != com.example.spring.wechat.care.model.CareTaskStatus.OVERDUE
                && task.status() != com.example.spring.wechat.care.model.CareTaskStatus.MISSED) {
            throw new CareException(CareErrorCode.CONFLICT, "当前任务不在补卡窗口内");
        }
        if (!repository.completeByBackfill(
                taskId, actor.userId(), command.version(), limit(command.note(), 1000), clock.instant())) {
            throw conflict();
        }
        return find(taskId);
    }

    public CareTaskInstance reportMissed(CareActor actor, long taskId, ActionCommand command) {
        if (command == null) throw invalid("缺少任务异常确认参数");
        CareTaskInstance task = find(taskId);
        authorizationService.require(actor, task.patientUserId(), CarePermissions.PATIENT_TASK_BACKFILL,
                "REPORT_CARE_TASK_MISSED", "CARE_TASK", Long.toString(taskId), command.requestId());
        if (task.status() == com.example.spring.wechat.care.model.CareTaskStatus.COMPLETED
                || task.status() == com.example.spring.wechat.care.model.CareTaskStatus.CANCELLED
                || task.status() == com.example.spring.wechat.care.model.CareTaskStatus.SKIPPED
                || task.status() == com.example.spring.wechat.care.model.CareTaskStatus.MISSED) {
            throw new CareException(CareErrorCode.CONFLICT, "当前任务已经关闭，不能重复确认");
        }
        if (!repository.reportMissed(
                taskId, actor.userId(), command.version(), limit(command.note(), 1000), clock.instant())) {
            throw conflict();
        }
        return find(taskId);
    }

    public CareTaskInstance correctMissed(CareActor actor, long taskId, ActionCommand command) {
        if (command == null) throw invalid("缺少临床纠错参数");
        if (actor == null || !actor.role().isClinical()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "只有医护人员可以纠正未完成任务");
        }
        CareTaskInstance task = find(taskId);
        authorizationService.require(actor, task.patientUserId(), CarePermissions.PATIENT_TASK_BACKFILL,
                "CORRECT_MISSED_CARE_TASK", "CARE_TASK", Long.toString(taskId), command.requestId());
        if (!repository.correctMissedByClinical(
                taskId, actor.userId(), command.version(), limit(command.note(), 1000), clock.instant())) {
            throw conflict();
        }
        return find(taskId);
    }

    public CareTaskInstance postpone(CareActor actor, long taskId, PostponeCommand command) {
        if (command == null) throw invalid("缺少任务延后参数");
        if (command.minutes() <= 0 || command.minutes() > properties.maxPostponeMinutes()) {
            throw invalid("延后时间必须在 1 至 " + properties.maxPostponeMinutes() + " 分钟之间");
        }
        CareTaskInstance task = find(taskId);
        authorizationService.require(actor, task.patientUserId(), CarePermissions.TASK_UPDATE,
                "POSTPONE_CARE_TASK", "CARE_TASK", Long.toString(taskId), command.requestId());
        Instant now = clock.instant();
        Instant newDueAt = now.plusSeconds(command.minutes() * 60L);
        if (!repository.postpone(
                taskId, actor.userId(), command.version(), task.dueAt(), newDueAt,
                limit(command.note(), 1000), now)) {
            throw conflict();
        }
        return find(taskId);
    }

    public CareTaskInstance findTask(long taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "照护任务不存在"));
    }

    private CareTaskInstance find(long taskId) {
        return findTask(taskId);
    }

    private String limit(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private CareException invalid(String message) {
        return new CareException(CareErrorCode.INVALID_ARGUMENT, message);
    }

    private CareException conflict() {
        return new CareException(CareErrorCode.CONFLICT, "任务状态已经变化，请刷新后重试");
    }

    private List<CareTaskInstance> deduplicate(List<CareTaskInstance> tasks) {
        LinkedHashMap<String, CareTaskInstance> selected = new LinkedHashMap<>();
        for (CareTaskInstance task : tasks) {
            String key = task.scheduledFor() + "|"
                    + dueMinute(task.dueAt()) + "|"
                    + task.taskType() + "|"
                    + cleanKey(task.title());
            CareTaskInstance existing = selected.get(key);
            if (existing == null || taskPriority(task) < taskPriority(existing)) {
                selected.put(key, task);
            }
        }
        return List.copyOf(selected.values());
    }

    private int taskPriority(CareTaskInstance task) {
        return switch (task.status()) {
            case OVERDUE -> 0;
            case PENDING -> 1;
            case MISSED -> 2;
            case COMPLETED -> 3;
            case SKIPPED -> 4;
            case CANCELLED -> 5;
        };
    }

    private String cleanKey(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String dueMinute(Instant value) {
        return value == null ? "" : value.truncatedTo(ChronoUnit.MINUTES).toString();
    }

    public record ActionCommand(long version, String note, String requestId) {
    }

    public record PostponeCommand(long version, int minutes, String note, String requestId) {
    }
}
