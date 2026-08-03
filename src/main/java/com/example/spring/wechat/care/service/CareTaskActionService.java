package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareTaskActionToken;
import com.example.spring.wechat.care.model.CareTaskInstance;
import com.example.spring.wechat.care.model.CareTaskStatus;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CareTaskActionService {

    private final CareTaskActionTokenService tokenService;
    private final CareTaskService taskService;
    private final CareAuthorizationService authorizationService;
    private final MedicalIdentityRepository identityRepository;
    private final Clock clock;

    public CareTaskActionService(
            CareTaskActionTokenService tokenService,
            CareTaskService taskService,
            CareAuthorizationService authorizationService,
            MedicalIdentityRepository identityRepository,
            Clock clock) {
        this.tokenService = tokenService;
        this.taskService = taskService;
        this.authorizationService = authorizationService;
        this.identityRepository = identityRepository;
        this.clock = clock;
    }

    public TaskActionView current(String rawToken, String requestId) {
        ActionContext context = requireContext(rawToken, requestId, "READ_TASK_ACTION_LINK");
        return view(context.task(), context.token());
    }

    @Transactional
    public TaskActionView complete(String rawToken, String note, String requestId) {
        ActionContext context = requireContext(rawToken, requestId, "COMPLETE_TASK_ACTION_LINK");
        CareTaskInstance task = context.task();
        CareTaskService.ActionCommand command = new CareTaskService.ActionCommand(
                task.version(), note, requestId);
        CareTaskInstance updated;
        if (task.status() == CareTaskStatus.PENDING) {
            updated = taskService.complete(context.actor(), task.id(), command);
        } else if (task.status() == CareTaskStatus.OVERDUE) {
            updated = taskService.backfill(context.actor(), task.id(), command);
        } else {
            throw new CareException(CareErrorCode.CONFLICT, "当前任务不能再确认完成");
        }
        tokenService.consume(context.token(), clock.instant());
        return view(updated, context.token());
    }

    @Transactional
    public TaskActionView missed(String rawToken, String note, String requestId) {
        ActionContext context = requireContext(rawToken, requestId, "MISS_TASK_ACTION_LINK");
        CareTaskInstance task = context.task();
        CareTaskInstance updated = taskService.reportMissed(context.actor(), task.id(),
                new CareTaskService.ActionCommand(task.version(), note, requestId));
        tokenService.consume(context.token(), clock.instant());
        return view(updated, context.token());
    }

    private ActionContext requireContext(String rawToken, String requestId, String action) {
        Instant now = clock.instant();
        CareTaskActionToken token = tokenService.requireActive(rawToken, now);
        CareTaskInstance task = taskService.findTask(token.taskInstanceId());
        if (!identityRepository.hasActiveRole(token.actorUserId(), token.actorRole())) {
            throw new CareException(CareErrorCode.FORBIDDEN, "任务链接对应的身份已经失效");
        }
        MedicalUser user = identityRepository.findUserById(token.actorUserId())
                .orElseThrow(() -> new CareException(CareErrorCode.NOT_FOUND, "任务链接对应的用户不存在"));
        CareActor actor = new CareActor(user.id(), user.userCode(), user.displayName(), token.actorRole());
        authorizationService.require(actor, task.patientUserId(), CarePermissions.PATIENT_TASK_BACKFILL,
                action, "CARE_TASK", Long.toString(task.id()), requestId);
        return new ActionContext(token, task, actor);
    }

    private TaskActionView view(CareTaskInstance task, CareTaskActionToken token) {
        boolean open = task.status() == CareTaskStatus.PENDING || task.status() == CareTaskStatus.OVERDUE;
        return new TaskActionView(
                task.id(), task.title(), task.status(), task.dueAt(), task.lateCheckinDeadlineAt(),
                task.version(), token.actorRole(), open, open);
    }

    private record ActionContext(CareTaskActionToken token, CareTaskInstance task, CareActor actor) {
    }

    public record TaskActionView(
            long taskId,
            String title,
            CareTaskStatus status,
            Instant dueAt,
            Instant backfillDeadlineAt,
            long version,
            com.example.spring.wechat.care.model.MedicalRole actorRole,
            boolean canComplete,
            boolean canReportMissed) {
    }
}
