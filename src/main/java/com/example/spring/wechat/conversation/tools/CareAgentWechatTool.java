package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalNotification;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MedicalUser;
import com.example.spring.wechat.care.model.NotificationTarget;
import com.example.spring.wechat.care.repository.CareNotificationRepository;
import com.example.spring.wechat.care.repository.MedicalIdentityRepository;
import com.example.spring.wechat.care.service.CareAuthorizationService;
import com.example.spring.wechat.care.service.CarePermissions;
import com.example.spring.wechat.care.service.CarePlanDraftService;
import com.example.spring.wechat.care.service.CareTaskInteractionService;
import com.example.spring.wechat.care.service.CareWebLinkService;
import com.example.spring.wechat.reminder.service.ReminderNotificationSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CareAgentWechatTool implements WechatTool {

    private final MedicalIdentityRepository identityRepository;
    private final CareAuthorizationService authorizationService;
    private final CareWebLinkService linkService;
    private final CarePlanDraftService draftService;
    private final CareTaskInteractionService taskInteractionService;
    private final CareNotificationRepository notificationRepository;
    private final ObjectProvider<ReminderNotificationSender> notificationSenderProvider;
    private final ChatService chatService;
    private final Clock clock;
    private final Map<String, String> pendingDraftIdsBySession = new ConcurrentHashMap<>();

    public CareAgentWechatTool(
            MedicalIdentityRepository identityRepository,
            CareAuthorizationService authorizationService,
            CareWebLinkService linkService,
            CarePlanDraftService draftService,
            CareTaskInteractionService taskInteractionService,
            CareNotificationRepository notificationRepository,
            ObjectProvider<ReminderNotificationSender> notificationSenderProvider,
            ChatService chatService,
            Clock clock) {
        this.identityRepository = identityRepository;
        this.authorizationService = authorizationService;
        this.linkService = linkService;
        this.draftService = draftService;
        this.taskInteractionService = taskInteractionService;
        this.notificationRepository = notificationRepository;
        this.notificationSenderProvider = notificationSenderProvider;
        this.chatService = chatService;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "care_agent";
    }

    @Override
    public String description() {
        return "医疗照护业务工具：识别当前微信医疗身份，检查患者绑定，返回家属/医生/患者 Web 页面链接，联系医生，并整理医生照护方案草稿。";
    }

    @Override
    public List<String> arguments() {
        return List.of("action", "patient_code", "message", "plan_text", "nickname");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum("action", "操作类型", List.of(
                        "status", "bind", "contact_doctor", "doctor_workspace", "plan_draft", "plan_confirm", "task_response", "whoami", "rename"), "status"),
                WechatToolParameter.optionalString("patient_code", "患者编号，例如 PAT-12345678；绑定或指定患者时填写", "PAT-12345678"),
                WechatToolParameter.optionalString("message", "联系医生时要发送的消息", "患者今天头晕，请医生关注。"),
                WechatToolParameter.optionalString("plan_text", "医生输入的原始照护方案要求", "每天提醒患者喝水三次，晚上确认安全。"),
                WechatToolParameter.optionalString("nickname", "修改昵称时填写新昵称", "朵"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "把微信自然语言请求接入医疗照护业务，负责身份、绑定、状态链接、医生工作台、联系医生和方案草稿整理。",
                List.of(
                        "当前微信用户未通过 /patient、/caregiver、/doctor 登录时，提示先登录。",
                        "家属和医生没有绑定患者时，返回对应绑定页面链接。",
                        "患者状态详情通过 Web 链接展示，不在微信里展开敏感详情。",
                        "联系医生只发给已绑定到同一患者且有计划管理权限的医生。",
                        "医生方案草稿只做专业化整理，不自动激活任务，必须由医生在页面确认。"),
                List.of("action：用户想做什么；patient_code：绑定患者或指定患者时需要；message：联系医生时需要"),
                List.of("身份说明", "绑定页面链接", "患者状态页面链接", "医生工作台链接", "医生通知发送结果", "方案草稿和审核链接"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            MedicalRole role = identityRepository.findCurrentRoleBySessionKey(request.sessionKey()).orElse(null);
            MedicalUser user = role == null
                    ? identityRepository.findUserBySessionKey(request.sessionKey()).orElse(null)
                    : identityRepository.findUserBySessionKeyAndRole(request.sessionKey(), role).orElse(null);
            if (user == null) {
                return WechatReply.text("""
                        当前微信账号还没有医疗身份。
                        请先在命令行使用 /patient、/caregiver 或 /doctor 生成二维码并扫码登录，然后再发送一条消息完成身份绑定。
                        """.strip());
            }
            if (role == null) {
                role = firstRole(user.id());
            }
            CareActor actor = new CareActor(user.id(), user.userCode(), user.displayName(), role);
            String action = resolveAction(request, role);
            return switch (action) {
                case "whoami" -> WechatReply.text(whoami(actor));
                case "bind" -> WechatReply.text(bindLink(actor, request.sessionKey()));
                case "contact_doctor" -> WechatReply.text(contactDoctor(actor, request));
                case "doctor_workspace" -> WechatReply.text(doctorWorkspace(actor, request.sessionKey()));
                case "plan_draft" -> WechatReply.text(planDraft(actor, request));
                case "plan_confirm" -> WechatReply.text(confirmPlanDraft(actor, request.sessionKey()));
                case "task_response" -> WechatReply.text(taskResponse(actor, request));
                case "rename" -> WechatReply.text(renameNickname(actor, request));
                default -> WechatReply.text(status(actor, request));
            };
        } catch (CareException exception) {
            return WechatReply.text(exception.getMessage());
        } catch (RuntimeException exception) {
            return WechatReply.text("照护业务处理失败：" + rootMessage(exception));
        }
    }

    private String whoami(CareActor actor) {
        return """
                当前医疗身份：%s
                用户编号：%s
                昵称：%s
                """.formatted(roleLabel(actor.role()), actor.userCode(), actor.displayName()).strip();
    }

    private String renameNickname(CareActor actor, WechatToolRequest request) {
        String nickname = extractNickname(request);
        if (nickname.isBlank()) {
            return """
                    请直接告诉我你想改成的昵称。
                    例如：我想改一下昵称:朵
                    """.strip();
        }
        Optional<MedicalUser> updated = identityRepository.updateDisplayName(actor.userId(), nickname, clock.instant());
        if (updated.isEmpty()) {
            return "昵称修改失败，请稍后重试。";
        }
        MedicalUser user = updated.get();
        return """
                昵称已更新为：%s
                当前医疗身份：%s
                用户编号：%s
                """.formatted(user.displayName(), roleLabel(actor.role()), user.userCode()).strip();
    }

    private String status(CareActor actor, WechatToolRequest request) {
        if (actor.role() == MedicalRole.PATIENT) {
            CareWebLinkService.CareWebSessionLink link = linkService.createForWechatSession(
                    request.sessionKey(), "/patient/tasks");
            return "今日任务与打卡：\n" + link.url();
        }

        List<MedicalUser> patients = authorizationService.listAccessiblePatients(actor, CarePermissions.STATUS_READ);
        if (patients.isEmpty()) {
            return noBindingText(actor, request.sessionKey());
        }
        String route = actor.role().isClinical() ? "/doctor/patients" : "/caregiver/status";
        CareWebLinkService.CareWebSessionLink link = linkService.createForWechatSession(request.sessionKey(), route);
        return link.url();
    }

    private String bindLink(CareActor actor, String sessionKey) {
        if (actor.role() == MedicalRole.PATIENT) {
            return "患者端用于接收任务。请把你的患者编号发给家属或医生：\n" + actor.userCode();
        }
        String route = actor.role().isClinical() ? "/bind/doctor" : "/bind/caregiver";
        String label = actor.role().isClinical() ? "医生绑定患者" : "家属绑定患者";
        return "%s：\n%s".formatted(label, linkService.createForWechatSession(sessionKey, route).url());
    }

    private String noBindingText(CareActor actor, String sessionKey) {
        return """
                你当前还没有绑定患者，暂时不能查看患者状态。
                请先打开下面的页面完成绑定：
                %s
                """.formatted(linkService.createForWechatSession(
                sessionKey, actor.role().isClinical() ? "/bind/doctor" : "/bind/caregiver").url()).strip();
    }

    private String doctorWorkspace(CareActor actor, String sessionKey) {
        if (!actor.role().isClinical()) {
            return "当前账号不是医生/医护身份，不能打开医生工作台。";
        }
        List<MedicalUser> patients = authorizationService.listAccessiblePatients(actor, CarePermissions.STATUS_READ);
        if (patients.isEmpty()) {
            return noBindingText(actor, sessionKey);
        }
        return "医生患者工作台：\n" + linkService.createForWechatSession(sessionKey, "/doctor/patients").url();
    }

    private String contactDoctor(CareActor actor, WechatToolRequest request) {
        if (actor.role().isClinical()) {
            return "你当前就是医护身份。若要给患者发布方案，请直接描述方案内容。";
        }
        List<MedicalUser> patients = authorizationService.listAccessiblePatients(actor, CarePermissions.STATUS_READ);
        if (patients.isEmpty()) {
            return noBindingText(actor, request.sessionKey());
        }
        MedicalUser patient = choosePatient(patients, request.argument("patient_code"), request.userText());
        String message = firstNonBlank(request.argument("message"), request.userText(), "家属请求医生关注患者情况");
        List<NotificationTarget> doctors = identityRepository.listNotificationTargetsByRole(
                patient.id(), MedicalRole.DOCTOR, CarePermissions.PLAN_MANAGE, clock.instant());
        if (doctors.isEmpty()) {
            return "当前患者还没有绑定可联系的医生。你可以先让医生完成患者绑定，或通过邮箱把情况发给医生。";
        }
        String content = """
                【家属联系医生】
                患者：%s（%s）
                家属：%s（%s）
                内容：%s
                """.formatted(patient.displayName(), patient.userCode(), actor.displayName(), actor.userCode(), message).strip();
        int delivered = 0;
        for (NotificationTarget doctor : doctors) {
            if (trySendNow(doctor, content)) {
                delivered++;
            } else {
                enqueue(patient.id(), doctor, "CARE_FAMILY_TO_DOCTOR", content);
            }
        }
        if (delivered == doctors.size()) {
            return "已通过机器人把消息发送给绑定医生。";
        }
        return "已记录给医生的消息；其中 " + delivered + " 位医生已即时收到，其余会在通知通道可用后继续发送。";
    }

    private String planDraft(CareActor actor, WechatToolRequest request) {
        if (!actor.role().isClinical()) {
            return "只有医生/医护身份可以制定或调整患者方案。";
        }
        List<MedicalUser> patients = authorizationService.listAccessiblePatients(actor, CarePermissions.PLAN_MANAGE);
        if (patients.isEmpty()) {
            return noBindingText(actor, request.sessionKey());
        }
        MedicalUser patient = choosePatient(patients, request.argument("patient_code"), request.userText());
        String rawPlan = firstNonBlank(request.argument("plan_text"), request.userText());
        String refined = refinePlan(patient, rawPlan);
        CarePlanDraftService.CarePlanDraftDetails draft = draftService.createDraft(
                actor,
                patient.id(),
                patient.displayName(),
                patient.userCode(),
                patient.displayName() + "照护方案草案",
                rawPlan,
                refined,
                traceId());
        pendingDraftIdsBySession.put(request.sessionKey(), draft.id());
        String link = linkService.createForWechatSession(
                request.sessionKey(),
                "/doctor/alerts-review",
                Map.of("draftId", draft.id())).url();
        return """
                已整理成医生审核草稿，暂未发送给患者。

                患者：%s（%s）

                %s

                如果确认发送给患者，请直接回复“确认发送给患者”。

                请打开医生审核页确认后再发送给患者：
                %s
                """.formatted(patient.displayName(), patient.userCode(), refined, link).strip();
    }

    private String confirmPlanDraft(CareActor actor, String sessionKey) {
        if (!actor.role().isClinical()) {
            return "只有医生/医护身份可以确认发送患者方案。";
        }
        String draftId = pendingDraftIdsBySession.get(sessionKey);
        if (draftId == null) {
            return "当前没有待确认的患者方案草稿。请先描述要给患者制定的方案。";
        }
        CarePlanDraftService.DraftSendResult result = draftService.confirm(actor, draftId, traceId());
        pendingDraftIdsBySession.remove(sessionKey);
        if (result.deliveredCount() == 0 && result.queuedCount() == 0) {
            return "这份方案已经发送给患者，可在审核页查看状态。";
        }
        return "已确认方案并提交给患者；其中 " + result.deliveredCount()
                + " 个微信通道已即时送达，其余会在通知通道可用后继续发送。";
    }

    private String taskResponse(CareActor actor, WechatToolRequest request) {
        return taskInteractionService.processReply(actor, request.userText(), traceId()).message();
    }

    private String refinePlan(MedicalUser patient, String rawPlan) {
        String text = rawPlan == null ? "" : rawPlan.strip();
        if (text.isBlank()) {
            return "请补充方案内容，例如提醒时间、任务频率、需要患者确认的事项。";
        }
        try {
            return chatService.reply("""
                    请把医生输入的照护方案整理成专业、清晰、可审核的草稿。
                    不要新增药物剂量、诊断或医疗承诺；缺少信息时标注“待医生确认”。
                    输出包含：目标、执行任务、提醒频率、患者确认方式、风险提示。
                    患者：%s（%s）
                    医生原始输入：%s
                    """.formatted(patient.displayName(), patient.userCode(), text)).strip();
        } catch (RuntimeException exception) {
            return "方案草稿：" + text + "\n\n待医生确认：提醒时间、执行频率、风险边界。";
        }
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

    private MedicalUser choosePatient(List<MedicalUser> patients, String patientCode, String userText) {
        String code = firstNonBlank(patientCode, extractPatientCode(userText));
        if (!code.isBlank()) {
            for (MedicalUser patient : patients) {
                if (patient.userCode().equalsIgnoreCase(code) || patient.displayName().contains(code)) {
                    return patient;
                }
            }
        }
        String text = userText == null ? "" : userText;
        for (MedicalUser patient : patients) {
            if (!patient.displayName().isBlank() && text.contains(patient.displayName())) {
                return patient;
            }
        }
        return patients.get(0);
    }

    private String resolveAction(WechatToolRequest request, MedicalRole role) {
        String action = request.argument("action").strip().toLowerCase(Locale.ROOT);
        String text = request.userText();
        if (!action.isBlank() && !"status".equals(action)) {
            return action;
        }
        if (CareTaskInteractionService.looksLikeTaskReply(text)) return "task_response";
        if (containsAny(text, "我是谁", "当前身份", "身份")) return "whoami";
        if (containsAny(text, "绑定", "新增患者", "添加患者")) return "bind";
        if (containsAny(text, "联系医生", "通知医生", "告诉医生", "紧急", "发给医生")) return "contact_doctor";
        if (containsAny(text, "确认发送", "发送给患者", "发给患者", "确认并发送")) return "plan_confirm";
        if (looksLikeCarePlanDraft(request, text, role)) return "plan_draft";
        if (containsAny(text, "工作台", "切换患者") && role.isClinical()) return "doctor_workspace";
        return "status";
    }

    private boolean looksLikeCarePlanDraft(WechatToolRequest request, String text, MedicalRole role) {
        if (role == null || !role.isClinical()) {
            return false;
        }
        if (!request.argument("plan_text").isBlank()) {
            return true;
        }
        boolean explicitDraft = containsAny(text, "方案", "计划", "提醒", "定时", "制定", "重新制定", "调整", "发布");
        if (explicitDraft) {
            return true;
        }
        boolean statusQuery = containsAny(text, "查看", "查询", "完成情况", "状态", "怎么样", "多少");
        if (statusQuery && !containsAny(text, "发送", "每", "早上", "晚上", "中午", "半小时", "小时")) {
            return false;
        }
        return containsAny(text,
                "喝水", "饮水", "服药", "吃药", "安全确认", "确认安全", "打卡", "散步",
                "每半小时", "半小时", "每小时", "每个小时", "每2个小时", "每两个小时",
                "每天早上", "每天晚上", "早上", "晚上", "中午", "频率");
    }

    private MedicalRole firstRole(long userId) {
        List<MedicalRole> roles = identityRepository.listActiveRoles(userId);
        return roles.isEmpty() ? MedicalRole.PATIENT : roles.get(0);
    }

    private String extractPatientCode(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Z]{3}-[A-Z0-9]{8}").matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : "";
    }

    private String roleLabel(MedicalRole role) {
        if (role == MedicalRole.PATIENT) return "患者";
        if (role.isFamily()) return "家属";
        if (role.isClinical()) return "医生/医护";
        return role.name();
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return "";
    }

    private String extractNickname(WechatToolRequest request) {
        String[] sources = {
                request.argument("nickname"),
                request.argument("message"),
                request.userText()
        };
        for (String source : sources) {
            String nickname = extractNickname(source);
            if (!nickname.isBlank()) {
                return nickname;
            }
        }
        return "";
    }

    private String extractNickname(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String value = text.strip();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?:改(?:一下)?昵称|修改昵称|昵称改成|叫我|以后叫我|请叫我)[:：\\s]*([\\p{L}\\p{N}_·-]{1,32})")
                .matcher(value);
        if (matcher.find()) {
            return cleanNickname(matcher.group(1));
        }
        int colon = Math.max(value.lastIndexOf(':'), value.lastIndexOf('：'));
        if (colon >= 0 && colon < value.length() - 1 && value.contains("昵称")) {
            return cleanNickname(value.substring(colon + 1));
        }
        return "";
    }

    private String cleanNickname(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        while (!clean.isBlank() && "，。,.!！?？：:；;".indexOf(clean.charAt(clean.length() - 1)) >= 0) {
            clean = clean.substring(0, clean.length() - 1).strip();
        }
        return clean.length() > 32 ? clean.substring(0, 32) : clean;
    }

    private String traceId() {
        return java.util.UUID.randomUUID().toString();
    }

    private String idempotency(String type, long patientUserId, long targetUserId, String content) {
        try {
            String raw = type + ":" + patientUserId + ":" + targetUserId + ":" + content;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return "care-wechat:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception exception) {
            return "care-wechat:" + java.util.UUID.randomUUID();
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

}
