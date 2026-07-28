package com.example.spring.wechat.reminder.service;

import com.example.spring.wechat.reminder.config.ReminderProperties;
import com.example.spring.wechat.reminder.model.ReminderDelayUnit;
import com.example.spring.wechat.reminder.model.ReminderException;
import com.example.spring.wechat.reminder.model.ReminderRecipient;
import com.example.spring.wechat.reminder.model.ReminderRepeatType;
import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.model.ReminderStatus;
import com.example.spring.wechat.reminder.repository.ReminderTaskRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class ReminderService {

    private final ReminderTaskRepository repository;
    private final ReminderProperties properties;
    private final Clock clock;

    public ReminderService(ReminderTaskRepository repository, ReminderProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public ReminderTask create(CreateCommand command) {
        return create(command, clock.instant());
    }

    public ReminderTask create(CreateCommand command, Instant now) {
        if (command == null) {
            throw new ReminderException("缺少提醒内容");
        }
        ZoneId zoneId = resolveZoneId(command.timezone());
        Instant executeAt = parseExecuteAt(command.executeAt(), zoneId);
        if (!executeAt.isAfter(now)) {
            throw new ReminderException("提醒时间必须晚于当前时间");
        }
        ReminderRepeatType repeatType = ReminderRepeatType.from(command.repeatType());
        return createTask(
                null,
                command.sessionKey(),
                command.title(),
                command.content(),
                repeatType,
                zoneId,
                executeAt,
                now);
    }

    public ReminderTask createAfter(CreateAfterCommand command) {
        if (command == null) {
            throw new ReminderException("缺少提醒内容");
        }
        Instant now = clock.instant();
        ZoneId zoneId = resolveZoneId(command.timezone());
        return createTask(
                null,
                command.sessionKey(),
                command.title(),
                command.content(),
                ReminderRepeatType.ONCE,
                zoneId,
                now.plus(delay(command.delayValue(), command.delayUnit())),
                now);
    }

    public List<ReminderTask> list(String sessionKey) {
        return repository.listBySession(clean(sessionKey));
    }

    public List<ReminderTask> list(ListCommand command) {
        if (command == null) {
            throw new ReminderException("缺少提醒查询条件");
        }
        ReminderStatus status = parseStatus(command.status());
        int limit = command.limit() == null ? 20 : command.limit();
        if (limit < 1 || limit > 100) {
            throw new ReminderException("limit 需要在 1 到 100 之间");
        }
        return repository.listBySession(
                clean(command.sessionKey()), status, clean(command.keyword()), limit);
    }

    public ReminderTask cancel(TargetCommand command) {
        Instant now = clock.instant();
        ReminderTask task = resolveTarget(command, true);
        if (!repository.cancel(task.id(), clean(command.sessionKey()), now)) {
            throw new ReminderException("该提醒当前无法取消，可能正在发送或已经结束");
        }
        return repository.findById(task.id()).orElseThrow();
    }

    public ReminderTask complete(TargetCommand command) {
        Instant now = clock.instant();
        ReminderTask task = resolveTarget(command, true);
        if (!repository.complete(task.id(), clean(command.sessionKey()), now)) {
            throw new ReminderException("该提醒当前无法标记完成，可能已经结束");
        }
        return repository.findById(task.id()).orElseThrow();
    }

    public ReminderTask snooze(SnoozeCommand command) {
        if (command == null) {
            throw new ReminderException("缺少延后提醒参数");
        }
        Instant now = clock.instant();
        Duration delay = delay(command.delayValue(), command.delayUnit());
        boolean useLatestDelivery = command.reminderId() == null && clean(command.title()).isBlank();
        ReminderTask task = useLatestDelivery
                ? repository.findLatestDeliveredBySession(clean(command.sessionKey()))
                        .orElseThrow(() -> new ReminderException("没有找到最近发送的提醒，请提供提醒编号或标题"))
                : resolveTarget(new TargetCommand(
                        command.sessionKey(), command.reminderId(), command.title()), false);
        if (!useLatestDelivery && task.status() == ReminderStatus.ACTIVE) {
            if (!repository.snooze(task.id(), clean(command.sessionKey()), now.plus(delay), now)) {
                throw new ReminderException("该提醒当前无法延后，可能正在发送或已经结束");
            }
            return repository.findById(task.id()).orElseThrow();
        }
        if (task.status() == ReminderStatus.CANCELLED || task.status() == ReminderStatus.FAILED) {
            throw new ReminderException("已取消或发送失败的提醒不能直接延后，请重新创建提醒");
        }
        return createTask(
                task.id(),
                command.sessionKey(),
                task.title(),
                task.content(),
                ReminderRepeatType.ONCE,
                resolveZoneId(task.timezone()),
                now.plus(delay),
                now);
    }

    public ReminderTask update(UpdateCommand command) {
        if (command == null) {
            throw new ReminderException("缺少提醒修改参数");
        }
        ReminderTask task = resolveTarget(
                new TargetCommand(command.sessionKey(), command.reminderId(), command.currentTitle()), true);
        Instant now = clock.instant();
        boolean hasAbsoluteTime = command.executeAt() != null && !command.executeAt().isBlank();
        boolean hasRelativeTime = command.delayValue() != null;
        if (hasAbsoluteTime && hasRelativeTime) {
            throw new ReminderException("execute_at 和相对延迟时间不能同时提供");
        }
        ZoneId zoneId = resolveZoneId(firstNonBlank(command.timezone(), task.timezone()));
        Instant executeAt = task.nextExecuteAt();
        if (hasAbsoluteTime) {
            executeAt = parseExecuteAt(command.executeAt(), zoneId);
        } else if (hasRelativeTime) {
            executeAt = now.plus(delay(command.delayValue(), command.delayUnit()));
        }
        if (executeAt == null || !executeAt.isAfter(now)) {
            throw new ReminderException("提醒时间必须晚于当前时间");
        }
        String title = firstNonBlank(command.newTitle(), task.title());
        String content = Boolean.TRUE.equals(command.clearContent())
                ? ""
                : command.content() == null || command.content().isBlank()
                        ? task.content()
                        : command.content().strip();
        boolean changed = !title.equals(task.title())
                || !content.equals(task.content())
                || !zoneId.getId().equals(task.timezone())
                || !executeAt.equals(task.nextExecuteAt());
        if (!changed) {
            throw new ReminderException("请提供需要修改的标题、内容或提醒时间");
        }
        validateTitle(title);
        if (!repository.updateActive(
                task.id(), clean(command.sessionKey()), title, limit(content, 4_000),
                zoneId.getId(), executeAt, now)) {
            throw new ReminderException("该提醒当前无法修改，可能正在发送或已经结束");
        }
        return repository.findById(task.id()).orElseThrow();
    }

    public ReminderTask cancel(long taskId, String sessionKey, Instant now) {
        requireTask(taskId, sessionKey);
        if (!repository.cancel(taskId, clean(sessionKey), now)) {
            throw new ReminderException("该提醒当前无法取消，可能正在发送或已经结束");
        }
        return repository.findById(taskId).orElseThrow();
    }

    public ReminderTask complete(long taskId, String sessionKey, Instant now) {
        requireTask(taskId, sessionKey);
        if (!repository.complete(taskId, clean(sessionKey), now)) {
            throw new ReminderException("该提醒当前无法标记完成，可能已经结束");
        }
        return repository.findById(taskId).orElseThrow();
    }

    public ReminderTask snooze(long taskId, String sessionKey, int minutes, Instant now) {
        if (minutes < 1 || minutes > 10_080) {
            throw new ReminderException("延后时间需要在 1 到 10080 分钟之间");
        }
        requireTask(taskId, sessionKey);
        Instant nextExecuteAt = now.plusSeconds(minutes * 60L);
        if (!repository.snooze(taskId, clean(sessionKey), nextExecuteAt, now)) {
            throw new ReminderException("该提醒当前无法延后，可能正在发送或已经结束");
        }
        return repository.findById(taskId).orElseThrow();
    }

    private ReminderTask requireTask(long taskId, String sessionKey) {
        if (taskId <= 0) {
            throw new ReminderException("提醒编号必须是正整数");
        }
        return repository.findByIdAndSession(taskId, clean(sessionKey))
                .orElseThrow(() -> new ReminderException("没有找到这个提醒，或它不属于当前会话"));
    }

    private ReminderTask createTask(
            Long parentTaskId,
            String sessionKey,
            String titleValue,
            String content,
            ReminderRepeatType repeatType,
            ZoneId zoneId,
            Instant executeAt,
            Instant now) {
        String title = required(titleValue, "请告诉我需要提醒什么");
        validateTitle(title);
        ReminderRecipient recipient = ReminderRecipient.fromSessionKey(sessionKey);
        return repository.save(new ReminderTask(
                0L,
                parentTaskId,
                clean(sessionKey),
                recipient.connectionId(),
                recipient.userId(),
                title,
                limit(content, 4_000),
                repeatType,
                zoneId.getId(),
                executeAt,
                ReminderStatus.ACTIVE,
                0,
                properties.delivery().maxRetryCount(),
                null,
                "",
                null,
                now,
                now));
    }

    private ReminderTask resolveTarget(TargetCommand command, boolean activeOnly) {
        if (command == null) {
            throw new ReminderException("请提供提醒编号或标题");
        }
        String sessionKey = clean(command.sessionKey());
        if (command.reminderId() != null) {
            if (command.reminderId() <= 0) {
                throw new ReminderException("提醒编号必须是正整数");
            }
            ReminderTask task = repository.findByIdAndSession(command.reminderId(), sessionKey)
                    .orElseThrow(() -> new ReminderException("没有找到这个提醒，或它不属于当前会话"));
            if (activeOnly && task.status() != ReminderStatus.ACTIVE) {
                throw new ReminderException("该提醒不是待提醒状态，当前无法操作");
            }
            return task;
        }
        String title = required(command.title(), "请提供提醒编号或标题");
        List<ReminderTask> candidates = repository.listBySession(
                        sessionKey, activeOnly ? ReminderStatus.ACTIVE : null, title, 10).stream()
                .filter(task -> !activeOnly || task.status() == ReminderStatus.ACTIVE)
                .toList();
        List<ReminderTask> exact = candidates.stream()
                .filter(task -> task.title().equalsIgnoreCase(title))
                .toList();
        List<ReminderTask> selected = exact.isEmpty() ? candidates : exact;
        if (selected.isEmpty()) {
            throw new ReminderException("没有找到标题包含“" + title + "”的待提醒任务");
        }
        if (selected.size() > 1) {
            String options = selected.stream()
                    .limit(5)
                    .map(task -> "#" + task.id() + " " + task.title())
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("");
            throw new ReminderException("找到多个匹配提醒，请指定编号：" + options);
        }
        return selected.get(0);
    }

    private Duration delay(Long value, String unitValue) {
        if (value == null || value < 1) {
            throw new ReminderException("delay_value 必须是正整数");
        }
        Duration duration;
        try {
            duration = ReminderDelayUnit.from(unitValue).duration(value);
        } catch (ArithmeticException exception) {
            throw new ReminderException("延后时间数值过大");
        }
        if (duration.compareTo(Duration.ofDays(7)) > 0) {
            throw new ReminderException("相对延后时间不能超过 7 天");
        }
        return duration;
    }

    private ReminderStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return ReminderStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ReminderException("status 只能是 active、processing、completed、cancelled 或 failed");
        }
    }

    private void validateTitle(String title) {
        if (title.length() > 255) {
            throw new ReminderException("提醒标题不能超过 255 个字符");
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? clean(fallback) : first.strip();
    }

    private ZoneId resolveZoneId(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? properties.defaultTimezone() : timezone.strip());
        } catch (Exception exception) {
            throw new ReminderException("timezone 必须是有效时区，例如 Asia/Shanghai");
        }
    }

    private Instant parseExecuteAt(String value, ZoneId zoneId) {
        String text = required(value, "请提供提醒时间");
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // Accept an offset time and a local ISO time for compatibility with tool callers.
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // Continue with the configured zone for a local date-time value.
        }
        try {
            return LocalDateTime.parse(text).atZone(zoneId).toInstant();
        } catch (DateTimeParseException exception) {
            throw new ReminderException("execute_at 必须是 ISO-8601 时间，例如 2026-07-27T19:30:00+08:00");
        }
    }

    private String required(String value, String message) {
        String text = clean(value);
        if (text.isBlank()) {
            throw new ReminderException(message);
        }
        return text;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String limit(String value, int maxLength) {
        String text = clean(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public record CreateCommand(
            String sessionKey,
            String title,
            String content,
            String executeAt,
            String repeatType,
            String timezone) {
    }

    public record CreateAfterCommand(
            String sessionKey,
            String title,
            String content,
            Long delayValue,
            String delayUnit,
            String timezone) {

        public CreateAfterCommand(
                String sessionKey,
                String title,
                String content,
                int delayMinutes,
                String timezone) {
            this(sessionKey, title, content, (long) delayMinutes, "minutes", timezone);
        }
    }

    public record ListCommand(
            String sessionKey,
            String status,
            String keyword,
            Integer limit) {
    }

    public record TargetCommand(
            String sessionKey,
            Long reminderId,
            String title) {
    }

    public record SnoozeCommand(
            String sessionKey,
            Long reminderId,
            String title,
            Long delayValue,
            String delayUnit) {
    }

    public record UpdateCommand(
            String sessionKey,
            Long reminderId,
            String currentTitle,
            String newTitle,
            String content,
            Boolean clearContent,
            String executeAt,
            Long delayValue,
            String delayUnit,
            String timezone) {
    }
}
