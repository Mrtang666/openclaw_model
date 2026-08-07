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
  "scheduled-reports": ["定时报告", "定期采集分析并通过邮件或微信发送报告文件"],
  alerts: ["告警管理", "配置风险告警规则并查看告警事件"],
  authorization: ["账号授权", "管理小红书采集账号会话和重新授权"],
  system: ["系统状态", "检查数据库、采集 Sidecar 和运行任务"],
};

let authorizationPollTimer;
let opinionRequestController;

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
  document.body.dataset.view = view;
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
  clearTimeout(authorizationPollTimer);
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
  "scheduled-reports": renderScheduledReports,
  alerts: renderAlerts,
  authorization: renderAuthorization,
  system: renderSystem,
};

async function renderOverview() {
  const [overview, coverage, jobs, incidents] = await Promise.all([
    api(`/overview${query({ projectKey: state.projectKey })}`),
    api(`/coverage-metrics${query({ projectKey: state.projectKey, days: 7 })}`),
    api(`/jobs${query({ projectKey: state.projectKey, limit: 6 })}`),
    api(`/incidents${query({ projectKey: state.projectKey, limit: 6 })}`),
  ]);
  const metrics = [
    ["采集帖子", overview.postCount, ""], ["已分析", overview.analyzedCount, ""],
    ["负面帖子", overview.negativeCount, "warning"], ["高风险帖子", overview.highRiskCount, "danger"],
    ["待处理事件", overview.activeIncidentCount, "danger"], ["失败任务", overview.failedJobCount, "warning"],
  ];
  content.innerHTML = `
    <section class="overview-hero">
      <div><span class="overview-kicker">今日舆情态势</span><h2>从声量中识别<br><em>真正的风险信号</em></h2><p>当前范围已采集 ${overview.postCount} 篇帖子，其中 ${overview.negativeCount} 篇为负面内容，${overview.activeIncidentCount} 个风险事件仍待处理。</p></div>
      <aside><span>分析覆盖率</span><strong>${overview.postCount ? Math.round(overview.analyzedCount / overview.postCount * 1000) / 10 : 0}<small>%</small></strong><p>${overview.failedJobCount ? `${overview.failedJobCount} 个采集任务需要检查` : "采集与分析队列运行正常"}</p></aside>
    </section>
    <div class="metrics">${metrics.map(([label, value, cls]) => `<div class="metric ${cls}"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>
    <section class="panel"><div class="panel-header"><h2>采集与识别覆盖</h2><span class="muted">搜索为近 ${coverage.days} 天，内容完整度为当前存量</span></div><div class="panel-body"><div class="detail-meta">
      <div><span>搜索执行</span><strong>${coverage.searchExecutions}</strong><small>${coverage.searchStrategies} 种策略</small></div>
      <div><span>唯一命中帖子</span><strong>${coverage.uniquePostsFound}</strong><small>入库率 ${coverage.importRate}%</small></div>
      <div><span>异常搜索</span><strong>${coverage.partialSearches + coverage.failedSearches}</strong><small>部分 ${coverage.partialSearches} · 失败 ${coverage.failedSearches}</small></div>
      <div><span>评论采集覆盖</span><strong>${coverage.commentCoverageRate}%</strong><small>${coverage.collectedComments} / ${coverage.expectedComments}</small></div>
      <div><span>图片分析完成</span><strong>${coverage.imageAnalysisRate}%</strong><small>待处理 ${coverage.imagesPending} · 失败 ${coverage.imagesFailed}</small></div>
      <div><span>评论模型复核</span><strong>${coverage.commentsReviewed}</strong><small>待复核 ${coverage.commentsPending} · 负面 ${coverage.negativeComments}</small></div>
    </div></div></section>
    <div class="grid-two">
      <section class="panel"><div class="panel-header"><h2>最近采集任务</h2><button class="button text" data-go="jobs">查看全部</button></div>
        <div class="table-wrap">${jobs.length ? jobTable(jobs) : empty("暂无采集任务")}</div></section>
      <section class="panel"><div class="panel-header"><h2>高风险事件</h2><button class="button text" data-go="incidents">查看全部</button></div>
        <div class="panel-body">${incidents.length ? `<div class="summary-list overview-risk-list">${incidents.map(incident => `<div class="summary-item overview-risk-item"><div class="overview-risk-copy"><strong>${escapeHtml(incident.title)}</strong><div class="muted">${escapeHtml(incident.riskCategory)} · ${escapeHtml(incident.status)}</div></div><span class="tag ${riskClass(incident.riskLevel)}">${incident.riskScore}</span></div>`).join("")}</div>` : empty("暂无风险事件")}</div></section>
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
  const collector = state.projectKey ? `<section class="panel"><div class="panel-header"><h2>高级采集</h2></div><div class="panel-body"><form id="advanced-collection-form" class="toolbar">
    <label class="grow">搜索关键词<input name="query" required maxlength="200" placeholder="输入品牌、产品或场景词"></label>
    <label>数量<input name="limit" type="number" min="1" max="100" value="20"></label>
    <label>每帖评论上限<input name="commentLimit" type="number" min="0" max="1000" value="100"></label>
    <label>排序<select name="sortMode"><option value="GENERAL">综合</option><option value="LATEST">最新</option><option value="LIKES">点赞最多</option><option value="COMMENTS">评论最多</option><option value="COLLECTS">收藏最多</option></select></label>
    <label>时间<select name="timeRange"><option value="ANY">不限</option><option value="DAY">一天内</option><option value="WEEK">一周内</option><option value="HALF_YEAR">半年内</option></select></label>
    <label>类型<select name="noteType"><option value="ALL">不限</option><option value="IMAGE">图文</option><option value="VIDEO">视频</option></select></label>
    <button class="button primary" type="submit">开始采集</button>
    <button class="button secondary" type="button" id="coverage-collection">全面采集</button>
  </form></div></section>` : "";
  content.innerHTML = collector + `<section class="panel"><div class="panel-header"><h2>任务历史</h2><span class="muted">运行中的任务会自动刷新</span></div><div class="table-wrap">${jobs.length ? jobTable(jobs) : empty("暂无采集任务")}</div></section>`;
  document.querySelector("#advanced-collection-form")?.addEventListener("submit", async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      const data = new FormData(event.currentTarget);
      const result = await api(`/projects/${encodeURIComponent(state.projectKey)}/collections`, {
        method: "POST",
        body: JSON.stringify({
          query: data.get("query"),
          limit: Number(data.get("limit")),
          sortMode: data.get("sortMode"),
          timeRange: data.get("timeRange"),
          noteType: data.get("noteType"),
          commentLimit: Number(data.get("commentLimit")),
        }),
      });
      showNotice(`采集任务已提交：${result.jobKey}`, true);
      await renderJobs();
    } catch (error) {
      showNotice(error.message);
      button.disabled = false;
    }
  });
  document.querySelector("#coverage-collection")?.addEventListener("click", async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
      const limit = Number(document.querySelector("#advanced-collection-form [name=limit]").value);
      const result = await api(`/projects/${encodeURIComponent(state.projectKey)}/collection-plans`, {
        method: "POST",
        body: JSON.stringify({ limit }),
      });
      const suffix = result.errors?.length ? `，${result.errors.length} 个策略未提交` : "";
      showNotice(`已提交 ${result.submittedCount} 个覆盖采集任务${suffix}`, true);
      await renderJobs();
    } catch (error) {
      showNotice(error.message);
      button.disabled = false;
    }
  });
  if (jobs.some(job => ["PENDING", "SUBMITTED", "RUNNING"].includes(job.status))) {
    clearTimeout(renderJobs.timer);
    renderJobs.timer = setTimeout(() => state.view === "jobs" && renderJobs(), 4000);
  }
}

function jobTable(jobs) {
  return `<table><thead><tr><th>状态</th><th>完整度</th><th>项目</th><th>关键词与策略</th><th>采集统计</th><th>开始时间</th><th>完成时间</th><th>错误</th></tr></thead><tbody>${jobs.map(job => `<tr><td><span class="tag ${jobClass(job.status)}">${jobStatus(job.status)}</span></td><td><span class="tag ${completenessClass(job.completenessStatus)}">${completenessStatus(job.completenessStatus)}</span></td><td>${escapeHtml(job.projectName)}</td><td class="title-cell">${escapeHtml(job.query)}<br><span class="muted">${collectionStrategy(job)}</span></td><td class="muted">原始 ${job.rawCount ?? 0} / 入库 ${job.importedCount ?? job.recordCount ?? 0}<br>评论 ${job.commentCount ?? 0} / 跳过 ${job.skippedCount ?? 0}</td><td>${formatDate(job.startedAt)}</td><td>${formatDate(job.finishedAt)}</td><td class="title-cell muted">${escapeHtml(job.errorMessage || job.errorCode || "-")}</td></tr>`).join("")}</tbody></table>`;
}

function collectionStrategy(job) {
  const sort = ({ GENERAL: "综合", LATEST: "最新", LIKES: "点赞", COMMENTS: "评论", COLLECTS: "收藏" })[job.sortMode] || job.sortMode;
  const time = ({ ANY: "不限时间", DAY: "一天内", WEEK: "一周内", HALF_YEAR: "半年内" })[job.timeRange] || job.timeRange;
  const type = ({ ALL: "全部类型", IMAGE: "图文", VIDEO: "视频" })[job.noteType] || job.noteType;
  return `${sort} · ${time} · ${type} · 评论上限 ${job.requestedCommentLimit ?? 100}`;
}

function completenessStatus(status) {
  return ({ FULL: "完整", PARTIAL: "部分完整", FAILED: "失败", NOT_STARTED: "未开始" })[status] || status || "未开始";
}

function completenessClass(status) {
  return status === "FULL" ? "succeeded" : status === "FAILED" ? "failed" : status === "PARTIAL" ? "partial" : "watch";
}

async function renderOpinions() {
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>筛选条件</h2></div><div class="panel-body"><form id="opinion-filter" class="opinion-filter">
    <label class="opinion-keyword">标题或内容关键词<input name="keyword" placeholder="输入关键词"></label>
    <label>情感<select name="sentiment"><option value="">全部</option><option value="NEGATIVE">负面</option><option value="NEUTRAL">中性</option><option value="POSITIVE">正面</option></select></label>
    <label>最低风险分<input name="minimumRiskScore" type="number" min="0" max="100" value="0"></label>
    <button class="button primary opinion-submit" type="submit">查询</button>
    <fieldset class="opinion-flags"><legend>反馈类型</legend>
      <label><input name="commentNegativeOnly" type="checkbox"><span>评论存在负面反馈</span></label>
      <label><input name="consultationNegativeOnly" type="checkbox"><span>咨询帖含负面评论</span></label>
      <label><input name="imageNegativeOnly" type="checkbox"><span>图片存在负面反馈</span></label>
    </fieldset>
    <div class="opinion-date-controls">
      <label>发布时间起始<input name="publishedFrom" type="datetime-local"></label>
      <label>发布时间结束<input name="publishedTo" type="datetime-local"></label>
      <label>排序字段<select name="sortBy"><option value="publishedAt">发布时间</option><option value="riskScore">风险分</option><option value="analyzedAt">分析时间</option><option value="likedCount">点赞数</option><option value="commentCount">评论数</option></select></label>
      <label>排序方向<select name="sortDirection"><option value="DESC">降序</option><option value="ASC">升序</option></select></label>
      <div class="opinion-date-actions"><button class="button secondary" type="button" data-opinion-range="today">今日</button><button class="button secondary" type="button" data-opinion-range="7d">近7天</button><button class="button secondary" type="button" data-opinion-reset="true">重置日期</button></div>
    </div>
  </form></div></section><section id="opinion-results" class="panel"><div class="loading-state">正在加载数据...</div></section>`;
  const form = document.querySelector("#opinion-filter");
  const dateControls = form.querySelector(".opinion-date-controls");
  dateControls.querySelectorAll("[data-opinion-range]").forEach(button => button.addEventListener("click", () => setOpinionRange(form, button.dataset.opinionRange)));
  dateControls.querySelector("[data-opinion-reset]").addEventListener("click", () => {
    form.elements.publishedFrom.value = "";
    form.elements.publishedTo.value = "";
    form.elements.sortBy.value = "publishedAt";
    form.elements.sortDirection.value = "DESC";
    loadOpinions(new FormData(form));
  });
  form.addEventListener("submit", event => { event.preventDefault(); loadOpinions(new FormData(form)); });
  await loadOpinions(new FormData(form));
}

function setOpinionRange(form, range) {
  const end = new Date();
  const start = new Date(end);
  if (range === "today") start.setHours(0, 0, 0, 0);
  else start.setDate(start.getDate() - 6);
  form.elements.publishedFrom.value = toDateTimeLocal(start);
  form.elements.publishedTo.value = toDateTimeLocal(end);
  loadOpinions(new FormData(form));
}

function toDateTimeLocal(value) {
  const pad = number => String(number).padStart(2, "0");
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}

function isoDateTime(value) {
  return value ? new Date(value).toISOString() : "";
}

async function loadOpinions(data, page = 1) {
  const results = document.querySelector("#opinion-results");
  opinionRequestController?.abort();
  opinionRequestController = new AbortController();
  try {
    const result = await api(`/opinions${query({ projectKey: state.projectKey, keyword: data.get("keyword"), sentiment: data.get("sentiment"), commentNegativeOnly: data.get("commentNegativeOnly") === "on", consultationNegativeOnly: data.get("consultationNegativeOnly") === "on", imageNegativeOnly: data.get("imageNegativeOnly") === "on", minimumRiskScore: data.get("minimumRiskScore"), publishedFrom: isoDateTime(data.get("publishedFrom")), publishedTo: isoDateTime(data.get("publishedTo")), sortBy: data.get("sortBy") || "publishedAt", sortDirection: data.get("sortDirection") || "DESC", page, pageSize: 20 })}`, { signal: opinionRequestController.signal });
    const rows = result.items || [];
    results.innerHTML = `<div class="panel-header"><h2>分析结果</h2><span class="muted">共 ${result.total} 条 · 每页 20 条</span></div><div class="table-wrap">${rows.length ? `<table><thead><tr><th>风险</th><th>情感</th><th>帖子标题</th><th>作者</th><th>互动</th><th>发布时间</th><th>原帖</th></tr></thead><tbody>${rows.map(row => `<tr data-post="${row.postId}"><td><span class="tag ${riskClass(row.riskLevel)}">${row.riskScore} · ${riskLabel(row.riskLevel)}</span></td><td><span class="tag ${String(row.sentiment).toLowerCase()}">${sentimentLabel(row.sentiment)}</span></td><td class="title-cell"><button class="button text" data-detail="${row.postId}">${escapeHtml(row.title || "无标题")}</button></td><td>${escapeHtml(row.authorDisplayName)}</td><td>${row.likedCount} 赞 · ${row.commentCount} 评</td><td>${formatDate(row.publishedAt)}</td><td><a class="button text" href="${postOpenUrl(row.postId)}" target="_blank" rel="noopener noreferrer">打开原帖</a></td></tr>`).join("")}</tbody></table>` : empty("没有符合条件的舆情数据")}</div>`;
    results.insertAdjacentHTML("beforeend", `<div class="pagination"><span class="muted">共 ${result.total} 条 · 第 ${result.page}/${result.totalPages} 页</span><div class="inline-actions"><button class="button secondary" data-opinion-page="${result.page - 1}" ${result.page <= 1 ? "disabled" : ""}>上一页</button><button class="button secondary" data-opinion-page="${result.page + 1}" ${result.page >= result.totalPages ? "disabled" : ""}>下一页</button></div></div>`);
    results.querySelectorAll("[data-opinion-page]").forEach(button => button.addEventListener("click", () => loadOpinions(data, Number(button.dataset.opinionPage))));
    rows.forEach(row => {
      const titleCell = results.querySelector(`[data-post="${row.postId}"] .title-cell`);
      if (!titleCell) return;
      if (row.negativeCommentCount) {
        const badge = document.createElement("span");
        badge.className = `tag ${row.consultation ? "warning" : "negative"}`;
        badge.textContent = row.consultation
          ? `\u54a8\u8be2\u5e16\u8d1f\u8bc4 ${row.negativeCommentCount}`
          : `\u8d1f\u8bc4 ${row.negativeCommentCount}`;
        titleCell.prepend(badge);
      }
      if (row.negativeImageCount) {
        const badge = document.createElement("span");
        badge.className = "tag negative";
        badge.textContent = `\u8d1f\u9762\u56fe\u7247 ${row.negativeImageCount} \u00b7 ${row.highestImageRiskScore}\u5206`;
        titleCell.prepend(badge);
      }
    });
    results.querySelectorAll("[data-detail]").forEach(button => button.addEventListener("click", () => openPost(button.dataset.detail)));
  } catch (error) {
    if (error.name !== "AbortError") results.innerHTML = empty(error.message);
  }
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
      <section class="feedback-panel">
        <h3>分析反馈</h3>
        <p class="muted">反馈仅用于后续评估和优化，不会直接修改本次分析结果。</p>
        <div class="inline-actions">
          <button class="button secondary" type="button" data-analysis-feedback="CORRECT">分析准确</button>
          <button class="button secondary" type="button" data-analysis-feedback="SENTIMENT_WRONG">情感判断有误</button>
          <button class="button secondary" type="button" data-analysis-feedback="RISK_TOO_HIGH">风险评分过高</button>
          <button class="button secondary" type="button" data-analysis-feedback="RISK_TOO_LOW">风险评分过低</button>
        </div>
      </section>
      <h3>已采集评论</h3>${post.comments.length ? post.comments.map(comment => `<article class="comment"><header><span>${escapeHtml(comment.authorDisplayName)}</span><span>${comment.likedCount} 赞 · ${formatDate(comment.publishedAt)}</span></header><p>${escapeHtml(comment.content)}</p></article>`).join("") : `<p class="muted">暂无评论数据</p>`}`;
    document.querySelectorAll("[data-analysis-feedback]").forEach(button => button.addEventListener("click", () => submitPostFeedback(post.postId, button.dataset.analysisFeedback, button)));
    if (post.images?.length) {
      const gallery = document.createElement("section");
      gallery.className = "post-image-section";
      const heading = document.createElement("h3");
      heading.textContent = "\u5e16\u5b50\u56fe\u7247";
      const grid = document.createElement("div");
      grid.className = "post-image-grid";
      post.images.forEach((item, index) => {
        const link = document.createElement("a");
        link.href = item.imageUrl;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        link.title = item.summary || (item.analysisStatus === "PENDING" ? "\u5f85\u5206\u6790" : "");
        if (item.sentiment === "NEGATIVE") link.classList.add("negative-image");
        const image = document.createElement("img");
        image.src = item.imageUrl;
        image.alt = `\u5e16\u5b50\u56fe\u7247 ${index + 1}`;
        image.loading = "lazy";
        image.referrerPolicy = "no-referrer";
        link.appendChild(image);
        const status = document.createElement("span");
        status.className = "image-risk-label";
        status.textContent = item.analysisStatus === "SUCCEEDED"
          ? (item.sentiment === "NEGATIVE" ? `\u8d1f\u9762 ${item.riskScore}\u5206` : "\u672a\u89c1\u8d1f\u9762")
          : ({ PENDING: "\u5f85\u5206\u6790", FAILED: "\u5206\u6790\u5931\u8d25" })[item.analysisStatus] || "\u5f85\u5206\u6790";
        link.appendChild(status);
        grid.appendChild(link);
      });
      gallery.append(heading, grid);
      document.querySelector("#drawer-content .detail-meta")?.after(gallery);
    }
    document.querySelectorAll("#drawer-content .comment").forEach((article, index) => {
      const comment = post.comments[index];
      if (!comment?.negative) return;
      const badge = document.createElement("span");
      badge.className = "tag negative";
      badge.textContent = comment.analysisMethod === "LLM"
        ? `\u6a21\u578b\u590d\u6838\u8d1f\u9762 \u00b7 ${comment.riskScore}\u5206 \u00b7 ${Math.round(comment.confidence * 100)}%`
        : `\u89c4\u5219\u521d\u7b5b\u8d1f\u9762 \u00b7 ${comment.riskScore}\u5206`;
      article.querySelector("header span")?.append(" ", badge);
      if (comment.analysisSummary) {
        const summary = document.createElement("p");
        summary.className = "muted comment-analysis-summary";
        summary.textContent = comment.analysisSummary;
        article.appendChild(summary);
      }
    });
  } catch (error) { document.querySelector("#drawer-content").innerHTML = empty(error.message); }
}

async function submitPostFeedback(postId, feedbackType, button) {
  let note = "";
  if (feedbackType !== "CORRECT") {
    note = window.prompt("补充说明（可选）", "");
    if (note === null) return;
  }
  button.disabled = true;
  try {
    await api(`/posts/${postId}/feedback`, {
      method: "POST",
      body: JSON.stringify({ feedbackType, note }),
    });
    showNotice("分析反馈已记录", true);
  } catch (error) {
    showNotice(error.message);
  } finally {
    button.disabled = false;
  }
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
      <div class="metrics" style="margin-top:16px"><div class="metric"><span>新增帖子</span><strong>${report.collectedPosts}</strong></div><div class="metric"><span>已分析</span><strong>${report.analyzedPosts}</strong></div><div class="metric warning"><span>负面帖子</span><strong>${report.negativePosts}</strong></div><div class="metric warning"><span>含负面评论</span><strong>${report.negativeCommentPosts || 0}</strong></div><div class="metric warning"><span>含负面图片</span><strong>${report.negativeImagePosts || 0}</strong></div><div class="metric danger"><span>高风险</span><strong>${report.highRiskPosts}</strong></div><div class="metric"><span>新增事件</span><strong>${report.newIncidents}</strong></div><div class="metric"><span>已解决</span><strong>${report.resolvedIncidents}</strong></div></div>
      <h3>风险类别</h3>${report.categories.length ? `<div class="term-list">${report.categories.map(category => `<span class="tag">${escapeHtml(category.riskCategory)} · ${category.postCount}</span>`).join("")}</div>` : `<p class="muted">当日暂无风险分类数据</p>`}
      <h3>重点风险事件</h3>${report.topActiveIncidents.length ? `<div class="summary-list">${report.topActiveIncidents.map(item => `<div class="summary-item"><div><strong>${escapeHtml(item.title)}</strong><p class="muted">${escapeHtml(item.riskCategory)} · ${incidentStatus(item.status)} · ${item.postCount} 条帖子</p></div><span class="tag ${riskClass(item.riskLevel)}">${item.riskScore}</span></div>`).join("")}</div>` : `<p class="muted">当前没有未解决的风险事件</p>`}
      <h3>当日高风险笔记</h3>${report.topRiskPosts.length ? `<div class="table-wrap"><table><thead><tr><th>风险分</th><th>标题</th><th>类别</th><th>风险来源</th><th>摘要</th><th>原帖</th></tr></thead><tbody>${report.topRiskPosts.map(post => `<tr><td>${post.riskScore}</td><td class="title-cell">${escapeHtml(post.title || "无标题")}</td><td>${escapeHtml(post.riskCategory)}</td><td><strong>${escapeHtml(post.riskSource || "正文")}</strong>${post.negativeCommentCount ? `<div class="muted">负面评论 ${post.negativeCommentCount} 条 · 最高 ${post.highestCommentRiskScore} 分</div>` : ""}${post.negativeImageCount ? `<div class="muted">负面图片 ${post.negativeImageCount} 张 · 最高 ${post.highestImageRiskScore} 分</div>` : ""}</td><td class="title-cell muted">${escapeHtml(post.summary)}</td><td><a class="button text" href="${postOpenUrl(post.postId)}" target="_blank" rel="noopener noreferrer">打开原帖</a></td></tr>`).join("")}</tbody></table></div>` : `<p class="muted">当日暂无已分析笔记</p>`}</div>`;
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

async function renderScheduledReports() {
  if (!state.projectKey) {
    content.innerHTML = empty("请先在顶部选择一个项目");
    return;
  }
  const [schedules, runs, negativeDeliveries] = await Promise.all([
    api(`/report-schedules${query({ projectKey: state.projectKey })}`),
    api(`/report-runs${query({ projectKey: state.projectKey, limit: 30 })}`),
    api(`/negative-email-deliveries${query({ projectKey: state.projectKey, limit: 50 })}`),
  ]);
  content.innerHTML = `
    <section class="panel">
      <div class="panel-header"><h2>新建定时报告计划</h2><span class="muted">定时发送邮箱必须位于服务端白名单</span></div>
      <div class="panel-body">
        <form id="scheduled-report-form" class="form-grid">
          <label>计划名称<input name="name" required maxlength="255" placeholder="例如 每日品牌舆情报告"></label>
          <label>执行频率<select name="frequency"><option value="DAILY">每日</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option></select></label>
          <label>执行时间<input name="runTime" type="time" value="09:00" required></label>
          <label>时区<input name="timezone" value="Asia/Shanghai" required></label>
          <label>每周执行日<select name="dayOfWeek"><option value="1">周一</option><option value="2">周二</option><option value="3">周三</option><option value="4">周四</option><option value="5">周五</option><option value="6">周六</option><option value="7">周日</option></select></label>
          <label>每月执行日<input name="dayOfMonth" type="number" min="1" max="31" value="1"></label>
          <label>每个关键词采集数<input name="collectionLimit" type="number" min="1" max="100" value="20"></label>
          <label>报告重点笔记数<input name="topPostLimit" type="number" min="1" max="100" value="10"></label>
          <label class="full">邮件接收人<input name="emails" placeholder="多个邮箱使用英文逗号分隔"></label>
          <label>微信连接 ID<input name="wechatConnectionId" placeholder="可选，来自已登录微信连接"></label>
          <label>微信接收人 ID<input name="wechatRecipientId" placeholder="可选，需与连接 ID 同时填写"></label>
          <div class="full option-row">
            <label><input name="collectBeforeReport" type="checkbox" checked> 生成前先按项目关键词采集</label>
            <label><input name="docx" type="checkbox" checked> Word</label>
            <label><input name="xlsx" type="checkbox"> XLSX</label>
          </div>
          <div class="full form-actions"><button class="button primary" type="submit">创建计划</button></div>
        </form>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>报告计划</h2><span class="muted">${schedules.length} 个</span></div>
      <div class="table-wrap">${schedules.length ? scheduledReportTable(schedules) : empty("暂无定时报告计划")}</div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>运行历史</h2><span class="muted">运行中的任务会自动刷新</span></div>
      <div class="table-wrap">${runs.length ? scheduledRunTable(runs) : empty("暂无报告运行记录")}</div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>即时负面邮件</h2><span class="muted">最近 ${negativeDeliveries.length} 条发送记录</span></div>
      <div class="table-wrap">${negativeDeliveries.length ? negativeEmailTable(negativeDeliveries) : empty("暂无即时负面邮件记录")}</div>
    </section>`;
  const scheduledForm = document.querySelector("#scheduled-report-form");
  const negativeSettings = document.createElement("div");
  negativeSettings.className = "full option-row negative-email-settings";
  negativeSettings.innerHTML = `<label><input name="negativeEmailEnabled" type="checkbox"> 发现负面帖子后立即发送邮件</label><label>最低风险分<input name="negativeEmailMinimumRiskScore" type="number" min="0" max="100" value="60"></label><label><input name="negativeEmailHighRiskOnly" type="checkbox"> 仅高风险帖子</label><label>重复发送冷却（分钟）<input name="negativeEmailCooldownMinutes" type="number" min="1" max="1440" value="30"></label><span class="muted full">正文、评论或图片任一路确认负面且达到阈值后触发；使用上方邮箱接收人，邮件附带风险来源和 Word 帖子报告。</span>`;
  scheduledForm.querySelector(".form-actions").before(negativeSettings);
  scheduledForm.addEventListener("submit", createScheduledReport);
  content.querySelectorAll("[data-report-run]").forEach(button => button.addEventListener("click", () => runScheduledReport(button)));
  content.querySelectorAll("[data-report-toggle]").forEach(button => button.addEventListener("click", () => toggleScheduledReport(button, schedules)));
  content.querySelectorAll("[data-report-delete]").forEach(button => button.addEventListener("click", () => deleteScheduledReport(button)));
  content.querySelectorAll("[data-delivery-retry]").forEach(button => button.addEventListener("click", () => retryReportDelivery(button)));
  content.querySelectorAll("[data-negative-email-retry]").forEach(button => button.addEventListener("click", () => retryNegativeEmail(button)));
  if (runs.some(run => ["QUEUED", "COLLECTING", "ANALYZING", "GENERATING", "DELIVERING"].includes(run.status))
      || negativeDeliveries.some(item => ["PENDING", "PROCESSING"].includes(item.status))) {
    setTimeout(() => { if (state.view === "scheduled-reports") renderCurrent(); }, 5000);
  }
}

function scheduledReportTable(schedules) {
  return `<table><thead><tr><th>状态</th><th>计划</th><th>周期</th><th>格式</th><th>接收渠道</th><th>下次执行</th><th>操作</th></tr></thead><tbody>${schedules.map(item => {
    const channels = [item.emailRecipients.length ? `邮件 ${item.emailRecipients.length}` : "", item.wechatRecipientId ? "微信" : ""].filter(Boolean).join(" · ") || "仅生成";
    return `<tr><td><span class="tag ${item.enabled ? "normal" : "watch"}">${item.enabled ? "启用" : "暂停"}</span></td><td class="title-cell"><strong>${escapeHtml(item.name)}</strong><div class="muted">${item.collectBeforeReport ? "生成前采集" : "仅汇总已有数据"}</div></td><td>${scheduleFrequency(item)} · ${escapeHtml(item.runTime)}</td><td>${item.formats.map(value => `<span class="tag">${escapeHtml(value)}</span>`).join("")}</td><td>${escapeHtml(channels)}</td><td>${formatDate(item.nextRunAt)}</td><td><div class="inline-actions"><button class="button secondary" data-report-run="${item.id}">立即执行</button><button class="button secondary" data-report-toggle="${item.id}">${item.enabled ? "暂停" : "启用"}</button><button class="button danger" data-report-delete="${item.id}">删除</button></div></td></tr>`;
  }).join("")}</tbody></table>`;
}

function scheduledRunTable(runs) {
  return `<table><thead><tr><th>状态</th><th>计划</th><th>统计周期</th><th>文件</th><th>投递</th><th>开始</th><th>说明</th></tr></thead><tbody>${runs.map(run => `<tr><td><span class="tag ${reportRunClass(run.status)}">${reportRunStatus(run.status)}</span></td><td>${escapeHtml(run.scheduleName)}</td><td>${formatDate(run.periodStart)}<br><span class="muted">至 ${formatDate(run.periodEnd)}</span></td><td>${run.artifacts.length ? `<div class="artifact-list">${run.artifacts.map(file => `<a href="${API}/report-artifacts/${file.id}/download" title="下载 ${escapeAttr(file.fileName)}"><span class="tag">${escapeHtml(file.format)}</span><span>${escapeHtml(file.fileName)}</span></a>`).join("")}</div>` : "-"}</td><td>${deliverySummary(run)}</td><td>${formatDate(run.startedAt || run.createdAt)}</td><td class="title-cell muted">${escapeHtml(run.errorMessage || run.partialReason || "-")}</td></tr>`).join("")}</tbody></table>`;
}

function deliverySummary(run) {
  if (!run.deliveries?.length) return run.status === "SUCCEEDED" ? "仅生成" : "-";
  return `<div class="delivery-list">${run.deliveries.map(item => `<div><span class="tag ${item.status === "SENT" ? "succeeded" : item.status === "FAILED" ? "failed" : "running"}">${item.channel === "EMAIL" ? "邮件" : "微信"} · ${escapeHtml(item.status)}</span><span class="muted">${escapeHtml(item.target)}</span>${item.status === "FAILED" ? `<button class="button text" data-delivery-retry="${item.id}">重试</button>` : ""}${item.lastError ? `<div class="muted">${escapeHtml(item.lastError)}</div>` : ""}</div>`).join("")}</div>`;
}

async function retryReportDelivery(button) {
  button.disabled = true;
  try {
    await api(`/report-deliveries/${button.dataset.deliveryRetry}/retry`, { method: "POST", body: "{}" });
    showNotice("失败投递已重新排队", true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function createScheduledReport(event) {
  event.preventDefault();
  const button = event.submitter;
  const data = new FormData(event.currentTarget);
  button.disabled = true;
  try {
    const formats = [data.get("docx") ? "DOCX" : "", data.get("xlsx") ? "XLSX" : ""].filter(Boolean);
    if (!formats.length) throw new Error("至少选择一种报告格式");
    await api("/report-schedules", { method: "POST", body: JSON.stringify({
      projectKey: state.projectKey, name: data.get("name"), frequency: data.get("frequency"),
      runTime: data.get("runTime"), dayOfWeek: Number(data.get("dayOfWeek")), dayOfMonth: Number(data.get("dayOfMonth")),
      timezone: data.get("timezone"), formats, collectBeforeReport: Boolean(data.get("collectBeforeReport")),
      collectionLimit: Number(data.get("collectionLimit")), topPostLimit: Number(data.get("topPostLimit")),
      emailRecipients: splitEmails(data.get("emails")), wechatConnectionId: data.get("wechatConnectionId"),
      wechatRecipientId: data.get("wechatRecipientId"), enabled: true,
      negativeEmailEnabled: Boolean(data.get("negativeEmailEnabled")),
      negativeEmailMinimumRiskScore: Number(data.get("negativeEmailMinimumRiskScore")),
      negativeEmailHighRiskOnly: Boolean(data.get("negativeEmailHighRiskOnly")),
      negativeEmailCooldownMinutes: Number(data.get("negativeEmailCooldownMinutes")),
    }) });
    showNotice("定时报告计划已创建", true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function runScheduledReport(button) {
  button.disabled = true;
  try {
    const result = await api(`/report-schedules/${button.dataset.reportRun}/run`, { method: "POST", body: "{}" });
    showNotice(`报告任务已提交：${result.runId}`, true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function toggleScheduledReport(button, schedules) {
  const item = schedules.find(value => String(value.id) === button.dataset.reportToggle);
  if (!item) return;
  button.disabled = true;
  try {
    await api(`/report-schedules/${item.id}`, { method: "PATCH", body: JSON.stringify(schedulePayload(item, !item.enabled)) });
    showNotice(item.enabled ? "报告计划已暂停" : "报告计划已启用", true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function deleteScheduledReport(button) {
  if (!window.confirm("确认删除该定时报告计划及其运行记录？")) return;
  button.disabled = true;
  try {
    await api(`/report-schedules/${button.dataset.reportDelete}`, { method: "DELETE" });
    showNotice("定时报告计划已删除", true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

function schedulePayload(item, enabled) {
  return { projectKey: item.projectKey, name: item.name, frequency: item.frequency, runTime: item.runTime,
    dayOfWeek: item.dayOfWeek, dayOfMonth: item.dayOfMonth, timezone: item.timezone, formats: item.formats,
    collectBeforeReport: item.collectBeforeReport, collectionLimit: item.collectionLimit,
    topPostLimit: item.topPostLimit, emailRecipients: item.emailRecipients,
    wechatConnectionId: item.wechatConnectionId, wechatRecipientId: item.wechatRecipientId, enabled,
    negativeEmailEnabled: Boolean(item.negativeEmailEnabled),
    negativeEmailMinimumRiskScore: item.negativeEmailMinimumRiskScore || 60,
    negativeEmailHighRiskOnly: Boolean(item.negativeEmailHighRiskOnly),
    negativeEmailCooldownMinutes: item.negativeEmailCooldownMinutes || 30 };
}

function scheduleFrequency(item) {
  if (item.frequency === "WEEKLY") return `每周${["", "一", "二", "三", "四", "五", "六", "日"][item.dayOfWeek]}`;
  if (item.frequency === "MONTHLY") return `每月 ${item.dayOfMonth} 日`;
  return "每日";
}

function splitEmails(value) { return String(value || "").split(/[,，\n]/).map(item => item.trim()).filter(Boolean); }
function reportRunClass(value) { return ({ SUCCEEDED: "succeeded", PARTIAL: "partial", FAILED: "failed", DELIVERING: "running", GENERATING: "running", ANALYZING: "running", COLLECTING: "running", QUEUED: "submitted" })[value] || "watch"; }
function reportRunStatus(value) { return ({ QUEUED: "等待", COLLECTING: "采集中", ANALYZING: "分析中", GENERATING: "生成中", DELIVERING: "发送中", SUCCEEDED: "成功", PARTIAL: "部分成功", FAILED: "失败", SKIPPED: "已跳过" })[value] || value; }

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
  const [health, metrics] = await Promise.all([
    api("/health"),
    api(`/analysis-metrics${query({ projectKey: state.projectKey, days: 7 })}`),
  ]);
  const fallbackRate = metrics.calls ? Math.round(metrics.fallbackCalls * 100 / metrics.calls) : 0;
  const cacheRate = metrics.calls ? Math.round(metrics.cacheCalls * 100 / metrics.calls) : 0;
  const imageCacheRate = metrics.imageCalls ? Math.round((metrics.imageCacheCalls || 0) * 100 / metrics.imageCalls) : 0;
  content.innerHTML = `<section class="panel"><div class="panel-header"><h2>服务检查</h2><span class="tag ${health.status === "UP" ? "normal" : "warning"}">${health.status}</span></div><div class="panel-body"><div class="detail-meta"><div><span>管理接口</span><strong>正常</strong></div><div><span>数据库</span><strong>${health.databaseUp ? "正常" : "异常"}</strong></div><div><span>采集配置</span><strong>${health.collectorEnabled ? "已启用" : "未启用"}</strong></div><div><span>采集 Sidecar</span><strong>${health.collectorUp ? "正常" : "异常"}</strong></div><div><span>运行中任务</span><strong>${health.runningJobs}</strong></div><div><span>检查时间</span><strong>${formatDate(health.checkedAt)}</strong></div></div><p class="muted">${escapeHtml(health.collectorMessage)}</p></div></section>
    <section class="panel"><div class="panel-header"><h2>近 7 天正文分析消耗</h2><span class="muted">${state.projectKey ? "当前项目" : "全部项目"}</span></div><div class="panel-body"><div class="detail-meta"><div><span>分析执行</span><strong>${metrics.calls}</strong></div><div><span>输入 Token</span><strong>${metrics.promptTokens}</strong></div><div><span>输出 Token</span><strong>${metrics.completionTokens}</strong></div><div><span>总 Token</span><strong>${metrics.totalTokens}</strong></div><div><span>平均耗时</span><strong>${metrics.averageDurationMs} ms</strong></div><div><span>缓存命中率</span><strong>${cacheRate}%</strong></div><div><span>规则降级率</span><strong>${fallbackRate}%</strong></div></div></div></section>
    <section class="panel"><div class="panel-header"><h2>近 7 天评论模型复核消耗</h2><span class="muted">全局批次统计</span></div><div class="panel-body"><div class="detail-meta"><div><span>模型批次</span><strong>${metrics.commentCalls || 0}</strong></div><div><span>复核评论</span><strong>${metrics.reviewedComments || 0}</strong></div><div><span>输入 Token</span><strong>${metrics.commentPromptTokens || 0}</strong></div><div><span>输出 Token</span><strong>${metrics.commentCompletionTokens || 0}</strong></div><div><span>总 Token</span><strong>${metrics.commentTotalTokens || 0}</strong></div><div><span>平均耗时</span><strong>${metrics.commentAverageDurationMs || 0} ms</strong></div><div><span>失败批次</span><strong>${metrics.commentFailedCalls || 0}</strong></div></div><p class="muted">评论可跨项目合并为一个批次，因此 Token 按全局批次展示，避免拆分后重复计算。</p></div></section>
    <section class="panel"><div class="panel-header"><h2>近 7 天图片分析消耗</h2><span class="muted">${state.projectKey ? "当前项目" : "全部项目"}</span></div><div class="panel-body"><div class="detail-meta"><div><span>图片执行</span><strong>${metrics.imageCalls || 0}</strong></div><div><span>输入 Token</span><strong>${metrics.imagePromptTokens || 0}</strong></div><div><span>输出 Token</span><strong>${metrics.imageCompletionTokens || 0}</strong></div><div><span>总 Token</span><strong>${metrics.imageTotalTokens || 0}</strong></div><div><span>平均耗时</span><strong>${metrics.imageAverageDurationMs || 0} ms</strong></div><div><span>缓存命中率</span><strong>${imageCacheRate}%</strong></div><div><span>失败执行</span><strong>${metrics.imageFailedCalls || 0}</strong></div></div></div></section>`;
}

function negativeEmailTable(deliveries) {
  return `<table><thead><tr><th>状态</th><th>风险</th><th>帖子</th><th>接收邮箱</th><th>尝试</th><th>创建/发送时间</th><th>说明</th><th>操作</th></tr></thead><tbody>${deliveries.map(item => `<tr>
    <td><span class="tag ${item.status === "SENT" ? "succeeded" : item.status === "FAILED" ? "failed" : "running"}">${negativeEmailStatus(item.status)}</span></td>
    <td><strong>${item.riskScore}</strong><div class="muted">${negativeRiskSource(item.riskSource)} · ${escapeHtml(item.riskCategory || "其他")}</div></td>
    <td class="title-cell"><strong>${escapeHtml(item.title || "无标题")}</strong><div class="muted">#${item.postId}</div></td>
    <td>${escapeHtml(item.recipientEmail)}</td><td>${item.attemptCount}</td>
    <td>${formatDate(item.createdAt)}${item.sentAt ? `<div class="muted">发送 ${formatDate(item.sentAt)}</div>` : ""}</td>
    <td class="title-cell muted">${escapeHtml(item.lastError || "-")}</td>
    <td><div class="inline-actions"><a class="button text" href="${postOpenUrl(item.postId)}" target="_blank" rel="noopener noreferrer">原帖</a>${item.status === "FAILED" ? `<button class="button secondary" data-negative-email-retry="${item.id}">重试</button>` : ""}</div></td>
  </tr>`).join("")}</tbody></table>`;
}

function negativeEmailStatus(status) {
  return ({ SENT: "已发送", FAILED: "失败", PENDING: "等待发送", PROCESSING: "发送中" })[status] || escapeHtml(status);
}

function negativeRiskSource(source) {
  return String(source || "POST").replaceAll("POST", "正文").replaceAll("COMMENT", "评论").replaceAll("IMAGE", "图片").replaceAll("+", " / ");
}

async function retryNegativeEmail(button) {
  button.disabled = true;
  try {
    await api(`/negative-email-deliveries/${button.dataset.negativeEmailRetry}/retry${query({ projectKey: state.projectKey })}`, { method: "POST", body: "{}" });
    showNotice("即时负面邮件已重新排队", true);
    await renderScheduledReports();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function renderAuthorization() {
  const auth = await api("/authorization");
  const statusClass = auth.collectAllowed ? "normal" : auth.status === "EXPIRED" ? "failed" : "warning";
  content.innerHTML = `<div class="auth-layout">
    <section class="panel">
      <div class="panel-header"><h2>授权状态</h2><span class="tag ${statusClass}">${authorizationStatus(auth.status)}</span></div>
      <div class="panel-body">
        <div class="detail-meta">
          <div><span>采集权限</span><strong>${auth.collectAllowed ? "可用" : "已暂停"}</strong></div>
          <div><span>授权账号</span><strong>${escapeHtml(auth.accountNickname || "-")}</strong></div>
          <div><span>小红书号</span><strong>${escapeHtml(auth.accountRedId || "-")}</strong></div>
          <div><span>授权方式</span><strong>${authorizationSource(auth.source)}</strong></div>
          <div><span>最近验证</span><strong>${formatDate(auth.lastVerifiedAt)}</strong></div>
          <div><span>连续失败</span><strong>${auth.consecutiveAuthFailures || 0}</strong></div>
        </div>
        ${auth.lastError ? `<p class="auth-error">${escapeHtml(auth.lastError)}</p>` : ""}
        <div class="form-actions">
          <button id="auth-validate" class="button secondary">验证授权</button>
          <button id="auth-qr-start" class="button primary">扫码重新授权</button>
          <button id="auth-clear" class="button danger">清除授权</button>
        </div>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>手动更新 Cookie</h2><span class="tag">备用</span></div>
      <div class="panel-body">
        <form id="auth-cookie-form">
          <label class="auth-cookie-field">完整 Request Cookie
            <input id="auth-cookie" type="password" autocomplete="off" required placeholder="a1=...; web_session=...">
          </label>
          <div class="option-row auth-cookie-options">
            <label><input id="auth-cookie-visible" type="checkbox">显示内容</label>
            <button class="button secondary" type="submit">验证并保存</button>
          </div>
        </form>
      </div>
    </section>
  </div><section id="auth-qr-panel" class="panel" hidden></section>`;
  document.querySelector("#auth-validate").addEventListener("click", validateAuthorization);
  document.querySelector("#auth-qr-start").addEventListener("click", startAuthorizationQr);
  document.querySelector("#auth-clear").addEventListener("click", clearAuthorization);
  document.querySelector("#auth-cookie-form").addEventListener("submit", updateAuthorizationCookie);
  document.querySelector("#auth-cookie-visible").addEventListener("change", event => {
    document.querySelector("#auth-cookie").type = event.target.checked ? "text" : "password";
  });
}

async function validateAuthorization(event) {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    await api("/authorization/validate", { method: "POST" });
    showNotice("账号授权验证成功", true);
    await renderAuthorization();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function updateAuthorizationCookie(event) {
  event.preventDefault();
  const button = event.submitter;
  button.disabled = true;
  try {
    await api("/authorization/cookie", {
      method: "POST",
      body: JSON.stringify({ cookie: document.querySelector("#auth-cookie").value }),
    });
    event.target.reset();
    showNotice("Cookie 已验证并加密保存", true);
    await renderAuthorization();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function startAuthorizationQr(event) {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const qr = await api("/authorization/qr", { method: "POST" });
    const panel = document.querySelector("#auth-qr-panel");
    panel.hidden = false;
    panel.innerHTML = `<div class="panel-header"><h2>小红书扫码授权</h2><span id="auth-qr-status" class="tag watch">等待扫码</span></div>
      <div class="panel-body qr-login"><img src="${escapeAttr(qr.qrImage)}" alt="小红书登录二维码">
      <div><strong id="auth-qr-message">${escapeHtml(qr.message)}</strong><p class="muted">二维码有效期至 ${formatDate(qr.expiresAt)}</p></div></div>`;
    authorizationPollTimer = setTimeout(() => pollAuthorizationQr(qr.sessionId), 1800);
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

async function pollAuthorizationQr(sessionId) {
  if (state.view !== "authorization") return;
  try {
    const result = await api(`/authorization/qr/${encodeURIComponent(sessionId)}`);
    const label = document.querySelector("#auth-qr-status");
    const message = document.querySelector("#auth-qr-message");
    if (label) label.textContent = qrAuthorizationStatus(result.status);
    if (message) message.textContent = result.message;
    if (result.status === "AUTHORIZED") {
      showNotice("小红书扫码授权成功", true);
      await renderAuthorization();
      return;
    }
    if (result.status === "EXPIRED") {
      showNotice("登录二维码已过期，请重新生成");
      return;
    }
    authorizationPollTimer = setTimeout(() => pollAuthorizationQr(sessionId), 1800);
  } catch (error) { showNotice(error.message); }
}

async function clearAuthorization(event) {
  if (!window.confirm("确认清除当前小红书账号授权？清除后采集任务将暂停。")) return;
  const button = event.currentTarget;
  button.disabled = true;
  try {
    await api("/authorization", { method: "DELETE" });
    showNotice("账号授权已清除", true);
    await renderAuthorization();
  } catch (error) { showNotice(error.message); } finally { button.disabled = false; }
}

function authorizationStatus(value) { return ({ MISSING: "未授权", CONFIGURED: "待验证", VALID: "有效", EXPIRED: "已失效" })[value] || value; }
function authorizationSource(value) { return ({ ENV: "环境变量导入", MANUAL: "手动更新", QR: "扫码授权" })[value] || "-"; }
function qrAuthorizationStatus(value) { return ({ SCAN_REQUIRED: "等待扫码", CONFIRM_REQUIRED: "等待确认", AUTHORIZED: "授权成功", EXPIRED: "已过期" })[value] || value; }

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
