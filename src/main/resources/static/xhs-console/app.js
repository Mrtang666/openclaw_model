const API = "/api/xhs-console";

const state = {
  view: "overview",
  projects: [],
  projectKey: localStorage.getItem("xhs.console.project") || "",
  health: null,
};

const views = {
  overview: ["总览", "查看当前项目的舆情与风险概况"],
  projects: ["项目管理", "配置监控项目、关键词并启动采集"],
  jobs: ["采集任务", "查看采集进度、结果和失败原因"],
  opinions: ["舆情数据", "筛选分析结果并查看帖子原文与证据"],
  incidents: ["风险事件", "跟踪高风险事件并记录处置过程"],
  reports: ["舆情日报", "按项目和日期查看舆情摘要"],
  alerts: ["告警管理", "配置风险告警规则并查看告警事件"],
  system: ["系统状态", "检查数据库、采集 Sidecar 和运行任务"],
};

const content = document.querySelector("#content");
const notice = document.querySelector("#notice");
const projectSelect = document.querySelector("#global-project");
const refreshButton = document.querySelector("#refresh-button");
const serviceBadge = document.querySelector("#service-badge");

document.querySelector("#navigation").addEventListener("click", (event) => {
  const button = event.target.closest("[data-view]");
  if (!button) return;
  setView(button.dataset.view);
});

projectSelect.addEventListener("change", () => {
  state.projectKey = projectSelect.value;
  localStorage.setItem("xhs.console.project", state.projectKey);
  renderCurrent();
});
refreshButton.addEventListener("click", renderCurrent);
document.querySelector("#drawer-close").addEventListener("click", closeDrawer);
document.querySelector("#drawer-backdrop").addEventListener("click", closeDrawer);
document.querySelector("#action-form").addEventListener("submit", submitTransition);
document.querySelector("#project-edit-form").addEventListener("submit", submitProjectEdit);
document.querySelector("#delete-project-form").addEventListener("submit", submitProjectDelete);
document.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", () => {
  document.querySelector(`#${button.dataset.closeDialog}`).close();
}));

function setView(view) {
  state.view = view;
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === view));
  document.querySelector("#page-title").textContent = views[view][0];
  document.querySelector("#page-subtitle").textContent = views[view][1];
  renderCurrent();
}

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `请求失败（HTTP ${response.status}）`);
  }
  return response.status === 204 ? null : response.json();
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") search.set(key, value);
  });
  return search.toString() ? `?${search}` : "";
}

function loading() { content.innerHTML = `<div class="loading-state">正在加载数据...</div>`; }
function empty(message) { return `<div class="empty-state">${escapeHtml(message)}</div>`; }
function showNotice(message, success = false) {
  notice.textContent = message;
  notice.className = success ? "notice success" : "notice";
  notice.hidden = false;
  clearTimeout(showNotice.timer);
  showNotice.timer = setTimeout(() => { notice.hidden = true; }, 5000);
}

async function loadProjects() {
  state.projects = await api("/projects");
  if (state.projectKey && !state.projects.some((project) => project.projectKey === state.projectKey)) state.projectKey = "";
  projectSelect.innerHTML = `<option value="">全部项目</option>${state.projects.map((project) =>
    `<option value="${escapeAttr(project.projectKey)}">${escapeHtml(project.name)}</option>`).join("")}`;
  projectSelect.value = state.projectKey;
}

async function loadHealth() {
  try {
    state.health = await api("/health");
    const up = state.health.status === "UP";
    serviceBadge.className = `service-badge ${up ? "up" : "degraded"}`;
    serviceBadge.innerHTML = `<i></i><span>${up ? "服务正常" : "服务降级"}</span>`;
  } catch {
    serviceBadge.className = "service-badge degraded";
    serviceBadge.innerHTML = `<i></i><span>接口异常</span>`;
  }
}

async function renderCurrent() {
  loading();
  notice.hidden = true;
  try {
    await loadHealth();
    if (state.view !== "projects" && !state.projects.length) await loadProjects();
    await renderers[state.view]();
  } catch (error) {
    content.innerHTML = empty(error.message);
  }
}

const renderers = {
  overview: renderOverview,
  projects: renderProjects,
  jobs: renderJobs,
  opinions: renderOpinions,
  incidents: renderIncidents,
  reports: renderReports,
  alerts: renderAlerts,
  system: renderSystem,
};

async function renderOverview() {
  const [overview, jobs, incidents] = await Promise.all([
    api(`/overview${query({ projectKey: state.projectKey })}`),
    api(`/jobs${query({ projectKey: state.projectKey, limit: 6 })}`),
    api(`/incidents${query({ projectKey: state.projectKey, limit: 6 })}`),
  ]);
  const metrics = [
    ["采集帖子", overview.postCount, ""], ["已分析", overview.analyzedCount, ""],
    ["负面帖子", overview.negativeCount, "warning"], ["高风险帖子", overview.highRiskCount, "danger"],
    ["待处理事件", overview.activeIncidentCount, "danger"], ["失败任务", overview.failedJobCount, "warning"],
  ];
  content.innerHTML = `
    <div class="metrics">${metrics.map(([label, value, cls]) => `<div class="metric ${cls}"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>
    <div class="grid-two">
      <section class="panel"><div class="panel-header"><h2>最近采集任务</h2><button class="button text" data-go="jobs">查看全部</button></div>
        <div class="table-wrap">${jobs.length ? jobTable(jobs) : empty("暂无采集任务")}</div></section>
      <section class="panel"><div class="panel-header"><h2>高风险事件</h2><button class="button text" data-go="incidents">查看全部</button></div>
        <div class="panel-body">${incidents.length ? `<div class="summary-list">${incidents.map(incident => `<div class="summary-item"><div><strong>${escapeHtml(incident.title)}</strong><div class="muted">${escapeHtml(incident.riskCategory)} · ${escapeHtml(incident.status)}</div></div><span class="tag ${riskClass(incident.riskLevel)}">${incident.riskScore}</span></div>`).join("")}</div>` : empty("暂无风险事件")}</div></section>
    </div>`;
  content.querySelectorAll("[data-go]").forEach(button => button.addEventListener("click", () => setView(button.dataset.go)));
}

async function renderProjects() {
  await loadProjects();
  content.innerHTML = `
    <section class="panel"><div class="panel-header"><h2>新建监控项目</h2></div><div class="panel-body">
      <form id="project-form"><div class="form-grid">
        <label>项目标识<input name="projectKey" required maxlength="128" pattern="[A-Za-z0-9][A-Za-z0-9_-]*" placeholder="例如 brand_watch"></label>
        <label>项目名称<input name="name" required maxlength="255" placeholder="例如 品牌口碑监控"></label>
        <label class="full">监控关键词<input name="terms" required placeholder="多个关键词使用逗号分隔"></label>
      </div><div class="form-actions"><button class="button primary" type="submit">创建项目</button><span class="muted">项目标识创建后不可修改</span></div></form>
    </div></section>
    <section class="panel"><div class="panel-header"><h2>已有项目</h2><span class="muted">${state.projects.length} 个项目</span></div><div class="panel-body">
      ${state.projects.length ? `<div class="project-list">${state.projects.map(project => `
        <article class="project-row"><div><h3>${escapeHtml(project.name)} <span class="tag ${project.status === "ACTIVE" ? "normal" : "watch"}">${project.status === "ACTIVE" ? "运行中" : "已暂停"}</span></h3><p class="mono">${escapeHtml(project.projectKey)}</p></div>
        <div><div class="term-list">${project.terms.length ? project.terms.map(term => `<span class="tag">${escapeHtml(term)}</span>`).join("") : `<span class="muted">未配置关键词</span>`}</div><p>${project.postCount} 篇帖子 · ${project.activeIncidentCount} 个待处理事件</p></div>
        <div class="project-actions"><button class="button primary" data-collect="${escapeAttr(project.projectKey)}" ${project.status !== "ACTIVE" ? "disabled" : ""}>立即采集</button><button class="button secondary" data-opinions="${escapeAttr(project.projectKey)}">查看舆情</button><button class="button secondary" data-edit="${escapeAttr(project.projectKey)}">编辑</button><button class="button secondary" data-toggle="${escapeAttr(project.projectKey)}" data-status="${project.status}">${project.status === "ACTIVE" ? "暂停" : "启用"}</button><button class="button danger" data-delete="${escapeAttr(project.projectKey)}">删除</button></div></article>`).join("")}</div>` : empty("尚未创建项目")}
    </div></section>`;
  document.querySelector("#project-form").addEventListener("submit", createProject);
  content.querySelectorAll("[data-collect]").forEach(button => button.addEventListener("click", () => collectProject(button.dataset.collect, button)));
  content.querySelectorAll("[data-opinions]").forEach(button => button.addEventListener("click", () => {
    state.projectKey = button.dataset.opinions; projectSelect.value = state.projectKey; setView("opinions");
  }));
  content.querySelectorAll("[data-toggle]").forEach(button => button.addEventListener("click", () => toggleProject(button)));
  content.querySelectorAll("[data-edit]").forEach(button => button.addEventListener("click", () => openProjectEdit(button.dataset.edit)));
  content.querySelectorAll("[data-delete]").forEach(button => button.addEventListener("click", () => openProjectDelete(button.dataset.delete)));
}

function openProjectEdit(projectKey) {
  const project = state.projects.find(item => item.projectKey === projectKey);
  if (!project) return;
  document.querySelector("#edit-project-key").value = project.projectKey;
  document.querySelector("#edit-project-name").value = project.name;
  document.querySelector("#edit-project-terms").value = project.terms.join("，");
  document.querySelector("#edit-project-status").value = project.status;
  document.querySelector("#project-dialog").showModal();
}

async function submitProjectEdit(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const button = document.querySelector("#project-edit-submit");
  button.disabled = true;
  try {
    const projectKey = document.querySelector("#edit-project-key").value;
    await api(`/projects/${encodeURIComponent(projectKey)}`, { method: "PATCH", body: JSON.stringify({
      name: document.querySelector("#edit-project-name").value,
      status: document.querySelector("#edit-project-status").value,
      terms: splitTerms(document.querySelector("#edit-project-terms").value),
    }) });
    document.querySelector("#project-dialog").close();
    showNotice("项目配置已保存", true);
    await renderProjects();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

function openProjectDelete(projectKey) {
  document.querySelector("#delete-project-key").value = projectKey;
  document.querySelector("#delete-project-key-label").textContent = projectKey;
  document.querySelector("#delete-project-confirmation").value = "";
  document.querySelector("#delete-project-dialog").showModal();
}

async function submitProjectDelete(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const button = document.querySelector("#delete-project-submit");
  const projectKey = document.querySelector("#delete-project-key").value;
  const confirmation = document.querySelector("#delete-project-confirmation").value;
  if (confirmation !== projectKey) {
    showNotice("输入的项目标识不匹配");
    return;
  }
  button.disabled = true;
  try {
    await api(`/projects/${encodeURIComponent(projectKey)}`, {
      method: "DELETE", body: JSON.stringify({ confirmation }),
    });
    document.querySelector("#delete-project-dialog").close();
    if (state.projectKey === projectKey) {
      state.projectKey = "";
      localStorage.removeItem("xhs.console.project");
    }
    showNotice("项目及其关联数据已删除", true);
    await renderProjects();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function createProject(event) {
  event.preventDefault();
  const button = event.submitter;
  const data = new FormData(event.currentTarget);
  button.disabled = true;
  try {
    const project = await api("/projects", { method: "POST", body: JSON.stringify({
      projectKey: data.get("projectKey"), name: data.get("name"), terms: splitTerms(data.get("terms")),
    }) });
    state.projectKey = project.projectKey;
    localStorage.setItem("xhs.console.project", state.projectKey);
    showNotice("项目已创建", true);
    await renderProjects();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function toggleProject(button) {
  const next = button.dataset.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
  button.disabled = true;
  try {
    await api(`/projects/${encodeURIComponent(button.dataset.toggle)}`, { method: "PATCH", body: JSON.stringify({ status: next }) });
    showNotice(next === "ACTIVE" ? "项目已启用" : "项目已暂停", true);
    await renderProjects();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function collectProject(projectKey, button) {
  button.disabled = true;
  try {
    const result = await api(`/projects/${encodeURIComponent(projectKey)}/collections`, { method: "POST", body: JSON.stringify({ limit: 20 }) });
    showNotice(`采集任务已提交：${result.jobKey}`, true);
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function renderJobs() {
  const jobs = await api(`/jobs${query({ projectKey: state.projectKey, limit: 100 })}`);
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>任务历史</h2><span class="muted">运行中的任务会自动刷新</span></div><div class="table-wrap">${jobs.length ? jobTable(jobs) : empty("暂无采集任务")}</div></section>`;
  if (jobs.some(job => ["PENDING", "SUBMITTED", "RUNNING"].includes(job.status))) {
    clearTimeout(renderJobs.timer);
    renderJobs.timer = setTimeout(() => state.view === "jobs" && renderJobs(), 4000);
  }
}

function jobTable(jobs) {
  return `<table><thead><tr><th>状态</th><th>项目</th><th>采集关键词</th><th>结果数</th><th>开始时间</th><th>完成时间</th><th>错误</th></tr></thead><tbody>${jobs.map(job => `<tr><td><span class="tag ${jobClass(job.status)}">${jobStatus(job.status)}</span></td><td>${escapeHtml(job.projectName)}</td><td class="title-cell">${escapeHtml(job.query)}</td><td>${job.recordCount}</td><td>${formatDate(job.startedAt)}</td><td>${formatDate(job.finishedAt)}</td><td class="title-cell muted">${escapeHtml(job.errorMessage || job.errorCode || "-")}</td></tr>`).join("")}</tbody></table>`;
}

async function renderOpinions() {
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>筛选条件</h2></div><div class="panel-body"><form id="opinion-filter" class="toolbar">
    <label class="grow">标题或内容关键词<input name="keyword" placeholder="输入关键词"></label>
    <label>情感<select name="sentiment"><option value="">全部</option><option value="NEGATIVE">负面</option><option value="NEUTRAL">中性</option><option value="POSITIVE">正面</option></select></label>
    <label>最低风险分<input name="minimumRiskScore" type="number" min="0" max="100" value="0"></label>
    <button class="button primary" type="submit">查询</button></form></div></section><section id="opinion-results" class="panel"><div class="loading-state">正在加载数据...</div></section>`;
  const form = document.querySelector("#opinion-filter");
  form.addEventListener("submit", event => { event.preventDefault(); loadOpinions(new FormData(form)); });
  await loadOpinions(new FormData(form));
}

async function loadOpinions(data) {
  const results = document.querySelector("#opinion-results");
  try {
    const rows = await api(`/opinions${query({ projectKey: state.projectKey, keyword: data.get("keyword"), sentiment: data.get("sentiment"), minimumRiskScore: data.get("minimumRiskScore"), limit: 100 })}`);
    results.innerHTML = `<div class="panel-header"><h2>分析结果</h2><span class="muted">${rows.length} 条</span></div><div class="table-wrap">${rows.length ? `<table><thead><tr><th>风险</th><th>情感</th><th>帖子标题</th><th>作者</th><th>互动</th><th>发布时间</th><th>原帖</th></tr></thead><tbody>${rows.map(row => `<tr data-post="${row.postId}"><td><span class="tag ${riskClass(row.riskLevel)}">${row.riskScore} · ${riskLabel(row.riskLevel)}</span></td><td><span class="tag ${String(row.sentiment).toLowerCase()}">${sentimentLabel(row.sentiment)}</span></td><td class="title-cell"><button class="button text" data-detail="${row.postId}">${escapeHtml(row.title || "无标题")}</button></td><td>${escapeHtml(row.authorDisplayName)}</td><td>${row.likedCount} 赞 · ${row.commentCount} 评</td><td>${formatDate(row.publishedAt)}</td><td><a class="button text" href="${postOpenUrl(row.postId)}" target="_blank" rel="noopener noreferrer">打开原帖</a></td></tr>`).join("")}</tbody></table>` : empty("没有符合条件的舆情数据")}</div>`;
    results.querySelectorAll("[data-detail]").forEach(button => button.addEventListener("click", () => openPost(button.dataset.detail)));
  } catch (error) { results.innerHTML = empty(error.message); }
}

async function openPost(postId) {
  const drawer = document.querySelector("#detail-drawer");
  document.querySelector("#drawer-title").textContent = "正在加载...";
  document.querySelector("#drawer-content").innerHTML = `<div class="loading-state">正在加载帖子详情...</div>`;
  document.querySelector("#drawer-backdrop").hidden = false;
  drawer.classList.add("open"); drawer.setAttribute("aria-hidden", "false");
  try {
    const post = await api(`/posts/${postId}`);
    document.querySelector("#drawer-title").textContent = post.title || "无标题";
    document.querySelector("#drawer-content").innerHTML = `
      <div class="detail-meta"><div><span>作者</span><strong>${escapeHtml(post.authorDisplayName)}</strong></div><div><span>风险</span><strong>${post.riskScore ?? "待分析"}</strong></div><div><span>情感</span><strong>${sentimentLabel(post.sentiment)}</strong></div><div><span>点赞</span><strong>${post.likedCount}</strong></div><div><span>收藏</span><strong>${post.collectedCount}</strong></div><div><span>评论</span><strong>${post.commentCount}</strong></div></div>
      <h3>内容</h3><p>${escapeHtml(post.content || "暂无正文")}</p>
      <h3>分析摘要</h3><p>${escapeHtml(post.summary || "尚未完成分析")}</p>
      <h3>风险依据</h3><p>${escapeHtml(formatJson(post.evidence))}</p>
      <a class="button primary" href="${postOpenUrl(post.postId)}" target="_blank" rel="noopener noreferrer">打开小红书原帖</a>
      <h3>已采集评论</h3>${post.comments.length ? post.comments.map(comment => `<article class="comment"><header><span>${escapeHtml(comment.authorDisplayName)}</span><span>${comment.likedCount} 赞 · ${formatDate(comment.publishedAt)}</span></header><p>${escapeHtml(comment.content)}</p></article>`).join("") : `<p class="muted">暂无评论数据</p>`}`;
  } catch (error) { document.querySelector("#drawer-content").innerHTML = empty(error.message); }
}

function closeDrawer() {
  const drawer = document.querySelector("#detail-drawer");
  drawer.classList.remove("open"); drawer.setAttribute("aria-hidden", "true");
  document.querySelector("#drawer-backdrop").hidden = true;
}

async function renderIncidents() {
  const incidents = await api(`/incidents${query({ projectKey: state.projectKey, limit: 100 })}`);
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>风险事件</h2><span class="muted">状态变更必须填写处理备注</span></div><div class="table-wrap">${incidents.length ? `<table><thead><tr><th>风险</th><th>事件</th><th>类别</th><th>状态</th><th>关联帖子</th><th>最近发现</th><th>操作</th></tr></thead><tbody>${incidents.map(item => `<tr><td><span class="tag ${riskClass(item.riskLevel)}">${item.riskScore}</span></td><td class="title-cell">${escapeHtml(item.title)}</td><td>${escapeHtml(item.riskCategory)}</td><td><span class="tag ${item.status.toLowerCase()}">${incidentStatus(item.status)}</span></td><td>${item.postCount}</td><td>${formatDate(item.lastSeenAt)}</td><td>${nextStatuses(item.status).length ? `<button class="button secondary" data-transition="${item.incidentId}" data-current="${item.status}">变更状态</button>` : `<span class="muted">已结束</span>`}</td></tr>`).join("")}</tbody></table>` : empty("暂无风险事件")}</div></section>`;
  content.querySelectorAll("[data-transition]").forEach(button => button.addEventListener("click", () => openTransition(button.dataset.transition, button.dataset.current)));
}

function openTransition(id, current) {
  document.querySelector("#dialog-incident-id").value = id;
  document.querySelector("#dialog-note").value = "";
  document.querySelector("#dialog-status").innerHTML = nextStatuses(current).map(status => `<option value="${status}">${incidentStatus(status)}</option>`).join("");
  document.querySelector("#action-dialog").showModal();
}

async function submitTransition(event) {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const button = document.querySelector("#dialog-submit");
  button.disabled = true;
  try {
    await api(`/incidents/${document.querySelector("#dialog-incident-id").value}/transitions`, { method: "POST", body: JSON.stringify({ targetStatus: document.querySelector("#dialog-status").value, note: document.querySelector("#dialog-note").value }) });
    document.querySelector("#action-dialog").close(); showNotice("事件状态已更新", true); await renderIncidents();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function renderReports() {
  if (!state.projectKey) { content.innerHTML = empty("请先在顶部选择一个项目"); return; }
  const today = new Date().toISOString().slice(0, 10);
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>日报日期</h2></div><div class="panel-body"><form id="report-form" class="toolbar"><label>选择日期<input name="date" type="date" value="${today}"></label><button class="button primary" type="submit">生成日报</button><button class="button secondary" id="report-download" type="button">下载 Word</button></form></div></section><section id="report-result" class="panel"><div class="loading-state">正在生成日报...</div></section>`;
  const form = document.querySelector("#report-form"); form.addEventListener("submit", event => { event.preventDefault(); loadReport(new FormData(form).get("date")); });
  document.querySelector("#report-download").addEventListener("click", event => downloadReport(new FormData(form).get("date"), event.currentTarget));
  await loadReport(today);
}

async function loadReport(date) {
  const target = document.querySelector("#report-result");
  try {
    const report = await api(`/reports/daily${query({ projectKey: state.projectKey, date })}`);
    target.innerHTML = `<div class="panel-body"><div class="report-hero"><div><h2>${escapeHtml(report.projectName || report.projectKey)} 舆情日报</h2><p class="muted">${escapeHtml(report.projectKey)} · ${escapeHtml(report.reportDate)}</p></div><div><span class="muted">平均风险分</span><strong>${report.averageRiskScore}</strong></div></div>
      <div class="metrics" style="margin-top:16px"><div class="metric"><span>新增帖子</span><strong>${report.collectedPosts}</strong></div><div class="metric"><span>已分析</span><strong>${report.analyzedPosts}</strong></div><div class="metric warning"><span>负面帖子</span><strong>${report.negativePosts}</strong></div><div class="metric danger"><span>高风险</span><strong>${report.highRiskPosts}</strong></div><div class="metric"><span>新增事件</span><strong>${report.newIncidents}</strong></div><div class="metric"><span>已解决</span><strong>${report.resolvedIncidents}</strong></div></div>
      <h3>风险类别</h3>${report.categories.length ? `<div class="term-list">${report.categories.map(category => `<span class="tag">${escapeHtml(category.riskCategory)} · ${category.postCount}</span>`).join("")}</div>` : `<p class="muted">当日暂无风险分类数据</p>`}
      <h3>重点风险事件</h3>${report.topActiveIncidents.length ? `<div class="summary-list">${report.topActiveIncidents.map(item => `<div class="summary-item"><div><strong>${escapeHtml(item.title)}</strong><p class="muted">${escapeHtml(item.riskCategory)} · ${incidentStatus(item.status)} · ${item.postCount} 条帖子</p></div><span class="tag ${riskClass(item.riskLevel)}">${item.riskScore}</span></div>`).join("")}</div>` : `<p class="muted">当前没有未解决的风险事件</p>`}
      <h3>当日高风险笔记</h3>${report.topRiskPosts.length ? `<div class="table-wrap"><table><thead><tr><th>风险分</th><th>标题</th><th>类别</th><th>摘要</th><th>原帖</th></tr></thead><tbody>${report.topRiskPosts.map(post => `<tr><td>${post.riskScore}</td><td class="title-cell">${escapeHtml(post.title || "无标题")}</td><td>${escapeHtml(post.riskCategory)}</td><td class="title-cell muted">${escapeHtml(post.summary)}</td><td><a class="button text" href="${postOpenUrl(post.postId)}" target="_blank" rel="noopener noreferrer">打开原帖</a></td></tr>`).join("")}</tbody></table></div>` : `<p class="muted">当日暂无已分析笔记</p>`}</div>`;
  } catch (error) { target.innerHTML = empty(error.message); }
}

async function downloadReport(date, button) {
  button.disabled = true;
  try {
    const response = await fetch(`${API}/reports/daily.docx${query({ projectKey: state.projectKey, date })}`);
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || `下载失败（HTTP ${response.status}）`);
    }
    const blobUrl = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = blobUrl;
    link.download = `${state.projectKey}-小红书舆情日报-${date}.docx`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(blobUrl);
    showNotice("Word 日报已生成", true);
  } catch (error) {
    showNotice(error.message);
  } finally {
    button.disabled = false;
  }
}

async function renderAlerts() {
  const [rules, events] = await Promise.all([api(`/alert-rules${query({ projectKey: state.projectKey })}`), api(`/alert-events${query({ projectKey: state.projectKey, limit: 50 })}`)]);
  content.innerHTML = `${state.projectKey ? `<section class="panel"><div class="panel-header"><h2>新建告警规则</h2></div><div class="panel-body"><form id="alert-form" class="toolbar"><label class="grow">规则名称<input name="name" required placeholder="例如 高风险即时告警"></label><label>最低风险分<input name="minimumRiskScore" type="number" min="0" max="100" value="70"></label><label>冷却分钟<input name="cooldownMinutes" type="number" min="1" value="60"></label><button class="button primary" type="submit">创建规则</button></form></div></section>` : ""}
    <section class="panel"><div class="panel-header"><h2>告警规则</h2><span class="muted">微信接收人仍由微信端订阅管理</span></div><div class="table-wrap">${rules.length ? `<table><thead><tr><th>项目</th><th>名称</th><th>阈值</th><th>冷却时间</th><th>状态</th><th>操作</th></tr></thead><tbody>${rules.map(rule => `<tr><td>${escapeHtml(rule.projectKey)}</td><td>${escapeHtml(rule.name)}</td><td>${rule.minimumRiskScore}</td><td>${rule.cooldownMinutes} 分钟</td><td><span class="tag ${rule.enabled ? "normal" : "watch"}">${rule.enabled ? "启用" : "停用"}</span></td><td><button class="button secondary" data-rule-toggle="${rule.id}" data-rule-project="${escapeAttr(rule.projectKey)}" data-rule-name="${escapeAttr(rule.name)}" data-rule-score="${rule.minimumRiskScore}" data-rule-cooldown="${rule.cooldownMinutes}" data-rule-enabled="${rule.enabled}">${rule.enabled ? "停用" : "启用"}</button></td></tr>`).join("")}</tbody></table>` : empty("暂无告警规则")}</div></section>
    <section class="panel"><div class="panel-header"><h2>告警事件</h2></div><div class="table-wrap">${events.length ? `<table><thead><tr><th>状态</th><th>项目</th><th>规则</th><th>事件</th><th>风险分</th><th>创建时间</th><th>操作</th></tr></thead><tbody>${events.map(item => `<tr><td><span class="tag ${item.status.toLowerCase()}">${escapeHtml(item.status)}</span></td><td>${escapeHtml(item.projectKey)}</td><td>${escapeHtml(item.ruleName)}</td><td class="title-cell">${escapeHtml(item.title)}</td><td>${item.riskScore}</td><td>${formatDate(item.createdAt)}</td><td>${item.status === "ACKNOWLEDGED" ? `<span class="muted">已确认</span>` : `<button class="button secondary" data-alert-ack="${item.id}">确认</button>`}</td></tr>`).join("")}</tbody></table>` : empty("暂无告警事件")}</div></section>`;
  document.querySelector("#alert-form")?.addEventListener("submit", createAlertRule);
  content.querySelectorAll("[data-rule-toggle]").forEach(button => button.addEventListener("click", () => toggleAlertRule(button)));
  content.querySelectorAll("[data-alert-ack]").forEach(button => button.addEventListener("click", () => acknowledgeAlert(button)));
}

async function toggleAlertRule(button) {
  button.disabled = true;
  try {
    await api(`/alert-rules/${button.dataset.ruleToggle}`, { method: "PATCH", body: JSON.stringify({
      projectKey: button.dataset.ruleProject, name: button.dataset.ruleName,
      minimumRiskScore: Number(button.dataset.ruleScore), cooldownMinutes: Number(button.dataset.ruleCooldown),
      enabled: button.dataset.ruleEnabled !== "true",
    }) });
    showNotice("告警规则状态已更新", true); await renderAlerts();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function acknowledgeAlert(button) {
  button.disabled = true;
  try {
    await api(`/alert-events/${button.dataset.alertAck}/acknowledge`, { method: "POST", body: "{}" });
    showNotice("告警事件已确认", true); await renderAlerts();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function createAlertRule(event) {
  event.preventDefault(); const button = event.submitter; const data = new FormData(event.currentTarget); button.disabled = true;
  try {
    await api("/alert-rules", { method: "POST", body: JSON.stringify({ projectKey: state.projectKey, name: data.get("name"), minimumRiskScore: Number(data.get("minimumRiskScore")), cooldownMinutes: Number(data.get("cooldownMinutes")), enabled: true }) });
    showNotice("告警规则已创建", true); await renderAlerts();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function renderSystem() {
  const health = await api("/health");
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>服务检查</h2><span class="tag ${health.status === "UP" ? "normal" : "warning"}">${health.status}</span></div><div class="panel-body"><div class="detail-meta"><div><span>管理接口</span><strong>正常</strong></div><div><span>数据库</span><strong>${health.databaseUp ? "正常" : "异常"}</strong></div><div><span>采集配置</span><strong>${health.collectorEnabled ? "已启用" : "未启用"}</strong></div><div><span>采集 Sidecar</span><strong>${health.collectorUp ? "正常" : "异常"}</strong></div><div><span>运行中任务</span><strong>${health.runningJobs}</strong></div><div><span>检查时间</span><strong>${formatDate(health.checkedAt)}</strong></div></div><p class="muted">${escapeHtml(health.collectorMessage)}</p></div></section>`;
}

function splitTerms(value) { return String(value || "").split(/[,，\n]/).map(item => item.trim()).filter(Boolean); }
function nextStatuses(status) { return ({ OPEN: ["ACKNOWLEDGED", "INVESTIGATING"], ACKNOWLEDGED: ["INVESTIGATING"], INVESTIGATING: ["RESOLVED"], RESOLVED: [] })[status] || []; }
function incidentStatus(value) { return ({ OPEN: "待处理", ACKNOWLEDGED: "已确认", INVESTIGATING: "调查中", RESOLVED: "已解决" })[value] || value; }
function sentimentLabel(value) { return ({ NEGATIVE: "负面", NEUTRAL: "中性", POSITIVE: "正面" })[value] || value || "待分析"; }
function riskLabel(value) { return ({ CRITICAL: "严重", WARNING: "高风险", WATCH: "关注", NORMAL: "正常" })[value] || value; }
function riskClass(value) { return String(value || "normal").toLowerCase(); }
function jobClass(value) { return ({ PENDING: "submitted", SUBMITTED: "submitted", RUNNING: "running", SUCCEEDED: "succeeded", PARTIAL: "partial", FAILED: "failed" })[value] || "watch"; }
function jobStatus(value) { return ({ PENDING: "等待", SUBMITTED: "已提交", RUNNING: "运行中", SUCCEEDED: "成功", PARTIAL: "部分成功", FAILED: "失败" })[value] || value; }
function formatDate(value) { return value ? new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "-"; }
function formatJson(value) { if (!value) return "暂无"; if (Array.isArray(value)) return value.map(item => typeof item === "string" ? item : JSON.stringify(item)).join("\n") || "暂无"; return typeof value === "string" ? value : JSON.stringify(value, null, 2); }
function escapeHtml(value) { return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]); }
function escapeAttr(value) { return escapeHtml(value).replace(/`/g, "&#96;"); }
function postOpenUrl(postId) { return `${API}/posts/${encodeURIComponent(postId)}/open`; }

Promise.all([loadProjects(), loadHealth()]).then(renderCurrent).catch(error => { content.innerHTML = empty(error.message); });
