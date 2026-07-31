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

const routes = {
    "/bind/caregiver": renderCaregiverBind,
    "/bind/doctor": renderDoctorBind,
    "/caregiver/status": renderCaregiverStatus,
    "/doctor/patients": renderDoctorSwitcher,
    "/doctor/detail": renderDoctorDetail,
    "/doctor/alerts-review": renderAlertsAndReview
};

const routeRoles = {
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
    const hash = location.hash.replace(/^#/, "") || "/bind/caregiver";
    const [path, query = ""] = hash.split("?");
    const params = new URLSearchParams(query);
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

function apiPrefix(kind) {
    if (kind === "family") return "/api/care/v1/family";
    if (kind === "clinical") return "/api/care/v1/clinical";
    const active = role().toUpperCase();
    return active === "DOCTOR" ? "/api/care/v1/clinical" : "/api/care/v1/family";
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
    patient.summary = `近 7 天打卡 ${status.checkInCount} 次，未处理告警 ${status.openAlertCount} 个，紧急告警 ${status.urgentAlertCount} 个，待确认记忆 ${status.pendingMemoryCount} 条。`;
    const tasks = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/tasks`).catch(() => []);
    const alerts = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/alerts`).catch(() => []);
    const checkins = await careApi(`${apiPrefix(kind)}/patients/${patient.id}/checkins`).catch(() => []);
    patient.tasks = (tasks || []).map((task) => ({
        id: task.id,
        title: task.title || `任务 #${task.id}`,
        status: taskStatusLabel(task.status),
        detail: taskDetailText(task)
    }));
    patient.alerts = (alerts || []).map((alert) => ({
        id: alert.id,
        title: alert.alertType || "患者告警",
        level: alert.severity === "URGENT" || alert.severity === "CRITICAL" ? "urgent" : "attention",
        time: alert.createdAt ? new Date(alert.createdAt).toLocaleString() : "刚刚",
        detail: alert.description || alert.status || "请查看告警详情"
    }));
    patient.checkins = (checkins || []).map((item) => ({
        time: item.submittedAt ? new Date(item.submittedAt).toLocaleString() : item.checkinDate,
        title: item.incidentType || "每日打卡",
        detail: item.originalText || `睡眠：${item.sleepStatus || "-"}，饮水：${item.hydrationStatus || "-"}`
    }));
    patient.taskDone = patient.tasks.filter((task) => task.status === "COMPLETED").length;
    patient.taskTotal = patient.tasks.length;
    return patient;
}

function backendUnavailableMessage(error) {
    return `<div class="message info">当前显示演示数据。真实接口暂不可用：${escapeHtml(error.message)}</div>`;
}

function taskStatusLabel(status) {
    const value = String(status || "").toUpperCase();
    if (value === "COMPLETED") return "已完成";
    if (value === "OVERDUE") return "已超时";
    if (value === "CANCELLED") return "已取消";
    if (value === "PENDING") return "待完成";
    return status || "待处理";
}

function taskDetailText(task) {
    const due = task?.dueAt ? new Date(task.dueAt).toLocaleString() : "";
    const instructions = task?.instructions || "";
    if (due && instructions) {
        return `提醒时间：${due} · ${instructions}`;
    }
    return instructions || due || "查看任务详情";
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
        patient = await hydratePatientStatus(selectedPatient(), "family");
    } catch (error) {
        notice = backendUnavailableMessage(error);
        patient = patients[0];
    }
    root().innerHTML = `
        ${header("家属查看", `${patient.name}的今日状态`, "家属端优先展示当天状态、任务完成情况和异常提醒。", `<button class="button primary">联系医生</button>`)}
        ${notice}
        ${summaryGrid(patient)}
        <section class="grid two">
            <div class="card">
                <h2 class="card-title">任务与打卡</h2>
                ${taskList(patient)}
            </div>
            <div class="card">
                <h2 class="card-title">异常与建议</h2>
                ${alertList(patient)}
                <div class="message info">可由后端把当前状态整理成邮件发给医生。</div>
            </div>
        </section>
    `;
    bindSummaryJumps();
    const contactButton = document.querySelector(".page-header .button.primary");
    if (contactButton) {
        contactButton.addEventListener("click", () => contactDoctor(patient));
    }
}

async function renderDoctorSwitcher() {
    let notice = "";
    try {
        await loadPatients("clinical");
        await hydratePatientStatus(selectedPatient(), "clinical");
    } catch (error) {
        notice = backendUnavailableMessage(error);
    }
    const patient = selectedPatient();
    root().innerHTML = `
        ${header("医生工作台", "患者切换", "医生可以在多个患者之间快速切换，先看状态，再进入详情或处理告警。", `<a class="button primary" href="#/doctor/detail">查看详情</a>`)}
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
        ${header("患者详情", `${patient.name} · ${patient.id}`, "集中查看患者基本信息、近期打卡、照护计划和风险提示。", `<a class="button" href="#/doctor/patients">返回患者列表</a><a class="button primary" href="#/doctor/alerts-review">处理告警</a>`)}
        ${notice}
        ${patientTabs()}
        <section class="detail-layout">
            <div class="grid">
                <div class="card">
                    <h2 class="card-title">基本信息</h2>
                    ${patientProfile(patient)}
                </div>
                <div class="card">
                    <h2 class="card-title">当前照护方案</h2>
                    <div class="plan-preview">${patient.plan}</div>
                </div>
            </div>
            <div class="card">
                <h2 class="card-title">最近记录</h2>
                <div class="timeline">
                    ${patient.checkins.map((item) => `
                        <div class="timeline-item">
                            <div class="item-title">${item.time} · ${item.title}</div>
                            <div class="item-meta">${item.detail}</div>
                        </div>
                    `).join("")}
                </div>
            </div>
        </section>
    `;
    bindPatientTabs(renderDoctorDetail);
}

async function renderAlertsAndReview() {
    const source = livePatients.length ? livePatients : patients;
    const urgentPatients = source.filter((patient) => patient.alerts.length > 0);
    let draftList = [];
    let draftDetail = null;
    let notice = "";
    try {
        await loadPatients("clinical");
        await hydratePatientStatus(selectedPatient(), "clinical");
        draftList = await careApi(`${apiPrefix("clinical")}/plan-drafts`);
        const routeDraftId = routeParams().get("draftId");
        const selectedId = routeDraftId || draftList.find((draft) => draft.status === "待审核")?.id || draftList[0]?.id || "";
        if (selectedId) {
            draftDetail = await careApi(`${apiPrefix("clinical")}/plan-drafts/${selectedId}`);
        }
    } catch (error) {
        notice = backendUnavailableMessage(error);
        draftList = [];
    }
    const activeDraftId = draftDetail?.id || draftList.find((draft) => draft.status === "待审核")?.id || draftList[0]?.id || "";
    root().innerHTML = `
        ${header("告警与审核", "告警中心和方案审核", "", `<button class="button">批量忽略低风险</button><button class="button primary">确认当前方案</button>`)}
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

function taskList(patient) {
    if (!patient.tasks.length) return `<div class="empty-state">当前没有任务。</div>`;
    return `
        <div class="list">
            ${patient.tasks.map((task) => `
                <div class="list-item">
                    <div class="list-row">
                        <div class="item-title">${task.title}</div>
                        <span class="badge ${task.status.includes("超时") || task.status.includes("未") || task.status.includes("待") ? "amber" : "green"}">${task.status}</span>
                    </div>
                    <div class="item-meta">${task.detail}</div>
                </div>
            `).join("")}
        </div>
    `;
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
