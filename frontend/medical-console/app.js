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

const routes = {
    "/bind/caregiver": renderCaregiverBind,
    "/bind/doctor": renderDoctorBind,
    "/caregiver/status": renderCaregiverStatus,
    "/doctor/patients": renderDoctorSwitcher,
    "/doctor/detail": renderDoctorDetail,
    "/doctor/alerts-review": renderAlertsAndReview
};

function root() {
    return document.querySelector("#app");
}

function currentRoute() {
    return location.hash.replace(/^#/, "") || "/bind/caregiver";
}

function selectedPatient() {
    return patients.find((patient) => patient.id === selectedPatientId) || patients[0];
}

function render() {
    const route = currentRoute();
    updateNav(route);
    (routes[route] || renderCaregiverBind)();
}

function updateNav(route) {
    document.querySelectorAll(".nav-list a").forEach((item) => {
        item.classList.toggle("active", item.getAttribute("href") === `#${route}`);
    });
}

function header(kicker, title, text, actions = "") {
    return `
        <header class="page-header">
            <div>
                <div class="eyebrow">${kicker}</div>
                <h1>${title}</h1>
                <p class="header-text">${text}</p>
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
                    <input id="patientCode" value="P001" autocomplete="off">
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
                ${patientPreview(patients[0])}
                <div class="message info" data-bind-result>提交后由后端确认 token、身份和关系白名单。</div>
            </div>
        </section>
    `;
    document.querySelector("[data-bind-form]").addEventListener("submit", (event) => {
        event.preventDefault();
        const code = document.querySelector("#patientCode").value.trim();
        const relation = document.querySelector("#relation").value;
        const result = document.querySelector("[data-bind-result]");
        result.className = "message success";
        result.textContent = `${config.role}绑定申请已提交：患者 ${code}，关系为 ${relation}。`;
    });
}

function renderCaregiverStatus() {
    const patient = patients[0];
    root().innerHTML = `
        ${header("家属查看", `${patient.name}的今日状态`, "家属端优先展示当天状态、任务完成情况和异常提醒。", `<button class="button primary">联系医生</button>`)}
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
}

function renderDoctorSwitcher() {
    const patient = selectedPatient();
    root().innerHTML = `
        ${header("医生工作台", "患者切换", "医生可以在多个患者之间快速切换，先看状态，再进入详情或处理告警。", `<a class="button primary" href="#/doctor/detail">查看详情</a>`)}
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
    bindPatientTabs(renderDoctorSwitcher);
}

function renderDoctorDetail() {
    const patient = selectedPatient();
    root().innerHTML = `
        ${header("患者详情", `${patient.name} · ${patient.id}`, "集中查看患者基本信息、近期打卡、照护计划和风险提示。", `<a class="button" href="#/doctor/patients">返回患者列表</a><a class="button primary" href="#/doctor/alerts-review">处理告警</a>`)}
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

function renderAlertsAndReview() {
    const urgentPatients = patients.filter((patient) => patient.alerts.length > 0);
    root().innerHTML = `
        ${header("告警与审核", "告警中心和方案审核", "医生在这里处理异常提醒，并确认机器人优化后的照护计划。", `<button class="button">批量忽略低风险</button><button class="button primary">确认当前方案</button>`)}
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
                <div class="list">
                    <div class="list-item">
                        <div class="list-row">
                            <div>
                                <div class="item-title">${planDraft.title}</div>
                                <div class="item-meta">状态：${planDraft.status}</div>
                            </div>
                            <span class="badge blue">待审核</span>
                        </div>
                    </div>
                    <div class="list-item">
                        <div class="item-title">医生原始要求</div>
                        <div class="item-meta">${planDraft.doctorInput}</div>
                    </div>
                    <div class="list-item">
                        <div class="item-title">机器人优化建议</div>
                        <div class="item-meta">${planDraft.botRefined}</div>
                    </div>
                </div>
                <div class="toolbar" style="margin-top: 14px;">
                    <button class="button">退回修改</button>
                    <button class="button primary">确认并发送给患者</button>
                </div>
            </div>
        </section>
    `;
}

function patientTabs() {
    return `
        <div class="patient-switcher">
            ${patients.map((patient) => `
                <button class="patient-tab ${patient.id === selectedPatientId ? "active" : ""}" data-patient-id="${patient.id}">
                    <div class="item-title">${patient.name}</div>
                    <div class="item-meta">${patient.id} · ${patient.riskLabel}</div>
                </button>
            `).join("")}
        </div>
    `;
}

function bindPatientTabs(callback) {
    document.querySelectorAll("[data-patient-id]").forEach((button) => {
        button.addEventListener("click", () => {
            selectedPatientId = button.dataset.patientId;
            callback();
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
    return `
        <div class="list" style="margin-bottom: 14px;">
            <div class="list-item">
                <div class="list-row">
                    <div>
                        <div class="item-title">${patient.name}</div>
                        <div class="item-meta">${patient.id} · ${patient.age} 岁 · ${patient.doctor}</div>
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
