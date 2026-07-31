package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.config.CareProperties;
import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles the short, deterministic replies sent in response to a task reminder. */
@Service
public class CareTaskInteractionService {

    private static final Pattern TASK_ID = Pattern.compile("(?:任务\\s*)?#\\s*(\\d+)|任务\\s*(\\d+)");
    private static final Pattern COMPLETED = Pattern.compile("(?:已?完成|完成了|已做好|做好了)");
    private static final Pattern INCOMPLETE = Pattern.compile("(?:未完成|没完成|没有完成|还没完成|未做|没做)");

    private final CareTaskRepository taskRepository;
    private final CareAuthorizationService authorizationService;
    private final MedicalIdentityRepository identityRepository;
    private final CareNotificationRepository notificationRepository;
    private final CareProperties properties;
    private final Clock clock;

    public CareTaskInteractionService(
            CareTaskRepository taskRepository,
            CareAuthorizationService authorizationService,
            MedicalIdentityRepository identityRepository,
            CareNotificationRepository notificationRepository,
            CareProperties properties,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.authorizationService = authorizationService;
        this.identityRepository = identityRepository;
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public static boolean looksLikeTaskReply(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String value = text.strip();
        return TASK_ID.matcher(value).find()
                && (COMPLETED.matcher(value).find() || INCOMPLETE.matcher(value).find());
    }

    @Transactional
    public TaskReplyResult processReply(CareActor actor, String text, String requestId) {
        ParsedReply reply = parse(text);
        if (reply == null) {
            return TaskReplyResult.invalid("请按任务提醒中的编号回复，例如“完成 #12”或“未完成 #12”。");
        }
        CareTaskInstance task = taskRepository.findById(reply.taskId())
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "照护任务不存在"));
        authorizationService.require(actor, task.patientUserId(), CarePermissions.TASK_UPDATE,
                reply.completed() ? "COMPLETE_CARE_TASK_BY_WECHAT" : "REPORT_CARE_TASK_INCOMPLETE_BY_WECHAT",
                "CARE_TASK", Long.toString(task.id()), requestId);
        if (task.status() == CareTaskStatus.COMPLETED) {
            return TaskReplyResult.alreadyCompleted(task);
        }
        if (task.status() == CareTaskStatus.CANCELLED || task.status() == CareTaskStatus.SKIPPED) {
            return TaskReplyResult.unavailable(task);
        }
        Instant now = clock.instant();
        if (reply.completed()) {
            return complete(task, actor, now);
        }
        return reportIncomplete(task, actor, now);
    }

    private TaskReplyResult complete(CareTaskInstance task, CareActor actor, Instant now) {
        boolean changed = taskRepository.complete(
                task.id(), actor.userId(), task.version(), "微信回复：已完成", now);
        if (changed) {
            return TaskReplyResult.completed(task);
        }
        return afterConcurrentUpdate(task.id());
    }

    private TaskReplyResult reportIncomplete(CareTaskInstance task, CareActor actor, Instant now) {
        boolean changed = taskRepository.reportIncomplete(
                task.id(), actor.userId(), task.version(), "微信回复：未完成", now);
        if (!changed) {
            return afterConcurrentUpdate(task.id());
        }
        int notified = enqueueFamilyNotifications(task, actor.userId(), now);
        taskRepository.markOverdueNotified(task.id(), now);
        return TaskReplyResult.incomplete(task, notified);
    }

    private TaskReplyResult afterConcurrentUpdate(long taskId) {
        CareTaskInstance current = taskRepository.findById(taskId).orElse(null);
        if (current != null && current.status() == CareTaskStatus.COMPLETED) {
            return TaskReplyResult.alreadyCompleted(current);
        }
        return TaskReplyResult.conflict();
    }

    private int enqueueFamilyNotifications(CareTaskInstance task, long actorUserId, Instant now) {
        List<NotificationTarget> targets = familyTargets(task.patientUserId(), now).stream()
                .filter(target -> target.userId() != actorUserId)
                .toList();
        String content = "【任务异常提醒】\n患者反馈以下照护任务暂未完成，请及时关注。\n任务："
                + task.title() + "（#" + task.id() + "）";
        for (NotificationTarget target : targets) {
            notificationRepository.enqueue(new MedicalNotification(
                    0L, target.userId(), task.patientUserId(), target.connectionId(), target.recipientId(),
                    "CARE_TASK_INCOMPLETE", "WECHAT", content, "PENDING", now, null, 0,
                    properties.notification().maxRetryCount(), "", null,
                    "task:" + task.id() + ":CARE_TASK_INCOMPLETE:" + targetHash(target), now, now));
        }
        return targets.size();
    }

    private List<NotificationTarget> familyTargets(long patientUserId, Instant now) {
        Map<String, NotificationTarget> distinct = new LinkedHashMap<>();
        for (MedicalRole role : List.of(MedicalRole.FAMILY, MedicalRole.CAREGIVER)) {
            for (NotificationTarget target : identityRepository.listNotificationTargetsByRole(
                    patientUserId, role, CarePermissions.TASK_READ, now)) {
                distinct.put(target.userId() + ":" + target.connectionId() + ":" + target.recipientId(), target);
            }
        }
        return List.copyOf(distinct.values());
    }

    private String targetHash(NotificationTarget target) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (target.connectionId() + ":" + target.recipientId()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成照护通知幂等键", exception);
        }
    }

    private ParsedReply parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = TASK_ID.matcher(text.strip());
        if (!matcher.find()) {
            return null;
        }
        String id = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        try {
            long taskId = Long.parseLong(id);
            if (taskId <= 0) {
                return null;
            }
            if (INCOMPLETE.matcher(text).find()) {
                return new ParsedReply(taskId, false);
            }
            if (COMPLETED.matcher(text).find()) {
                return new ParsedReply(taskId, true);
            }
            return null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ParsedReply(long taskId, boolean completed) {
    }

    public record TaskReplyResult(String message, boolean stateChanged) {

        static TaskReplyResult completed(CareTaskInstance task) {
            return new TaskReplyResult("已记录：任务“" + task.title() + "”已完成。", true);
        }

        static TaskReplyResult incomplete(CareTaskInstance task, int notified) {
            String suffix = notified > 0
                    ? "任务已标记为异常，并已通知已授权家属关注。"
                    : "任务已标记为异常；目前没有可通知的已授权家属。";
            return new TaskReplyResult("已记录：任务“" + task.title() + "”暂未完成，" + suffix, true);
        }

        static TaskReplyResult alreadyCompleted(CareTaskInstance task) {
            return new TaskReplyResult("任务“" + task.title() + "”已经完成，无需重复打卡。", false);
        }

        static TaskReplyResult unavailable(CareTaskInstance task) {
            return new TaskReplyResult("任务“" + task.title() + "”已取消或跳过，不能再打卡。", false);
        }

        static TaskReplyResult conflict() {
            return new TaskReplyResult("任务状态刚刚发生变化，请刷新照护端后再确认。", false);
        }

        static TaskReplyResult invalid(String message) {
            return new TaskReplyResult(message, false);
        }
    }
}
