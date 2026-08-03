const patients = [
    {
        id: "P001",
        name: "张三",
        age: 68,
        gender: "男",
        doctor: "王医生",
        family: "李女士",
        risk: "ATTENTION",
        riskLabel: "需要关注",
        lastUpdate: "今天 09:42",
        taskDone: 5,
        taskTotal: 7,
        water: "已完成 4/6 次",
        safety: "08:30 已确认安全",
        summary: "昨晚睡眠一般，早晨血压偏高一次，已完成晨间安全确认。建议中午后再次关注饮水和血压记录。",
        tasks: [
            { title: "喝水提醒", status: "进行中", detail: "10:00、12:00、15:00、18:00 各提醒一次" },
            { title: "安全确认", status: "已完成", detail: "患者 08:30 回复状态正常" },
            { title: "血压记录", status: "待完成", detail: "今天晚饭前提醒测量一次" }
        ],
        alerts: [
            { title: "血压偏高", level: "attention", time: "09:10", detail: "收到一次偏高记录，等待晚间复测。" }
        ],
        checkins: [
            { time: "08:30", title: "安全确认", detail: "回复：今天感觉还可以。" },
            { time: "09:10", title: "血压记录", detail: "145/92，系统建议晚间复测。" },
            { time: "10:00", title: "喝水", detail: "已确认喝水。" }
        ],
        plan: "每日 6 次饮水提醒；早晚安全确认；晚饭前血压复测；若连续两次血压偏高，自动通知医生和家属。"
    },
    {
        id: "P002",
        name: "李四",
        age: 74,
        gender: "女",
        doctor: "王医生",
        family: "赵先生",
        risk: "NORMAL",
        riskLabel: "状态平稳",
        lastUpdate: "今天 10:18",
        taskDone: 6,
        taskTotal: 6,
        water: "已完成 5/5 次",
        safety: "09:00 已确认安全",
        summary: "今日任务完成情况良好，无异常告警。可继续保持当前照护计划。",
        tasks: [
            { title: "晨间问候", status: "已完成", detail: "患者回复精神状态良好" },
            { title: "饭后散步", status: "已完成", detail: "患者确认已完成 20 分钟散步" }
        ],
        alerts: [],
        checkins: [
            { time: "09:00", title: "安全确认", detail: "回复：一切正常。" },
            { time: "13:10", title: "饭后散步", detail: "已完成 20 分钟。" }
        ],
        plan: "保持每日晨间问候、午后散步和晚间总结。当前不需要额外增加提醒频率。"
    },
    {
        id: "P003",
        name: "陈五",
        age: 62,
        gender: "男",
        doctor: "赵医生",
        family: "陈女士",
        risk: "URGENT",
        riskLabel: "异常告警",
        lastUpdate: "今天 08:57",
        taskDone: 2,
        taskTotal: 6,
        water: "仅完成 1/5 次",
        safety: "未完成今日确认",
        summary: "患者今日多项任务未回复，安全确认超时，建议医生或家属尽快联系确认情况。",
        tasks: [
            { title: "安全确认", status: "超时", detail: "计划 08:00 确认，目前未收到回复" },
            { title: "服药提醒", status: "未确认", detail: "08:30 服药提醒未收到确认" }
        ],
        alerts: [
            { title: "安全确认超时", level: "urgent", time: "08:57", detail: "超过 60 分钟未回复安全确认。" },
            { title: "服药未确认", level: "attention", time: "09:05", detail: "早间服药任务未完成确认。" }
        ],
        checkins: [
            { time: "07:40", title: "晨间问候", detail: "未回复。" },
            { time: "08:57", title: "安全确认", detail: "超时未确认。" }
        ],
        plan: "提升安全确认优先级；若安全确认超过 30 分钟未回复，通知家属；超过 60 分钟未回复，通知医生。"
    }
];

const planDraft = {
    title: "张三 - 血压与饮水照护调整",
    doctorInput: "患者早晨血压偶有偏高，希望加强晚间复测，同时不要让提醒过于频繁。",
    botRefined: "建议保留原有饮水提醒，新增晚饭前血压复测；若两次连续偏高，系统自动生成告警并通知医生。提醒文案应保持温和，避免增加患者焦虑。",
    status: "待医生确认"
};

let selectedPatientId = "P001";
let livePatients = [];
let caregiverRefreshTimer;

const routes = {
    "/task-action": renderTaskAction,
    "/patient/tasks": renderPatientTasks,
    "/bind/caregiver": renderCaregiverBind,
    "/bind/doctor": renderDoctorBind,
    "/caregiver/status": renderCaregiverStatus,
    "/doctor/patients": renderDoctorSwitcher,
    "/doctor/detail": renderDoctorDetail,
    "/doctor/alerts-review": renderAlertsAndReview
};

const routeRoles = {
    "/task-action": [],
    "/patient/tasks": ["PATIENT"],
    "/bind/caregiver": ["CAREGIVER", "FAMILY"],
    "/caregiver/status": ["CAREGIVER", "FAMILY"],
    "/bind/doctor": ["DOCTOR", "NURSE", "THERAPIST", "DIETITIAN"],
    "/doctor/patients": ["DOCTOR", "NURSE", "THERAPIST", "DIETITIAN"],
    "/doctor/detail": ["DOCTOR", "NURSE", "THERAPIST", "DIETITIAN"],
    "/doctor/alerts-review": ["DOCTOR", "NURSE", "THERAPIST", "DIETITIAN"]
};

function root() {
    return document.querySelector("#app");
}

function currentRouteInfo() {
    const params = new URLSearchParams(location.search);
    const hash = location.hash.replace(/^#/, "");
    const [hashPath = "", hashQuery = ""] = hash.split("?");
    const hashParams = new URLSearchParams(hashQuery);
    hashParams.forEach((value, key) => params.set(key, value));
    const path = hashPath || params.get("view") || "/bind/caregiver";
    const token = params.get("token");
    const role = params.get("role");
    if (token) localStorage.setItem("care_access_token", token);
    if (role) localStorage.setItem("care_active_role", role);
    return {
        path: path || "/bind/caregiver",
        params
    };
}

function selectedPatient() {
    const source = livePatients.length ? livePatients : patients;
    return source.find((patient) => patient.id === selectedPatientId) || source[0] || patients[0];
}

async function render() {
    const routeInfo = currentRouteInfo();
    const route = routeInfo.path;
    updateShell(route, routeInfo.params);
    updateNav(route);
    if (!isRouteAllowed(route, routeInfo.params)) {
        renderRouteBlocked(route);
        return;
    }
    await (routes[route] || renderCaregiverBind)();
}

function token() {
    return localStorage.getItem("care_access_token") || "";
}

function role() {
    return localStorage.getItem("care_active_role") || "";
}

function activeRouteRole(params) {
    return (params.get("role") || role()).toUpperCase();
}

function isRouteAllowed(route, params) {
    const allowed = routeRoles[route];
    const activeRole = activeRouteRole(params);
    if (!allowed || !activeRole) return true;
    return allowed.includes(activeRole);
}

async function careApi(path, options = {}) {
    if (!token()) throw new Error("缺少登录 token，请从微信机器人发送的链接进入。");
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token()}`,
            ...(options.headers || {})
        }
    });
    const body = await response.json().catch(() => null);
    if (!response.ok || !body || body.code !== "OK") {
        throw new Error(body?.message || `请求失败：${response.status}`);
    }
    return body.data;
}

async function taskActionApi(path, actionToken, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            "X-Care-Task-Token": actionToken,
            ...(options.headers || {})
        }
    });
    const body = await response.json().catch(() => null);
    if (!response.ok || !body || body.code !== "OK") {
        throw new Error(body?.message || `请求失败：${response.status}`);
    }
    return body.data;
}

async function renderTaskAction() {
    const routeInfo = currentRouteInfo();
    const actionToken = routeInfo.params.get("actionToken") || "";
    if (!actionToken) {
        root().innerHTML = `<section class="single-message"><div class="card"><h1>任务链接无效</h1><div class="message error">缺少任务凭证，请从最新提醒进入。</div></div></section>`;
        return;
    }
    let task;
    try {
        task = await taskActionApi("/api/care/v1/task-actions/current", actionToken);
    } catch (error) {
        root().innerHTML = `<section class="single-message"><div class="card"><h1>任务链接不可用</h1><div class="message error">${escapeHtml(error.message)}</div></div></section>`;
        return;
    }
    const deadline = task.backfillDeadlineAt ? new Date(task.backfillDeadlineAt).toLocaleString() : "";
    root().innerHTML = `
        <section class="single-message task-action-page">
            <div class="card task-action-card">
                <div class="eyebrow">照护任务确认</div>
                <h1>${escapeHtml(normalizePlanText(task.title) || `任务 ${task.taskId}`)}</h1>
                <div class="task-action-meta">${escapeHtml(taskStatusLabel(task.status))}${deadline ? ` · 补卡截止 ${escapeHtml(deadline)}` : ""}</div>
                <div class="task-action-buttons">
                    <button class="button primary" id="task-action-complete" type="button" ${task.canComplete ? "" : "disabled"}>已完成</button>
                    <button class="button secondary" id="task-action-missed" type="button" ${task.canReportMissed ? "" : "disabled"}>未完成</button>
                </div>
                <p class="task-action-note">按钮只确认当前任务，不会修改医生方案。</p>
            </div>
        </section>
    `;
    document.querySelector("#task-action-complete")?.addEventListener("click", () => submitTaskAction(actionToken, "complete"));
    document.querySelector("#task-action-missed")?.addEventListener("click", () => submitTaskAction(actionToken, "missed"));
}

async function submitTaskAction(actionToken, action) {
    const completeButton = document.querySelector("#task-action-complete");
    const missedButton = document.querySelector("#task-action-missed");
    [completeButton, missedButton].forEach((button) => { if (button) button.disabled = true; });
    try {
        await taskActionApi(`/api/care/v1/task-actions/${action}`, actionToken, {
            method: "POST",
            body: JSON.stringify({ note: action === "complete" ? "微信任务页面确认完成" : "微信任务页面确认未完成" })
        });
        root().innerHTML = `<section class="single-message"><div class="card"><h1>${action === "complete" ? "已记录完成" : "已记录未完成"}</h1><div class="message info">${action === "complete" ? "患者和家属端会同步为已完成。" : "系统已通知家属关注患者情况。"}</div></div></section>`;
    } catch (error) {
        [completeButton, missedButton].forEach((button) => { if (button) button.disabled = false; });
        window.alert(error.message);
    }
}

function apiPrefix(kind) {
    if (kind === "patient") return "/api/care/v1/patient";
    if (kind === "family") return "/api/care/v1/family";
    if (kind === "clinical") return "/api/care/v1/clinical";
    const active = role().toUpperCase();
    return active === "DOCTOR" ? "/api/care/v1/clinical" : "/api/care/v1/family";
}

async function renderPatientTasks() {
    let tasks = [];
    let notice = "";
    try {
        tasks = await careApi("/api/care/v1/patient/tasks");
    } catch (error) {
        notice = `<div class="message info">暂时无法读取今日任务：${escapeHtml(error.message)}</div>`;
    }
    const patient = {
        tasks: (tasks || []).map((task) => ({
            id: task.id,
            version: task.version ?? 0,
            statusCode: String(task.status || "").toUpperCase(),
            title: normalizePlanText(task.title || `任务 ${task.id}`) || `任务 ${task.id}`,
            status: taskStatusLabel(task.status),
            detail: normalizePlanText(taskDetailText(task)),
            dueAt: task.dueAt || null,
            completedAt: task.completedAt || null
        }))
    };
    const completedCount = patient.tasks.filter((task) => task.statusCode === "COMPLETED").length;
    root().innerHTML = `
        ${header("患者任务", "今日任务与打卡", "系统会在任务开始时发送微信提醒，请按提醒中的任务编号完成打卡。")}
        ${notice}
        <section class="grid two">
            <div class="card">
                <h2 class="card-title">今日任务</h2>
                ${taskList(patient, { interactive: true, showDetail: false, showTime: true })}
            </div>
            <div class="card">
                <h2 class="card-title">完成情况</h2>
                <div class="summary-grid">
                    <div class="summary-item"><span>今日任务</span><strong>${patient.tasks.length}</strong></div>
                    <div class="summary-item"><span>已完成</span><strong>${completedCount}</strong></div>
                    <div class="summary-item"><span>待确认</span><strong>${patient.tasks.filter((task) => task.statusCode === "PENDING").length}</strong></div>
                </div>
            </div>
        </section>
    `;
    bindTaskActions();
}

async function loadPatients(kind) {
    const users = await careApi(`${apiPrefix(kind)}/patients`);
    livePatients = (users || []).map((user) => {
        const doctors = Array.isArray(user.doctors) ? user.doctors : [];
        return {
            id: String(user.id),
            code: user.userCode,
            name: user.displayName,
            doctors,
            age: "-",
            gender: "-",
            doctor: doctors.length ? doctors.map((doctor) => doctor.displayName || doctor.userCode).join("、") : "未绑定医生",
            family: "已绑定家属",
            risk: "NORMAL",
            riskLabel: "状态待同步",
            lastUpdate: "刚刚",
            taskDone: 0,
            taskTotal: 0,
            water: "查看任务详情",
            safety: "查看状态详情",
            summary: "后端已返回患者绑定关系，点击患者可查看最新状态、任务和告警。",
            tasks: [],
            alerts: [],
            checkins: [],
            plan: "请打开计划详情查看。"
        };
    });
    if (livePatients.length && !livePatients.some((patient) => patient.id === selectedPatientId)) {
        selectedPatientId = livePatients[0].id;
    }
    return livePatients;
}

async function hydratePatientStatus(patient, kind) {
    if (!patient || !token()) return patient;
    const status = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/status`);
    patient.name = status.patientDisplayName || patient.name;
    patient.code = status.patientUserCode || patient.code;
    patient.risk = status.urgentAlertCount > 0 ? "URGENT" : status.openAlertCount > 0 ? "ATTENTION" : "NORMAL";
    patient.riskLabel = status.urgentAlertCount > 0 ? "异常告警" : status.openAlertCount > 0 ? "需要关注" : "状态平稳";
    patient.lastUpdate = status.generatedAt ? new Date(status.generatedAt).toLocaleString() : "刚刚";
    const tasks = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/tasks`).catch(() => []);
    const alerts = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/alerts`).catch(() => []);
    const checkinRange = recentSevenDayRange();
    const checkins = await careApi(
        `${apiPrefix(kind)}/patients/${patient.id}/checkins?from=${checkinRange.from}&to=${checkinRange.to}`
    ).catch(() => []);
    patient.tasks = (tasks || []).filter((task) => String(task.status || "").toUpperCase() !== "CANCELLED").map((task) => ({
        id: task.id,
        version: task.version ?? 0,
        statusCode: String(task.status || "").toUpperCase(),
        taskType: String(task.taskType || "").toUpperCase(),
        title: normalizePlanText(task.title || `任务 ${task.id}`) || `任务 ${task.id}`,
        status: taskStatusLabel(task.status),
        detail: normalizePlanText(taskDetailText(task)),
        dueAt: task.dueAt || null,
        completedAt: task.completedAt || null
    }));
    patient.alerts = (alerts || []).filter((alert) => ["OPEN", "ACKNOWLEDGED", "ESCALATED"].includes(String(alert.status || "").toUpperCase())).map((alert) => ({
        id: alert.id,
        version: Number(alert.version || 0),
        status: String(alert.status || "").toUpperCase(),
        title: formatAlertTitle(alert.alertType),
        level: alert.severity === "URGENT" || alert.severity === "CRITICAL" ? "urgent" : "attention",
        time: alert.detectedAt ? new Date(alert.detectedAt).toLocaleString() : "刚刚",
        detail: alert.evidenceText || alert.status || "请查看告警详情"
    }));
    synchronizePatientTaskSummary(patient, status);
    patient.checkins = (checkins || []).map((item) => ({
        date: item.checkinDate || localDate(item.submittedAt),
        time: item.submittedAt ? new Date(item.submittedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "--:--",
        title: item.incidentType || "每日打卡",
        detail: item.originalText || `睡眠：${item.sleepStatus || "-"}，饮水：${item.hydrationStatus || "-"}`
    }));
    return patient;
}

function synchronizePatientTaskSummary(patient, status) {
    const tasks = patient.tasks || [];
    const completed = tasks.filter((task) => task.statusCode === "COMPLETED").length;
    const overdue = tasks.filter((task) => task.statusCode === "OVERDUE").length;
    const pending = tasks.filter((task) => task.statusCode === "PENDING").length;
    patient.taskDone = completed;
    patient.taskTotal = tasks.length;

    const hydrationTask = tasks.find((task) => task.taskType === "HYDRATION" || task.title.includes("喝水") || task.title.includes("饮水"));
    const safetyTask = tasks.find((task) => task.taskType === "DAILY_CHECKIN" || task.title.includes("安全") || task.title.includes("平安"));
    patient.water = hydrationTask ? taskStatusLabel(hydrationTask.statusCode) : "今日无饮水任务";
    patient.safety = safetyTask ? taskStatusLabel(safetyTask.statusCode) : "今日无安全确认";

    if (overdue > 0 && status.urgentAlertCount === 0) {
        patient.risk = "ATTENTION";
        patient.riskLabel = "任务超时";
    }
    const taskSummary = tasks.length
        ? `今日任务 ${tasks.length} 项，已完成 ${completed} 项，待完成 ${pending} 项，超时 ${overdue} 项。`
        : "今日暂无待执行任务。";
    patient.summary = `${taskSummary} 未处理告警 ${status.openAlertCount} 个，紧急告警 ${status.urgentAlertCount} 个，待确认记忆 ${status.pendingMemoryCount} 条。`;
}

function backendUnavailableMessage(error) {
    return `<div class="message info">当前显示演示数据。真实接口暂不可用：${escapeHtml(error.message)}</div>`;
}

function taskStatusLabel(status) {
    const value = String(status || "").toUpperCase();
    if (value === "COMPLETED") return "已完成";
    if (value === "OVERDUE") return "已超时";
    if (value === "MISSED") return "未完成";
    if (value === "CANCELLED") return "已取消";
    if (value === "SKIPPED") return "已跳过";
    if (value === "PENDING") return "待完成";
    return status || "待处理";
}

function planStatusLabel(status) {
    const value = String(status || "").toUpperCase();
    if (value === "ACTIVE") return "执行中";
    if (value === "APPROVED") return "已审核";
    if (value === "WAITING_REVIEW") return "待审核";
    if (value === "PAUSED") return "已暂停";
    if (value === "COMPLETED") return "已结束";
    if (value === "DRAFT") return "草稿";
    return status || "未同步";
}

function taskStatusClass(statusCode) {
    if (statusCode === "COMPLETED") return "completed";
    if (["OVERDUE", "PENDING"].includes(statusCode)) return "pending";
    if (statusCode === "MISSED") return "missed";
    return "inactive";
}

function taskDetailText(task) {
    const due = task?.dueAt ? new Date(task.dueAt).toLocaleString() : "";
    const instructions = task?.instructions || "";
    if (due && instructions) {
        return `提醒时间：${due} · ${instructions}`;
    }
    return instructions || due || "查看任务详情";
}

function normalizePlanText(value) {
    return String(value || "")
        .replace(/\r\n?/g, "\n")
        .replace(/^\s*#{1,6}\s*/gm, "")
        .replace(/^\s*[*+-]\s+/gm, "")
        .replace(/\*\*(.*?)\*\*/g, "$1")
        .replace(/__(.*?)__/g, "$1")
        .replace(/`([^`]+)`/g, "$1")
        .replace(/[#*]/g, "")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

function planScheduleText(template) {
    const scheduleType = {
        ONCE: "一次性",
        DAILY: "每日",
        WEEKLY: "每周"
    }[String(template?.scheduleType || "").toUpperCase()];
    const parts = [];
    if (scheduleType) parts.push(scheduleType);
    if (template?.localTime) parts.push(`时间 ${String(template.localTime).slice(0, 5)}`);
    if (template?.scheduledDate) parts.push(`日期 ${template.scheduledDate}`);
    if (template?.dayOfWeek) parts.push(`星期 ${template.dayOfWeek}`);
    return parts.join("，");
}

function buildPlanText(patient, version, templates) {
    const sections = [];
    if (version?.summary) sections.push(`方案摘要\n${version.summary}`);
    if (version?.instructions) sections.push(`执行说明\n${version.instructions}`);
    const taskText = templates.map((template, index) => {
        const schedule = planScheduleText(template);
        return [
            `任务 ${index + 1}：${template.title || "未命名任务"}`,
            template.instructions,
            schedule
        ].filter(Boolean).join("\n");
    }).join("\n\n");
    if (taskText) sections.push(`任务安排\n${taskText}`);
    if (!sections.length && patient.plan) sections.push(patient.plan);
    return normalizePlanText(sections.join("\n\n")) || "医生暂未填写详细说明。";
}

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function updateShell(route, params) {
    const standalone = Boolean(routeRoles[route]) && params.get("nav") !== "1";
    document.body.classList.toggle("standalone-view", standalone);
}

function renderRouteBlocked(route) {
    root().innerHTML = `
        <section class="single-message">
            <div class="card">
                <h1>当前身份不能访问这个页面</h1>
                <p class="header-text">请从机器人发送给你的业务链接进入对应界面，不要手动切换到其他身份的页面。</p>
                <div class="message info">当前页面：${escapeHtml(route)}</div>
            </div>
        </section>
    `;
}

function updateNav(route) {
    document.querySelectorAll(".nav-list a").forEach((item) => {
        item.classList.toggle("active", item.getAttribute("href") === `#${route}`);
    });
}

function header(kicker, title, text, actions = "") {
    const showText = text && !document.body.classList.contains("standalone-view");
    return `
        <header class="page-header">
            <div>
                <div class="eyebrow">${kicker}</div>
                <h1>${title}</h1>
                ${showText ? `<p class="header-text">${text}</p>` : ""}
            </div>
            <div class="toolbar">${actions}</div>
        </header>
    `;
}

function renderCaregiverBind() {
    renderBindPage({
        role: "家属",
        title: "家属绑定患者",
        description: "家属通过后端生成的一次性链接绑定患者。前端负责填写关系信息，后端负责权限和 token 校验。",
        relationLabel: "家属关系",
        relationOptions: ["母亲", "父亲", "配偶", "子女", "其他家属"],
        extraField: `
            <div class="field">
                <label for="pushRule">状态推送频率</label>
                <select id="pushRule">
                    <option>每天 20:00 推送总结</option>
                    <option>每 6 小时推送一次</option>
                    <option>仅异常时推送</option>
                </select>
            </div>
        `
    });
}

function renderDoctorBind() {
    renderBindPage({
        role: "医生",
        title: "医生绑定患者",
        description: "医生绑定患者后，可以查看患者状态、处理告警、制定计划和调整任务。",
        relationLabel: "医生关系",
        relationOptions: ["主治医生", "随访医生", "康复医生"],
        extraField: `
            <div class="field">
                <label for="permission">权限范围</label>
                <select id="permission">
                    <option>查看状态 + 制定任务 + 调整方案</option>
                    <option>仅查看状态和任务</option>
                </select>
            </div>
        `
    });
}

function renderBindPage(config) {
    root().innerHTML = `
        ${header(config.role === "医生" ? "医生绑定" : "家属绑定", config.title, config.description)}
        <section class="grid two">
            <form class="card form-grid" data-bind-form>
                <h2 class="card-title">绑定信息</h2>
                <div class="field">
                    <label for="patientCode">患者编号</label>
                    <input id="patientCode" placeholder="例如 PAT-12345678" autocomplete="off">
                </div>
                <div class="field">
                    <label for="relation">${config.relationLabel}</label>
                    <select id="relation">
                        ${config.relationOptions.map((item) => `<option>${item}</option>`).join("")}
                    </select>
                </div>
                ${config.extraField}
                <button class="button primary" type="submit">确认绑定</button>
            </form>
            <div class="card">
                <h2 class="card-title">患者预览</h2>
                <div data-patient-preview>
                    <div class="empty-state">绑定患者后，这里会显示真实患者信息、绑定医生和最近状态。</div>
                </div>
                <div class="message info" data-bind-result>提交后由后端确认 token、身份和关系白名单。</div>
            </div>
        </section>
    `;
    document.querySelector("[data-bind-form]").addEventListener("submit", (event) => {
        event.preventDefault();
        submitBinding(config);
    });
    refreshBindPreview(bindKind(config));
}

async function submitBinding(config) {
        const code = document.querySelector("#patientCode").value.trim();
        const relation = document.querySelector("#relation").value;
        const result = document.querySelector("[data-bind-result]");
        if (!code) {
            result.className = "message info";
            result.textContent = "请先填写患者编号。";
            return;
        }
        try {
            const kind = bindKind(config);
            await careApi(`${apiPrefix(kind)}/bindings`, {
                method: "POST",
                body: JSON.stringify({
                    patientUserCode: code,
                    relationLabel: relation
                })
            });
            result.className = "message success";
            result.textContent = `${config.role}绑定成功：患者 ${code}，关系为 ${relation}。`;
            await refreshBindPreview(kind, code);
        } catch (error) {
            result.className = "message info";
            result.textContent = error.message;
        }
}

function bindKind(config) {
    return config.role === "医生" ? "clinical" : "family";
}

async function refreshBindPreview(kind, preferredPatientCode = "") {
    const preview = document.querySelector("[data-patient-preview]");
    if (!preview || !token()) return;
    try {
        await loadPatients(kind);
        let patient = selectedPatient();
        if (preferredPatientCode) {
            patient = livePatients.find((item) => item.code === preferredPatientCode) || patient;
            if (patient) selectedPatientId = patient.id;
        }
        if (!patient) {
            preview.innerHTML = `<div class="empty-state">当前还没有绑定患者。</div>`;
            return;
        }
        await hydratePatientStatus(patient, kind).catch(() => patient);
        preview.innerHTML = patientPreview(patient);
    } catch (error) {
        preview.innerHTML = `<div class="message info">暂时无法同步患者预览：${escapeHtml(error.message)}</div>`;
    }
}

async function renderCaregiverStatus() {
    let patient = selectedPatient();
    let notice = "";
    try {
        await loadPatients("family");
        if (!livePatients.length) {
            root().innerHTML = `
                ${header("家属查看", "患者状态", "先绑定患者，才能查看任务、打卡和异常情况。", `<a class="button primary" href="#/bind/caregiver">绑定患者</a>`)}
                <div class="empty-state">当前没有绑定患者。请先在家属绑定患者页面完成绑定。</div>
            `;
            return;
        }
        patient = await hydratePatientStatus(selectedPatient(), "family");
    } catch (error) {
        notice = backendUnavailableMessage(error);
        patient = patients[0];
    }
    root().innerHTML = `
        ${header("家属查看", `${patient.name}的今日状态`, "家属端优先展示当天状态、任务完成情况和异常提醒。", `<button class="button" type="button" data-health-record>新增健康记录</button><button class="button primary" type="button" data-contact-doctor>联系医生</button>`)}
        ${notice}
        ${patientTabs()}
        ${summaryGrid(patient)}
        ${carePlanSection(patient)}
        <section class="grid two">
            <div class="card">
                <h2 class="card-title">任务与打卡</h2>
                ${taskList(patient, { interactive: true, showTime: true })}
            </div>
            <div class="card">
                <h2 class="card-title">异常与建议</h2>
                ${alertList(patient)}
                <div class="message info">可由后端把当前状态整理成邮件发给医生。</div>
            </div>
        </section>
    `;
    bindPatientTabs(renderCaregiverStatus);
    bindSummaryJumps();
    bindTaskActions();
    const contactButton = document.querySelector("[data-contact-doctor]");
    if (contactButton) {
        contactButton.addEventListener("click", () => contactDoctor(patient));
    }
    bindHealthRecordAction(patient, "family", renderCaregiverStatus);
    scheduleCaregiverStatusRefresh();
}

function scheduleCaregiverStatusRefresh() {
    clearTimeout(caregiverRefreshTimer);
    caregiverRefreshTimer = window.setTimeout(() => {
        if (currentRouteInfo().path === "/caregiver/status") {
            void renderCaregiverStatus();
        }
    }, 30_000);
}

async function renderDoctorSwitcher() {
    let notice = "";
    let loadedRealPatients = false;
    try {
        await loadPatients("clinical");
        loadedRealPatients = true;
        if (!livePatients.length) {
            root().innerHTML = `
                ${header("医生工作台", "患者切换", "医生可以在多个患者之间快速切换，先看状态，再进入详情或处理告警。")}
                <div class="empty-state">当前没有绑定患者。你可以通过医生绑定患者页面添加新的患者。</div>
            `;
            return;
        }
        await hydratePatientStatus(selectedPatient(), "clinical");
    } catch (error) {
        notice = backendUnavailableMessage(error);
    }
    if (loadedRealPatients && !livePatients.length) return;
    const patient = selectedPatient();
    root().innerHTML = `
        ${header("医生工作台", "患者切换", "医生可以在多个患者之间快速切换，先看状态，再进入详情或处理告警。", `
            <button class="button" type="button" data-transfer-doctor>切换医生</button>
            <button class="button danger" type="button" data-unbind-patient>解除绑定</button>
            <a class="button primary" href="#/doctor/detail">查看详情</a>
        `)}
        ${notice}
        ${patientTabs()}
        ${summaryGrid(patient)}
        <section class="grid two">
            <div class="card">
                <h2 class="card-title">当前任务</h2>
                ${taskList(patient)}
            </div>
            <div class="card">
                <h2 class="card-title">告警概览</h2>
                ${alertList(patient)}
            </div>
        </section>
    `;
    bindSummaryJumps();
    bindPatientTabs(renderDoctorSwitcher);
    bindDoctorPatientActions(patient);
}

async function renderDoctorDetail() {
    let notice = "";
    try {
        await loadPatients("clinical");
        await hydratePatientStatus(selectedPatient(), "clinical");
    } catch (error) {
        notice = backendUnavailableMessage(error);
    }
    const patient = selectedPatient();
    root().innerHTML = `
        ${header("患者详情", `${patient.name} · ${patient.id}`, "集中查看患者基本信息、最近 7 天打卡和风险提示。", `<a class="button" href="#/doctor/patients">返回患者列表</a><button class="button" type="button" data-health-record>新增健康记录</button><a class="button primary" href="#/doctor/alerts-review">处理告警</a>`)}
        ${notice}
        ${patientTabs()}
        <section class="detail-layout">
            <div class="card">
                <h2 class="card-title">基本信息</h2>
                ${patientProfile(patient)}
            </div>
            <div class="card">
                <h2 class="card-title">最近记录</h2>
                ${recentSevenDayCheckins(patient.checkins)}
            </div>
        </section>
    `;
    bindPatientTabs(renderDoctorDetail);
    bindHealthRecordAction(patient, "clinical", renderDoctorDetail);
}

function formatAlertTitle(type) {
    const labels = {
        BLOOD_PRESSURE_ELEVATED: "血压偏高",
        BLOOD_PRESSURE_CRITICAL: "血压紧急偏高",
        TEMPERATURE_ELEVATED: "体温偏高",
        TEMPERATURE_ELEVATED_URGENT: "体温紧急偏高",
        HEART_RATE_ABNORMAL: "心率异常",
        HEART_RATE_CRITICAL: "心率紧急异常",
        OXYGEN_SATURATION_LOW: "血氧偏低",
        OXYGEN_SATURATION_CRITICAL: "血氧紧急偏低",
        BLOOD_GLUCOSE_ELEVATED: "血糖偏高",
        BLOOD_GLUCOSE_ELEVATED_URGENT: "血糖紧急偏高",
        EMERGENCY_SYMPTOM_REPORTED: "患者上报紧急症状",
        TASK_OVERDUE: "照护任务超时"
    };
    return labels[type] || type || "患者告警";
}

function healthCategoryLabel(category) {
    return {
        BLOOD_PRESSURE: "血压",
        BLOOD_GLUCOSE: "血糖",
        TEMPERATURE: "体温",
        HEART_RATE: "心率",
        OXYGEN_SATURATION: "血氧",
        WEIGHT: "体重",
        MEDICATION: "用药情况",
        SYMPTOM: "症状",
        SAFETY_STATUS: "安全情况",
        OTHER: "其他"
    }[category] || category;
}

function healthFields(category) {
    if (category === "BLOOD_PRESSURE") {
        return `<div class="grid two"><div class="field"><label>收缩压（mmHg）<input name="primaryValue" type="number" min="0" step="0.1" required></label></div><div class="field"><label>舒张压（mmHg）<input name="secondaryValue" type="number" min="0" step="0.1" required></label></div></div>`;
    }
    if (["MEDICATION", "SYMPTOM", "SAFETY_STATUS", "OTHER"].includes(category)) {
        return `<div class="field"><label>记录内容<textarea name="recordText" rows="5" required placeholder="填写一项具体情况"></textarea></label></div>`;
    }
    const unit = {
        BLOOD_GLUCOSE: "mmol/L",
        TEMPERATURE: "°C",
        HEART_RATE: "bpm",
        OXYGEN_SATURATION: "%",
        WEIGHT: "kg"
    }[category] || "";
    return `<div class="field"><label>数值${unit ? `（${unit}）` : ""}<input name="primaryValue" type="number" min="0" step="0.1" required></label></div><input type="hidden" name="unit" value="${unit}"><div class="field"><label>补充说明（可选）<textarea name="recordText" rows="3" placeholder="例如测量时间、身体感受"></textarea></label></div>`;
}

function bindHealthRecordAction(patient, kind, rerender) {
    const button = document.querySelector("[data-health-record]");
    if (button) button.addEventListener("click", () => openHealthRecordModal(patient, kind, rerender));
}

function openHealthRecordModal(patient, kind, rerender) {
    const overlay = document.createElement("div");
    overlay.className = "modal-backdrop";
    overlay.innerHTML = `
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="health-record-title">
            <div class="modal-header"><div><h2 id="health-record-title">新增健康记录</h2><div class="item-meta">${escapeHtml(patient.name)} · 一次只记录一个项目</div></div><button class="button icon-button" type="button" data-modal-close aria-label="关闭">×</button></div>
            <div class="field"><label>记录项目<select name="healthCategory"><option value="BLOOD_PRESSURE">血压</option><option value="BLOOD_GLUCOSE">血糖</option><option value="TEMPERATURE">体温</option><option value="HEART_RATE">心率</option><option value="OXYGEN_SATURATION">血氧</option><option value="WEIGHT">体重</option><option value="MEDICATION">用药情况</option><option value="SYMPTOM">症状</option><option value="SAFETY_STATUS">安全情况</option><option value="OTHER">其他</option></select></label></div>
            <div data-health-fields></div>
            <div class="toolbar modal-actions"><button class="button" type="button" data-modal-close>取消</button><button class="button primary" type="button" data-health-submit>保存记录</button></div>
        </div>`;
    document.body.appendChild(overlay);
    const category = overlay.querySelector("[name=healthCategory]");
    const renderFields = () => {
        overlay.querySelector("[data-health-fields]").innerHTML = healthFields(category.value);
    };
    renderFields();
    category.addEventListener("change", renderFields);
    overlay.querySelectorAll("[data-modal-close]").forEach((button) => button.addEventListener("click", () => overlay.remove()));
    overlay.querySelector("[data-health-submit]").addEventListener("click", async () => {
        const valueOf = (name) => overlay.querySelector(`[name="${name}"]`)?.value || "";
        const number = (name) => valueOf(name) === "" ? null : Number(valueOf(name));
        const payload = {
            category: category.value,
            primaryValue: number("primaryValue"),
            secondaryValue: number("secondaryValue"),
            unit: valueOf("unit") || (category.value === "BLOOD_PRESSURE" ? "mmHg" : ""),
            recordText: valueOf("recordText"),
            idempotencyKey: `web-${Date.now()}-${Math.random().toString(36).slice(2)}`
        };
        const submit = overlay.querySelector("[data-health-submit]");
        submit.disabled = true;
        try {
            const result = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/health-records`, { method: "POST", body: JSON.stringify(payload) });
            overlay.remove();
            window.alert(result.alert ? `记录已保存，并生成${result.alert.severity === "URGENT" ? "紧急" : "关注"}告警。` : "健康记录已保存。");
            await rerender();
        } catch (error) {
            submit.disabled = false;
            window.alert(error.message);
        }
    });
}

function recentSevenDayCheckins(checkins) {
    const days = recentSevenDays();
    const itemsByDate = new Map();
    (checkins || []).forEach((item) => {
        const date = item.date || "";
        if (!itemsByDate.has(date)) itemsByDate.set(date, []);
        itemsByDate.get(date).push(item);
    });
    return `<div class="timeline">${days.map((day) => {
        const items = itemsByDate.get(day.value) || [];
        return `
            <div class="timeline-item">
                <div class="item-title">${day.label}</div>
                ${items.length ? items.map((item) => `
                    <div class="item-meta">${escapeHtml(item.time)} ${escapeHtml(item.title)}：${escapeHtml(item.detail)}</div>
                `).join("") : '<div class="item-meta">暂无打卡</div>'}
            </div>
        `;
    }).join("")}</div>`;
}

function recentSevenDayRange() {
    const days = recentSevenDays();
    return { from: days[0].value, to: days[days.length - 1].value };
}

function recentSevenDays() {
    const today = new Date();
    return Array.from({ length: 7 }, (_, index) => {
        const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (6 - index));
        const value = localDate(date);
        return { value, label: `${value} 周${["日", "一", "二", "三", "四", "五", "六"][date.getDay()]}` };
    });
}

function localDate(value) {
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

async function renderAlertsAndReview() {
    let urgentPatients = [];
    let draftList = [];
    let draftDetail = null;
    let notice = "";
    try {
        await loadPatients("clinical");
        await Promise.all(livePatients.map((patient) => hydratePatientStatus(patient, "clinical").catch(() => patient)));
        urgentPatients = livePatients.filter((patient) => patient.alerts.length > 0);
        draftList = await careApi(`${apiPrefix("clinical")}/plan-drafts`);
        const routeDraftId = routeParams().get("draftId");
        const selectedId = routeDraftId || draftList.find((draft) => draft.status === "待审核")?.id || draftList[0]?.id || "";
        if (selectedId) {
            draftDetail = await careApi(`${apiPrefix("clinical")}/plan-drafts/${selectedId}`);
        }
    } catch (error) {
        notice = backendUnavailableMessage(error);
        urgentPatients = [];
        draftList = [];
    }
    const activeDraftId = draftDetail?.id || draftList.find((draft) => draft.status === "待审核")?.id || draftList[0]?.id || "";
    root().innerHTML = `
        ${header("告警与审核", "告警中心和方案审核", "", `<button class="button" type="button" data-alert-resolve-attention>批量解决关注告警</button><button class="button primary" type="button" data-draft-confirm-header>确认当前方案</button>`)}
        ${notice}
        <section class="grid two">
            <div class="card">
                <h2 class="card-title">告警中心</h2>
                <div class="list">
                    ${urgentPatients.flatMap((patient) => patient.alerts.map((alert) => `
                        <div class="list-item">
                            <div class="list-row">
                                <div>
                                    <div class="item-title">${patient.name} · ${alert.title}</div>
                                    <div class="item-meta">${alert.time} · ${alert.detail}</div>
                                </div>
                                <span class="badge ${alert.level === "urgent" ? "red" : "amber"}">${alert.level === "urgent" ? "紧急" : "关注"}</span>
                            </div>
                            <div class="toolbar" style="margin-top: 10px;">
                                ${alert.status === "OPEN" ? `<button class="button" type="button" data-alert-acknowledge data-alert-id="${alert.id}" data-alert-version="${alert.version}">确认关注</button>` : ""}
                                <button class="button ${alert.level === "urgent" ? "primary" : ""}" type="button" data-alert-resolve data-alert-id="${alert.id}" data-alert-version="${alert.version}">标记已处理</button>
                            </div>
                        </div>
                    `)).join("") || `<div class="empty-state">当前没有告警。</div>`}
                </div>
            </div>
            <div class="card">
                <h2 class="card-title">方案审核</h2>
                <div class="draft-list">
                    ${draftList.length ? draftList.map((draft) => `
                        <button class="draft-item ${draft.id === activeDraftId ? "active" : ""}" data-draft-id="${draft.id}" type="button">
                            <div class="list-row">
                                <div>
                                    <div class="item-title">${draft.patientName} · ${draft.title}</div>
                                    <div class="item-meta">${draft.patientCode} · ${draft.status}</div>
                                </div>
                                <span class="badge ${draft.status === "已发送" ? "green" : "blue"}">${draft.status}</span>
                            </div>
                        </button>
                    `).join("") : `<div class="empty-state">当前没有待审核方案。</div>`}
                </div>
                ${draftDetail ? draftDetailPanel(draftDetail) : ""}
            </div>
        </section>
    `;
    bindDraftActions(draftDetail);
    bindAlertActions(urgentPatients);
}

function draftDetailPanel(draft) {
    return `
        <div class="draft-detail">
            <div class="list-item">
                <div class="list-row">
                    <div>
                        <div class="item-title">${draft.patientName} · ${draft.title}</div>
                        <div class="item-meta">${draft.patientCode} · ${draft.status}</div>
                    </div>
                    <span class="badge ${draft.status === "已发送" ? "green" : "blue"}">${draft.status}</span>
                </div>
            </div>
            <div class="field">
                <label for="draft-title">方案标题</label>
                <input id="draft-title" value="${escapeHtml(draft.title || "")}">
            </div>
            <div class="field">
                <label>医生原始要求</label>
                <div class="message info">${escapeHtml(draft.doctorInput || "")}</div>
            </div>
            <div class="field">
                <label>机器人优化建议</label>
                <div class="message info">${escapeHtml(draft.refinedPlan || "")}</div>
            </div>
            <div class="field">
                <label for="draft-final-plan">发送内容</label>
                <textarea id="draft-final-plan" rows="10">${escapeHtml(draft.editedPlan || draft.refinedPlan || "")}</textarea>
            </div>
            <div class="toolbar" style="margin-top: 14px;">
                <button class="button" type="button" data-draft-save ${draft.editable ? "" : "disabled"}>保存修改</button>
                <button class="button primary" type="button" data-draft-confirm>${draft.editable ? "确认并发送给患者" : "同步任务详情"}</button>
            </div>
            ${draft.confirmedAt ? `<div class="message success" style="margin-top: 12px;">已发送给患者：${new Date(draft.confirmedAt).toLocaleString()}</div>` : ""}
        </div>
    `;
}

function bindDraftActions(draft) {
    document.querySelectorAll("[data-draft-id]").forEach((button) => {
        button.addEventListener("click", () => selectDraft(button.dataset.draftId));
    });
    const headerConfirmButton = document.querySelector("[data-draft-confirm-header]");
    if (headerConfirmButton) {
        headerConfirmButton.disabled = !draft || !draft.editable;
        headerConfirmButton.addEventListener("click", () => {
            document.querySelector("[data-draft-confirm]")?.click();
        });
    }
    if (!draft) return;
    const saveButton = document.querySelector("[data-draft-save]");
    const confirmButton = document.querySelector("[data-draft-confirm]");
    if (saveButton) {
        saveButton.addEventListener("click", async () => {
            try {
                await careApi(`${apiPrefix("clinical")}/plan-drafts/${draft.id}`, {
                    method: "PATCH",
                    body: JSON.stringify(draftFormPayload())
                });
                await renderAlertsAndReview();
            } catch (error) {
                window.alert(error.message);
            }
        });
    }
    if (confirmButton) {
        confirmButton.addEventListener("click", async () => {
            try {
                if (draft.editable) {
                    await careApi(`${apiPrefix("clinical")}/plan-drafts/${draft.id}`, {
                        method: "PATCH",
                        body: JSON.stringify(draftFormPayload())
                    });
                }
                await careApi(`${apiPrefix("clinical")}/plan-drafts/${draft.id}/confirm`, {
                    method: "POST"
                });
                await renderAlertsAndReview();
            } catch (error) {
                window.alert(error.message);
            }
        });
    }
}

function bindAlertActions(patients) {
    document.querySelectorAll("[data-alert-acknowledge]").forEach((button) => {
        button.addEventListener("click", async () => {
            await updateAlert(button, "acknowledge", {
                version: Number(button.dataset.alertVersion || 0),
                note: "医生已确认关注该告警"
            });
        });
    });
    document.querySelectorAll("[data-alert-resolve]").forEach((button) => {
        button.addEventListener("click", async () => {
            await updateAlert(button, "resolve", {
                version: Number(button.dataset.alertVersion || 0),
                falseAlarm: false,
                note: "医生已处理该告警"
            });
        });
    });

    const resolveAttentionButton = document.querySelector("[data-alert-resolve-attention]");
    if (!resolveAttentionButton) return;
    const attentionAlerts = patients.flatMap((patient) => patient.alerts)
        .filter((alert) => alert.level === "attention");
    resolveAttentionButton.disabled = attentionAlerts.length === 0;
    resolveAttentionButton.addEventListener("click", async () => {
        if (!attentionAlerts.length || !window.confirm(`确认将 ${attentionAlerts.length} 条关注告警标记为已处理？`)) return;
        resolveAttentionButton.disabled = true;
        try {
            const results = await Promise.allSettled(attentionAlerts.map((alert) => careApi(
                `${apiPrefix("clinical")}/alerts/${alert.id}/resolve`, {
                    method: "POST",
                    body: JSON.stringify({
                        version: alert.version,
                        falseAlarm: false,
                        note: "医生批量处理关注告警"
                    })
                }
            )));
            const failed = results.filter((result) => result.status === "rejected").length;
            if (failed) {
                window.alert(`${attentionAlerts.length - failed} 条关注告警已处理，${failed} 条处理失败，请刷新后重试。`);
            }
            await renderAlertsAndReview();
        } catch (error) {
            window.alert(error.message);
            resolveAttentionButton.disabled = false;
        }
    });
}

async function updateAlert(button, action, payload) {
    if (button.disabled) return;
    button.disabled = true;
    try {
        await careApi(`${apiPrefix("clinical")}/alerts/${button.dataset.alertId}/${action}`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        await renderAlertsAndReview();
    } catch (error) {
        button.disabled = false;
        window.alert(error.message);
    }
}

function draftFormPayload() {
    return {
        title: document.querySelector("#draft-title")?.value || "",
        editedPlan: document.querySelector("#draft-final-plan")?.value || ""
    };
}

function selectDraft(draftId) {
    const params = routeParams();
    const next = new URLSearchParams();
    const currentToken = params.get("token") || token();
    const currentRole = params.get("role") || role();
    if (currentToken) {
        next.set("token", currentToken);
    }
    if (currentRole) {
        next.set("role", currentRole);
    }
    next.set("draftId", draftId);
    location.hash = `#/doctor/alerts-review?${next.toString()}`;
}

function routeParams() {
    const hash = location.hash.replace(/^#/, "");
    const query = hash.includes("?") ? hash.split("?")[1] : "";
    return new URLSearchParams(query);
}

function patientTabs() {
    const source = livePatients.length ? livePatients : patients;
    return `
        <div class="patient-switcher">
            ${source.map((patient) => `
                <button class="patient-tab ${patient.id === selectedPatientId ? "active" : ""}" data-patient-id="${patient.id}">
                    <div class="item-title">${patient.name}</div>
                    <div class="item-meta">${patient.code || patient.id} · ${patient.riskLabel}</div>
                </button>
            `).join("")}
        </div>
    `;
}

async function contactDoctor(patient) {
    const resultPayload = await showDoctorContactDialog(patient);
    if (!resultPayload) return;
    try {
        const result = await careApi(`/api/care/v1/family/patients/${patient.id}/doctor-messages`, {
            method: "POST",
            body: JSON.stringify({
                doctorUserIds: resultPayload.doctorUserIds,
                message: resultPayload.message
            })
        });
        window.alert(`已提交给 ${resultPayload.doctorNames.join("、")}。即时送达 ${result.deliveredCount || 0} 位，排队 ${result.queuedCount || 0} 位。`);
    } catch (error) {
        window.alert(error.message);
    }
}

async function loadContactDoctors(patient) {
    let doctors = Array.isArray(patient.doctors) ? patient.doctors : [];
    try {
        doctors = await careApi(`/api/care/v1/family/patients/${patient.id}/doctors`);
        patient.doctors = doctors;
        patient.doctor = doctors.length ? doctors.map((doctor) => doctor.displayName || doctor.userCode).join("、") : "未绑定医生";
    } catch (error) {
        if (!doctors.length) {
            window.alert(error.message);
            return null;
        }
    }
    if (!doctors.length) {
        window.alert("当前患者还没有绑定可联系的医生。");
        return null;
    }
    return doctors;
}

async function showDoctorContactDialog(patient) {
    const doctors = await loadContactDoctors(patient);
    if (!doctors) return null;
    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        overlay.className = "modal-backdrop";
        overlay.innerHTML = `
            <div class="modal" role="dialog" aria-modal="true" aria-labelledby="doctor-contact-title">
                <div class="modal-header">
                    <div>
                        <div class="eyebrow">联系医生</div>
                        <h2 id="doctor-contact-title">选择接收医生</h2>
                    </div>
                    <button class="button icon-button" type="button" data-modal-close aria-label="关闭">×</button>
                </div>
                <div class="field">
                    <label>已绑定当前患者的医生</label>
                    <div class="doctor-picker">
                        ${doctors.map((doctor, index) => `
                            <label class="doctor-option">
                                <input type="checkbox" value="${doctor.id}" ${doctors.length === 1 || index === 0 ? "checked" : ""}>
                                <span>
                                    <strong>${escapeHtml(doctor.displayName || "医生")}</strong>
                                    <small>${escapeHtml(doctor.userCode || `#${doctor.id}`)}</small>
                                </span>
                            </label>
                        `).join("")}
                    </div>
                </div>
                <div class="field">
                    <label for="doctor-contact-message">发送内容</label>
                    <textarea id="doctor-contact-message" rows="5">${escapeHtml(`${patient.name}当前状态需要医生关注。`)}</textarea>
                </div>
                <div class="toolbar modal-actions">
                    <button class="button" type="button" data-modal-close>取消</button>
                    <button class="button primary" type="button" data-modal-send>发送</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);
        const close = (value) => {
            overlay.remove();
            resolve(value);
        };
        overlay.querySelectorAll("[data-modal-close]").forEach((button) => {
            button.addEventListener("click", () => close(null));
        });
        overlay.addEventListener("click", (event) => {
            if (event.target === overlay) {
                close(null);
            }
        });
        overlay.querySelector("[data-modal-send]").addEventListener("click", () => {
            const selectedIds = Array.from(overlay.querySelectorAll(".doctor-option input:checked"))
                .map((input) => Number(input.value))
                .filter((value) => Number.isFinite(value));
            const message = overlay.querySelector("#doctor-contact-message").value.trim();
            if (!selectedIds.length) {
                window.alert("请至少选择一位医生。");
                return;
            }
            if (!message) {
                window.alert("请填写要发送给医生的内容。");
                return;
            }
            const selectedDoctors = doctors.filter((doctor) => selectedIds.includes(Number(doctor.id)));
            close({
                doctorUserIds: selectedIds,
                doctorNames: selectedDoctors.map((doctor) => doctor.displayName || doctor.userCode || "医生"),
                message
            });
        });
        overlay.querySelector("#doctor-contact-message").focus();
    });
}

function bindDoctorPatientActions(patient) {
    const transferButton = document.querySelector("[data-transfer-doctor]");
    const unbindButton = document.querySelector("[data-unbind-patient]");
    if (transferButton) {
        transferButton.addEventListener("click", () => transferPatientDoctor(patient));
    }
    if (unbindButton) {
        unbindButton.addEventListener("click", () => unbindSelectedPatient(patient));
    }
}

async function transferPatientDoctor(patient) {
    const payload = await showDoctorTransferDialog(patient);
    if (!payload) return;
    try {
        const result = await careApi(`/api/care/v1/clinical/patients/${patient.id}/doctor-transfer`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        window.alert(`已将患者 ${result.patientDisplayName || patient.name} 转移给 ${result.toDoctorName || payload.targetDoctorUserCode}。`);
        selectedPatientId = "";
        await renderDoctorSwitcher();
    } catch (error) {
        window.alert(error.message);
    }
}

function showDoctorTransferDialog(patient) {
    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        overlay.className = "modal-backdrop";
        overlay.innerHTML = `
            <div class="modal" role="dialog" aria-modal="true" aria-labelledby="doctor-transfer-title">
                <div class="modal-header">
                    <div>
                        <div class="eyebrow">切换医生</div>
                        <h2 id="doctor-transfer-title">转移 ${escapeHtml(patient.name)} 的负责医生</h2>
                    </div>
                    <button class="button icon-button" type="button" data-modal-close aria-label="关闭">×</button>
                </div>
                <div class="message warn">
                    确认后，当前医生将解除与该患者的绑定；新医生会获得该患者的信息访问和方案管理权限。
                </div>
                <div class="field">
                    <label for="target-doctor-code">新医生编号</label>
                    <input id="target-doctor-code" placeholder="例如 DOC-12345678" autocomplete="off">
                </div>
                <div class="field">
                    <label for="target-doctor-relation">关系说明</label>
                    <input id="target-doctor-relation" value="转入医生" autocomplete="off">
                </div>
                <div class="toolbar modal-actions">
                    <button class="button" type="button" data-modal-close>取消</button>
                    <button class="button primary" type="button" data-modal-submit>确认转移</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);
        const close = (value) => {
            overlay.remove();
            resolve(value);
        };
        overlay.querySelectorAll("[data-modal-close]").forEach((button) => {
            button.addEventListener("click", () => close(null));
        });
        overlay.addEventListener("click", (event) => {
            if (event.target === overlay) close(null);
        });
        overlay.querySelector("[data-modal-submit]").addEventListener("click", () => {
            const targetDoctorUserCode = overlay.querySelector("#target-doctor-code").value.trim();
            const relationLabel = overlay.querySelector("#target-doctor-relation").value.trim();
            if (!targetDoctorUserCode) {
                window.alert("请填写新医生编号。");
                return;
            }
            close({
                targetDoctorUserCode,
                relationLabel: relationLabel || "转入医生"
            });
        });
        overlay.querySelector("#target-doctor-code").focus();
    });
}

async function unbindSelectedPatient(patient) {
    const confirmed = await showUnbindPatientDialog(patient);
    if (!confirmed) return;
    try {
        await careApi(`/api/care/v1/clinical/patients/${patient.id}/unbind`, {
            method: "POST"
        });
        window.alert(`已解除与患者 ${patient.name} 的绑定。`);
        selectedPatientId = "";
        await renderDoctorSwitcher();
    } catch (error) {
        window.alert(error.message);
    }
}

function showUnbindPatientDialog(patient) {
    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        overlay.className = "modal-backdrop";
        overlay.innerHTML = `
            <div class="modal" role="dialog" aria-modal="true" aria-labelledby="unbind-patient-title">
                <div class="modal-header">
                    <div>
                        <div class="eyebrow">解除绑定</div>
                        <h2 id="unbind-patient-title">确认解除患者绑定</h2>
                    </div>
                    <button class="button icon-button" type="button" data-modal-close aria-label="关闭">×</button>
                </div>
                <div class="message warn">
                    你将解除与患者 ${escapeHtml(patient.name)}（${escapeHtml(patient.code || patient.id)}）的绑定。
                    解除后，当前医生将不能继续查看该患者信息、处理告警或调整方案。
                </div>
                <div class="toolbar modal-actions">
                    <button class="button" type="button" data-modal-close>取消</button>
                    <button class="button danger" type="button" data-modal-confirm>确认解除</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);
        const close = (value) => {
            overlay.remove();
            resolve(value);
        };
        overlay.querySelectorAll("[data-modal-close]").forEach((button) => {
            button.addEventListener("click", () => close(false));
        });
        overlay.addEventListener("click", (event) => {
            if (event.target === overlay) close(false);
        });
        overlay.querySelector("[data-modal-confirm]").addEventListener("click", () => close(true));
    });
}

function bindPatientTabs(callback) {
    document.querySelectorAll("[data-patient-id]").forEach((button) => {
        button.addEventListener("click", () => {
            selectedPatientId = button.dataset.patientId;
            callback();
        });
    });
}

function bindSummaryJumps() {
    const detailCards = document.querySelectorAll(".grid.two > .card");
    if (detailCards[0] && !detailCards[0].id) {
        detailCards[0].id = "task-detail";
    }
    if (detailCards[1] && !detailCards[1].id) {
        detailCards[1].id = "status-detail";
    }

    const jumpTargets = ["", "task-detail", "status-detail"];
    document.querySelectorAll(".summary-card").forEach((card, index) => {
        const targetId = jumpTargets[index];
        if (!targetId) {
            return;
        }
        card.classList.add("summary-action");
        card.setAttribute("role", "button");
        card.setAttribute("tabindex", "0");
        card.dataset.jumpTarget = targetId;
        const jump = () => {
            const target = document.getElementById(targetId);
            if (!target) {
                return;
            }
            target.scrollIntoView({ behavior: "smooth", block: "start" });
            target.classList.add("jump-highlight");
            window.setTimeout(() => target.classList.remove("jump-highlight"), 1200);
        };
        card.addEventListener("click", jump);
        card.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                jump();
            }
        });
    });
}

function summaryGrid(patient) {
    return `
        <section class="grid three" style="margin-bottom: 16px;">
            <div class="card summary-card">
                <span class="badge ${riskClass(patient.risk)}">${patient.riskLabel}</span>
                <div class="summary-value">${patient.taskDone}/${patient.taskTotal}</div>
                <div class="summary-label">今日任务完成</div>
            </div>
            <div class="card summary-card">
                <span class="badge blue">饮水记录</span>
                <div class="summary-value">${patient.water}</div>
                <div class="summary-label">最近更新：${patient.lastUpdate}</div>
            </div>
            <div class="card summary-card">
                <span class="badge green">安全确认</span>
                <div class="summary-value">${patient.safety}</div>
                <div class="summary-label">负责医生：${patient.doctor}</div>
            </div>
        </section>
        <section class="card" style="margin-bottom: 16px;">
            <h2 class="card-title">状态摘要</h2>
            <p class="header-text">${patient.summary}</p>
        </section>
    `;
}

function carePlanSection(patient) {
    const details = patient.planDetails;
    const plan = details?.plan;
    const version = details?.version;
    const templates = Array.isArray(details?.tasks) ? details.tasks : [];
    const title = normalizePlanText(plan?.title || "今日照护方案") || "今日照护方案";
    const status = planStatusLabel(plan?.status);
    const effective = version?.effectiveFrom
        ? `${version.effectiveFrom}${version.effectiveTo ? ` 至 ${version.effectiveTo}` : ""}`
        : "按医生发布的当前版本执行";
    const instructions = buildPlanText(patient, version, templates);
    const templateSummary = templates.length
        ? `已拆分 ${templates.length} 项日常任务`
        : "任务正在同步中";
    return `
        <section class="card plan-card" aria-labelledby="care-plan-title">
            <div class="plan-card-header">
                <div>
                    <div class="section-kicker">今日照护方案</div>
                    <h2 class="card-title" id="care-plan-title">${escapeHtml(title)}</h2>
                    <div class="plan-meta">${escapeHtml(effective)} · ${escapeHtml(version?.timezone || "Asia/Shanghai")} · ${escapeHtml(templateSummary)}</div>
                </div>
                <span class="badge ${plan?.status === "ACTIVE" ? "green" : "blue"}">${escapeHtml(status)}</span>
            </div>
            <details class="plan-disclosure">
                <summary>查看方案说明</summary>
                <div class="plan-textbox">${escapeHtml(instructions)}</div>
            </details>
        </section>
    `;
}

function patientProfile(patient) {
    return `
        <div class="list">
            <div class="list-item"><div class="item-title">姓名</div><div class="item-meta">${patient.name}</div></div>
            <div class="list-item"><div class="item-title">年龄 / 性别</div><div class="item-meta">${patient.age} 岁 / ${patient.gender}</div></div>
            <div class="list-item"><div class="item-title">医生 / 家属</div><div class="item-meta">${patient.doctor} / ${patient.family}</div></div>
            <div class="list-item"><div class="item-title">风险状态</div><div class="item-meta">${patient.riskLabel}</div></div>
        </div>
    `;
}

function patientPreview(patient) {
    const patientCode = patient.code || patient.id;
    const meta = [patientCode, patient.age && patient.age !== "-" ? `${patient.age} 岁` : "", patient.doctor]
        .filter(Boolean)
        .join(" · ");
    return `
        <div class="list" style="margin-bottom: 14px;">
            <div class="list-item">
                <div class="list-row">
                    <div>
                        <div class="item-title">${patient.name}</div>
                        <div class="item-meta">${meta}</div>
                    </div>
                    <span class="badge ${riskClass(patient.risk)}">${patient.riskLabel}</span>
                </div>
            </div>
            <div class="list-item">
                <div class="item-title">最近摘要</div>
                <div class="item-meta">${patient.summary}</div>
            </div>
        </div>
    `;
}

function taskList(patient, { interactive = false, showDetail = !interactive, showTime = false } = {}) {
    if (!patient.tasks.length) return `<div class="empty-state">当前没有任务。</div>`;
    return `
        <div class="task-list">
            ${patient.tasks.map((task, index) => {
                const statusCode = task.statusCode || (task.status === "已完成" ? "COMPLETED" : "PENDING");
                const completed = statusCode === "COMPLETED";
                const inactive = ["CANCELLED", "SKIPPED", "MISSED"].includes(statusCode);
                const canComplete = interactive && task.id && !completed && !inactive;
                const badgeClass = completed ? "green" : inactive ? "blue" : "amber";
                const dueAt = task.dueAt ? new Date(task.dueAt) : null;
                const dueLabel = dueAt && !Number.isNaN(dueAt.getTime()) ? dueAt.toLocaleString() : "";
                return `
                <article class="task-item ${taskStatusClass(statusCode)}">
                    <div class="task-leading">
                        ${interactive ? `
                            <button class="task-check" type="button" title="${completed ? "任务已完成" : "标记为已完成"}"
                                aria-label="${completed ? "任务已完成" : `标记${escapeHtml(task.title)}为已完成`}" data-task-complete="${task.id || ""}"
                                data-task-version="${task.version ?? 0}" data-task-status="${statusCode}" ${canComplete ? "" : "disabled"}>
                                <span class="task-dot">${completed ? "✓" : ""}</span>
                            </button>
                        ` : `<span class="task-dot task-dot-readonly">${completed ? "✓" : ""}</span>`}
                        <span class="task-index">${String(index + 1).padStart(2, "0")}</span>
                    </div>
                    <div class="task-content">
                        <div class="list-row">
                            <h3 class="task-heading">${escapeHtml(task.title)}</h3>
                            <span class="badge ${badgeClass}">${escapeHtml(task.status)}</span>
                        </div>
                        ${showTime && dueLabel ? `<div class="item-meta">执行时间：${escapeHtml(dueLabel)}</div>` : ""}
                        ${showDetail ? `<div class="item-meta">${escapeHtml(task.detail)}</div>` : ""}
                        ${interactive && statusCode === "OVERDUE" ? `<button class="task-missed-link" type="button" data-task-missed="${task.id || ""}" data-task-version="${task.version ?? 0}">确认未完成</button>` : ""}
                    </div>
                </article>
                `;
            }).join("")}
        </div>
    `;
}

function bindTaskActions() {
    document.querySelectorAll("[data-task-complete]").forEach((button) => {
        button.addEventListener("click", async () => {
            const taskId = button.dataset.taskComplete;
            if (!taskId || button.disabled) return;
            button.disabled = true;
            button.classList.add("is-loading");
            try {
                const taskApi = role().toUpperCase() === "PATIENT" ? apiPrefix("patient") : apiPrefix("family");
                const endpoint = button.dataset.taskStatus === "OVERDUE" ? "late-complete" : "complete";
                await careApi(`${taskApi}/tasks/${taskId}/${endpoint}`, {
                    method: "POST",
                    body: JSON.stringify({
                        version: Number(button.dataset.taskVersion || 0),
                        note: role().toUpperCase() === "PATIENT"
                            ? "患者端确认已完成任务"
                            : "家属端确认患者已完成任务"
                    })
                });
                if (role().toUpperCase() === "PATIENT") {
                    await renderPatientTasks();
                } else {
                    await renderCaregiverStatus();
                }
            } catch (error) {
                button.disabled = false;
                button.classList.remove("is-loading");
                window.alert(error.message);
            }
        });
    });

    document.querySelectorAll("[data-task-missed]").forEach((button) => {
        button.addEventListener("click", async () => {
            const taskId = button.dataset.taskMissed;
            if (!taskId || button.disabled) return;
            button.disabled = true;
            try {
                const taskApi = role().toUpperCase() === "PATIENT" ? apiPrefix("patient") : apiPrefix("family");
                await careApi(`${taskApi}/tasks/${taskId}/missed`, {
                    method: "POST",
                    body: JSON.stringify({
                        version: Number(button.dataset.taskVersion || 0),
                        note: "页面确认任务未完成"
                    })
                });
                if (role().toUpperCase() === "PATIENT") await renderPatientTasks();
                else await renderCaregiverStatus();
            } catch (error) {
                button.disabled = false;
                window.alert(error.message);
            }
        });
    });
}

function alertList(patient) {
    if (!patient.alerts.length) return `<div class="empty-state">当前没有异常告警。</div>`;
    return `
        <div class="list" style="margin-bottom: 14px;">
            ${patient.alerts.map((alert) => `
                <div class="list-item">
                    <div class="list-row">
                        <div>
                            <div class="item-title">${alert.title}</div>
                            <div class="item-meta">${alert.time} · ${alert.detail}</div>
                        </div>
                        <span class="badge ${alert.level === "urgent" ? "red" : "amber"}">${alert.level === "urgent" ? "紧急" : "关注"}</span>
                    </div>
                </div>
            `).join("")}
        </div>
    `;
}

function riskClass(risk) {
    if (risk === "URGENT") return "red";
    if (risk === "ATTENTION") return "amber";
    return "green";
}

window.addEventListener("hashchange", render);
window.addEventListener("DOMContentLoaded", render);
