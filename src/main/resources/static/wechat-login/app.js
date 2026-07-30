import * as THREE from "/webjars/three/0.169.0/build/three.module.js";

const canvas = document.querySelector("#particle-canvas");
const qrCanvas = document.querySelector("#qr-canvas");
const message = document.querySelector("#status-message");
const detail = document.querySelector("#status-detail");
const title = document.querySelector("h1");
const eyebrow = document.querySelector(".eyebrow");
const roleCaption = document.querySelector("#role-caption");
const floatingMessageLayer = document.querySelector("#floating-message-layer");
const sessionId = new URLSearchParams(location.search).get("session");
const addUserButton = document.querySelector("#add-user-button");
const connectionPanel = document.querySelector("#connection-panel");
const connectionList = document.querySelector("#connection-list");
const mobilePanelToggle = document.querySelector("#mobile-panel-toggle");
const loginDialog = document.querySelector("#new-login-dialog");
const dialogQrCanvas = document.querySelector("#dialog-qr-canvas");
const dialogStatus = document.querySelector("#dialog-status");
const closeDialogButton = document.querySelector("#close-dialog");
const statusLabels = {
  WAITING: ["请使用微信扫描二维码", "二维码保持固定，背景粒子可以拖动交互"],
  SCANNED: ["已扫码，请在微信中确认登录", "请在手机微信中确认登录"],
  LOGGED_IN: ["微信登录成功", "机器人正在建立消息连接"],
  EXPIRED: ["二维码已过期", "请重新启动机器人获取新的二维码"],
  ERROR: ["登录状态获取失败", "请查看 IDEA 日志后重试"]
};

const roleVisuals = {
  PATIENT: {
    bodyClass: "role-patient",
    title: "患者身份扫码登录",
    eyebrow: "PATIENT ACCESS",
    caption: "扫码后通过微信完成提醒、打卡和安全确认。",
    colorA: [0.18, 0.92, 0.68],
    colorB: [0.35, 0.58, 1.0],
    phrases: [
      "自愈的路上，你从不孤单",
      "坚持日常，遇见新生",
      "今天完成一点点，也是在向前走",
      "认真打卡，是给明天的自己一点确定",
      "请慢慢来，我们会陪你一起完成",
      "每一次确认安全，都很重要",
      "小步骤，也会带来大改变"
    ]
  },
  CAREGIVER: {
    bodyClass: "role-caregiver",
    title: "家属身份扫码登录",
    eyebrow: "CAREGIVER ACCESS",
    caption: "扫码后查看患者状态、异常提醒和照护摘要。",
    colorA: [1.0, 0.72, 0.36],
    colorB: [1.0, 0.44, 0.58],
    phrases: [
      "守护在身边，安心每一天",
      "家人同心，照护有光",
      "你关心的每一次查看，都有意义",
      "及时了解状态，让陪伴更踏实",
      "异常提醒会帮你更早发现变化",
      "把担心交给系统，把陪伴留给家人",
      "一份状态摘要，也是一份安心"
    ]
  },
  DOCTOR: {
    bodyClass: "role-doctor",
    title: "医生身份扫码登录",
    eyebrow: "DOCTOR ACCESS",
    caption: "扫码后查看患者、处理告警、审核计划和调整任务。",
    colorA: [0.24, 0.58, 1.0],
    colorB: [0.38, 0.92, 1.0],
    phrases: [
      "专业守护，精准照护",
      "每一次关注，都托举康复",
      "告警聚合，让风险更早被看见",
      "计划可追踪，任务可调整",
      "把重复提醒交给系统，把判断留给医生",
      "数据沉淀，服务连续照护",
      "清晰状态，辅助更稳的决策"
    ]
  },
  DEFAULT: {
    bodyClass: "",
    title: "微信扫码登录",
    eyebrow: "iLink BOT",
    caption: "正在识别本次登录身份，请稍候。",
    colorA: [0.12, 0.60, 1.0],
    colorB: [0.62, 0.26, 1.0],
    phrases: []
  }
};

let renderer;
let scene;
let camera;
let cloud;
let clock;
let particleCount = 0;
let activeRole = "DEFAULT";
let activeTargetIndex = 0;
let targetCycleTimer;
let floatingMessageTimer;
let dragging = false;
let lastPointer = { x: 0, y: 0 };
let targetRotation = { x: -0.08, y: 0.2 };
let velocity = { x: 0, y: 0 };
let dragEnergy = 0;

function setStatus(status) {
  const labels = statusLabels[status] || ["正在等待登录状态", "请稍候"];
  message.textContent = labels[0];
  detail.textContent = labels[1];
  message.style.color = status === "LOGGED_IN" ? "#7dffbf" : (status === "ERROR" || status === "EXPIRED" ? "#ff9c9c" : "#d9f7e7");
}

function drawQr(matrix, matrixSize, targetCanvas = qrCanvas) {
  if (!Array.isArray(matrix) || !matrix.length || !matrixSize) throw new Error("二维码矩阵为空");
  const size = 768;
  const context = targetCanvas.getContext("2d", { alpha: false });
  targetCanvas.width = size;
  targetCanvas.height = size;
  context.imageSmoothingEnabled = false;
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, size, size);
  const cell = size / matrixSize;
  context.fillStyle = "#000000";
  matrix.forEach((row, y) => {
    [...row].forEach((value, x) => {
      if (value === "1") {
        const left = Math.floor(x * cell);
        const top = Math.floor(y * cell);
        const right = Math.ceil((x + 1) * cell);
        const bottom = Math.ceil((y + 1) * cell);
        context.fillRect(left, top, right - left, bottom - top);
      }
    });
  });
  targetCanvas.classList.add("ready");
}

function normalizeRole(role) {
  const value = String(role || "").trim().toUpperCase();
  if (value === "PARENT" || value === "PARENTS" || value === "FAMILY") return "CAREGIVER";
  return roleVisuals[value] ? value : "DEFAULT";
}

function applyLoginRole(role) {
  const roleKey = normalizeRole(role);
  if (roleKey === activeRole && (roleKey === "DEFAULT" || floatingMessageTimer)) return;
  activeRole = roleKey;
  activeTargetIndex = 0;
  const visual = roleVisuals[roleKey];
  document.body.classList.remove("role-patient", "role-caregiver", "role-doctor");
  if (visual.bodyClass) document.body.classList.add(visual.bodyClass);
  title.textContent = visual.title;
  eyebrow.textContent = visual.eyebrow;
  if (roleCaption) roleCaption.textContent = visual.caption;
  clearInterval(targetCycleTimer);
  targetCycleTimer = null;
  if (roleKey === "DEFAULT") {
    stopFloatingMessages();
    if (cloud) cloud.visible = true;
    return;
  }
  if (cloud) cloud.visible = false;
  startFloatingMessages(roleKey);
}

function startFloatingMessages(roleKey) {
  stopFloatingMessages();
  if (!floatingMessageLayer) return;
  for (let index = 0; index < 8; index++) {
    setTimeout(() => spawnFloatingMessage(roleKey), index * 280);
  }
  floatingMessageTimer = setInterval(() => {
    const count = 1 + Math.floor(Math.random() * 3);
    for (let index = 0; index < count; index++) {
      setTimeout(() => spawnFloatingMessage(roleKey), index * 180);
    }
  }, 1700);
}

function stopFloatingMessages() {
  clearInterval(floatingMessageTimer);
  floatingMessageTimer = null;
  if (floatingMessageLayer) floatingMessageLayer.replaceChildren();
}

function spawnFloatingMessage(roleKey) {
  const phrases = roleVisuals[roleKey]?.phrases || [];
  if (!floatingMessageLayer || !phrases.length) return;
  const item = document.createElement("span");
  const sizeClass = Math.random() > 0.72 ? "large" : (Math.random() > 0.5 ? "small" : "");
  item.className = `floating-message ${sizeClass}`.trim();
  item.textContent = phrases[Math.floor(Math.random() * phrases.length)];
  const position = randomMessagePosition();
  item.style.left = "";
  item.style.right = "";
  item.style[position.anchor] = `${position.offset}%`;
  item.style.top = `${position.top}%`;
  item.style.animationDuration = `${4.8 + Math.random() * 2.2}s`;
  floatingMessageLayer.append(item);
  window.setTimeout(() => item.remove(), 7600);
}

function randomMessagePosition() {
  const zones = [
    { anchor: "left", offset: [1, 6], top: [8, 22] },
    { anchor: "left", offset: [1, 6], top: [30, 46] },
    { anchor: "left", offset: [1, 6], top: [62, 84] },
    { anchor: "right", offset: [1, 6], top: [8, 22] },
    { anchor: "right", offset: [1, 6], top: [30, 46] },
    { anchor: "right", offset: [1, 6], top: [62, 84] }
  ];
  const zone = zones[Math.floor(Math.random() * zones.length)];
  return {
    anchor: zone.anchor,
    offset: randomBetween(zone.offset[0], zone.offset[1]),
    top: randomBetween(zone.top[0], zone.top[1])
  };
}

function randomBetween(min, max) {
  return min + Math.random() * (max - min);
}

function updateParticleTarget(roleKey, targetIndex) {
  if (!cloud || !particleCount) return;
  const targets = roleVisuals[roleKey]?.targets || roleVisuals.DEFAULT.targets;
  const target = targets[targetIndex % targets.length];
  const positions = target.type === "heart"
    ? heartTargetPositions(particleCount)
    : textTargetPositions(particleCount, target.value);
  cloud.geometry.setAttribute("aTarget", new THREE.BufferAttribute(positions, 3));
  cloud.geometry.attributes.aTarget.needsUpdate = true;
}

function textTargetPositions(count, text) {
  const width = 1200;
  const height = 360;
  const offscreen = document.createElement("canvas");
  offscreen.width = width;
  offscreen.height = height;
  const context = offscreen.getContext("2d");
  context.clearRect(0, 0, width, height);
  context.fillStyle = "#fff";
  context.textAlign = "center";
  context.textBaseline = "middle";
  const lines = splitParticleText(text);
  const longestLine = Math.max(...lines.map((line) => line.length));
  const fontSize = longestLine > 6 ? 132 : 152;
  context.font = `700 ${fontSize}px "Microsoft YaHei", "PingFang SC", sans-serif`;
  lines.forEach((line, index) => {
    const lineY = height / 2 + (index - (lines.length - 1) / 2) * fontSize * 1.02;
    context.fillText(line, width / 2, lineY, width - 72);
  });
  const image = context.getImageData(0, 0, width, height).data;
  const samples = [];
  const step = 3;
  for (let y = 0; y < height; y += step) {
    for (let x = 0; x < width; x += step) {
      if (image[(y * width + x) * 4 + 3] > 32) {
        samples.push([x, y]);
      }
    }
  }
  if (!samples.length) return heartTargetPositions(count);
  const result = new Float32Array(count * 3);
  for (let index = 0; index < count; index++) {
    const sample = samples[index % samples.length];
    const offset = index * 3;
    result[offset] = (sample[0] / width - 0.5) * 2.65 + randomJitter(0.006);
    result[offset + 1] = (0.5 - sample[1] / height) * 1.72 + 0.72 + randomJitter(0.006);
    result[offset + 2] = randomJitter(0.08) - 0.18;
  }
  return result;
}

function splitParticleText(text) {
  if (text.includes("|")) return text.split("|").filter(Boolean);
  if (text.length <= 10) return [text];
  const punctuationIndex = Math.max(text.indexOf("，"), text.indexOf("、"));
  if (punctuationIndex > 1 && punctuationIndex < text.length - 2) {
    return [text.slice(0, punctuationIndex), text.slice(punctuationIndex + 1)];
  }
  const middle = Math.ceil(text.length / 2);
  return [text.slice(0, middle), text.slice(middle)];
}

function heartTargetPositions(count) {
  const result = new Float32Array(count * 3);
  for (let index = 0; index < count; index++) {
    const t = (index / count) * Math.PI * 2 * 18 + Math.random() * 0.08;
    const radius = Math.sqrt(Math.random());
    const x = 16 * Math.pow(Math.sin(t), 3);
    const y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
    const offset = index * 3;
    result[offset] = x / 8.6 * radius + randomJitter(0.018);
    result[offset + 1] = y / 11.5 * radius + 0.34 + randomJitter(0.018);
    result[offset + 2] = randomJitter(0.2) - 0.22;
  }
  return result;
}

function randomJitter(amount) {
  return (Math.random() - 0.5) * amount;
}

function createParticleCloud() {
  const count = innerWidth < 700 ? 14000 : 32000;
  const positions = new Float32Array(count * 3);
  const seeds = new Float32Array(count * 4);
  const sizes = new Float32Array(count);
  for (let index = 0; index < count; index++) {
    const offset = index * 3;
    const u = Math.random();
    const v = Math.random();
    const theta = u * Math.PI * 2;
    const phi = Math.acos(2 * v - 1);
    const radius = 1.0 + (Math.random() - 0.5) * 0.24;
    positions[offset] = Math.sin(phi) * Math.cos(theta) * radius;
    positions[offset + 1] = Math.cos(phi) * radius;
    positions[offset + 2] = Math.sin(phi) * Math.sin(theta) * radius;
    const seedOffset = index * 4;
    seeds[seedOffset] = Math.random();
    seeds[seedOffset + 1] = Math.random();
    seeds[seedOffset + 2] = Math.random();
    seeds[seedOffset + 3] = Math.random();
    sizes[index] = 0.7 + Math.pow(Math.random(), 2) * 2.8;
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("aSeed", new THREE.BufferAttribute(seeds, 4));
  geometry.setAttribute("aSize", new THREE.BufferAttribute(sizes, 1));
  const material = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: {
      uTime: { value: 0 },
      uDpr: { value: Math.min(devicePixelRatio, 2) },
      uDragEnergy: { value: 0 },
      uColorShift: { value: 0 }
    },
    vertexShader: `
      attribute vec4 aSeed;
      attribute float aSize;
      uniform float uTime;
      uniform float uDpr;
      uniform float uDragEnergy;
      varying float vDepth;
      varying float vSeed;
      varying float vEdge;
      void main() {
        float t = uTime * 0.24;
        vec3 sphere = normalize(position) * (1.22 + 0.22 * sin(t * 1.7 + aSeed.x * 18.0));
        float angle = aSeed.x * 6.2831853 + t * 0.42;
        float tube = 0.25 + (aSeed.y - 0.5) * 0.13;
        float ring = 1.02 + (aSeed.z - 0.5) * 0.28;
        vec3 torus = vec3((ring + tube * cos(aSeed.y * 6.2831853)) * cos(angle), tube * sin(aSeed.y * 6.2831853), (ring + tube * cos(aSeed.y * 6.2831853)) * sin(angle));
        vec3 wave = vec3((aSeed.x - 0.5) * 3.25, sin((aSeed.x * 5.0 + t) * 2.2) * 0.48 + (aSeed.y - 0.5) * 0.42, (aSeed.z - 0.5) * 1.75 + cos(aSeed.x * 11.0 + t) * 0.28);
        float morph = 0.5 + 0.5 * sin(t);
        float secondMorph = 0.5 + 0.5 * sin(t * 0.63 + 2.1);
        vec3 p = mix(sphere, torus, smoothstep(0.16, 0.84, morph));
        p = mix(p, wave, smoothstep(0.62, 1.0, secondMorph) * 0.56);
        p += normalize(p + vec3(0.001)) * sin(uTime * 0.65 + aSeed.w * 20.0) * (0.025 + uDragEnergy * 0.07);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        float perspective = clamp(2.8 / max(1.0, -mv.z), 0.72, 2.3);
        gl_PointSize = max(1.0, aSize * uDpr * perspective * (1.0 + uDragEnergy * 0.7));
        vDepth = perspective;
        vSeed = aSeed.x + aSeed.z * 2.0;
        vEdge = abs(dot(normalize(p), normalize(vec3(0.25, 0.3, 1.0))));
      }
    `,
    fragmentShader: `
      uniform float uTime;
      uniform float uColorShift;
      varying float vDepth;
      varying float vSeed;
      varying float vEdge;
      void main() {
        vec2 c = gl_PointCoord - 0.5;
        float dotShape = 1.0 - smoothstep(0.22, 0.5, length(c));
        vec3 deepBlue = vec3(0.03, 0.22, 1.0);
        vec3 electricBlue = vec3(0.12, 0.60, 1.0);
        vec3 violet = vec3(0.62, 0.26, 1.0);
        float flow = 0.5 + 0.5 * sin(uTime * 0.16 + vSeed * 9.0 + uColorShift * 4.0);
        vec3 color = mix(deepBlue, electricBlue, smoothstep(0.12, 0.72, flow));
        color = mix(color, violet, smoothstep(0.68, 1.0, flow + uColorShift * 0.32));
        color += (1.0 - vEdge) * vec3(0.08, 0.16, 0.3);
        float alpha = dotShape * clamp(0.24 + vDepth * 0.36, 0.24, 0.95);
        gl_FragColor = vec4(color, alpha);
      }
    `
  });
  cloud = new THREE.Points(geometry, material);
  cloud.rotation.set(targetRotation.x, targetRotation.y, 0);
  scene.add(cloud);
}

function initBackground() {
  try {
    scene = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(44, innerWidth / Math.max(1, innerHeight), 0.1, 20);
    camera.position.z = 3.15;
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false, powerPreference: "high-performance" });
    renderer.setClearColor(0x000000, 1);
    renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    renderer.setSize(innerWidth, innerHeight, false);
    clock = new THREE.Clock();
    createParticleCloud();
    applyLoginRole("DEFAULT");
    addEventListener("resize", resize);
    canvas.addEventListener("pointerdown", pointerDown);
    addEventListener("pointermove", pointerMove, { passive: true });
    addEventListener("pointerup", pointerUp, { passive: true });
    addEventListener("pointercancel", pointerUp, { passive: true });
    animate();
  } catch (error) {
    canvas.hidden = true;
    console.error("Particle background initialization failed", error);
  }
}

function pointerDown(event) {
  dragging = true;
  lastPointer = { x: event.clientX, y: event.clientY };
  canvas.setPointerCapture?.(event.pointerId);
}

function pointerMove(event) {
  if (!dragging) return;
  const dx = event.clientX - lastPointer.x;
  const dy = event.clientY - lastPointer.y;
  lastPointer = { x: event.clientX, y: event.clientY };
  velocity.y = dx * 0.0045;
  velocity.x = dy * 0.0045;
  targetRotation.y += velocity.y;
  targetRotation.x += velocity.x;
  targetRotation.x = Math.max(-1.15, Math.min(1.15, targetRotation.x));
  dragEnergy = Math.min(1, dragEnergy + Math.hypot(dx, dy) * 0.012);
}

function pointerUp() {
  dragging = false;
}

function resize() {
  if (!renderer || !camera) return;
  camera.aspect = innerWidth / Math.max(1, innerHeight);
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight, false);
}

function animate() {
  requestAnimationFrame(animate);
  if (!renderer || !cloud) return;
  const elapsed = clock.getElapsedTime();
  if (!dragging) {
    targetRotation.y += velocity.y;
    targetRotation.x += velocity.x;
    velocity.x *= 0.94;
    velocity.y *= 0.94;
    targetRotation.y += 0.0007;
    dragEnergy *= 0.955;
  }
  cloud.rotation.x += (targetRotation.x - cloud.rotation.x) * 0.09;
  cloud.rotation.y += (targetRotation.y - cloud.rotation.y) * 0.09;
  cloud.material.uniforms.uTime.value = elapsed;
  cloud.material.uniforms.uDragEnergy.value = dragEnergy;
  cloud.material.uniforms.uColorShift.value = Math.sin(targetRotation.y) * 0.5 + dragEnergy * 0.65;
  renderer.render(scene, camera);
}

async function fetchSession(targetSessionId, targetCanvas = qrCanvas, shouldApplyRole = targetCanvas === qrCanvas) {
  if (!targetSessionId) throw new Error("缺少登录会话");
  const response = await fetch(`/api/wechat-login/${encodeURIComponent(targetSessionId)}`, { cache: "no-store" });
  if (!response.ok) throw new Error("登录会话不存在或已过期");
  const data = await response.json();
  if (shouldApplyRole) applyLoginRole(data.requestedRole);
  if (!targetCanvas.classList.contains("ready")) drawQr(data.matrix, data.matrixSize, targetCanvas);
  return data;
}

async function poll() {
  try {
    const data = await fetchSession(sessionId);
    setStatus(data.status);
    if (["LOGGED_IN", "EXPIRED", "ERROR"].includes(data.status)) return;
  } catch (error) {
    message.textContent = error.message;
    detail.textContent = "请返回 IDEA 查看机器人日志";
  }
  setTimeout(poll, 1500);
}

function connectionStateLabel(connection) {
  if (connection.state === "RUNNING") return connection.processingState === "PROCESSING" ? "处理中" : "在线";
  if (connection.state === "WAITING_FOR_SCAN") return "待扫码";
  if (connection.state === "ERROR") return "异常";
  return "已停止";
}

function renderManager(snapshot) {
  document.querySelector("#connected-count").textContent = snapshot.connectedCount;
  document.querySelector("#mobile-connected-count").textContent = snapshot.connectedCount;
  document.querySelector("#online-count").textContent = snapshot.connectedCount;
  document.querySelector("#pending-count").textContent = snapshot.pendingCount;
  document.querySelector("#active-count").textContent = snapshot.activeTasks;
  document.querySelector("#queued-count").textContent = snapshot.queuedTasks;
  document.querySelector("#capacity-detail").textContent =
    `${snapshot.totalConnections}/${snapshot.maxConnections} 连接 · ${snapshot.workerThreads} 工作线程 · 模型并发 ${snapshot.modelMaxConcurrency}`;
  addUserButton.disabled = snapshot.totalConnections >= snapshot.maxConnections || snapshot.pendingCount >= snapshot.maxPendingLogins;
  connectionList.replaceChildren(...snapshot.connections.map((connection, index) => {
    const item = document.createElement("article");
    item.className = `connection-item state-${connection.state.toLowerCase()}`;
    const title = document.createElement("div");
    title.className = "connection-title";
    const stateDot = document.createElement("span");
    stateDot.className = "state-dot";
    const name = document.createElement("strong");
    name.textContent = connection.displayName || `用户 ${index + 1}`;
    const state = document.createElement("em");
    state.textContent = connectionStateLabel(connection);
    title.append(stateDot, name, state);
    const meta = document.createElement("p");
    meta.textContent = `队列 ${connection.queuedMessages} · 处理中 ${connection.activeMessages}`;
    item.append(title, meta);
    if (connection.lastError) {
      const error = document.createElement("p");
      error.className = "connection-error";
      error.textContent = connection.lastError;
      item.append(error);
    }
    return item;
  }));
}

async function pollManager() {
  try {
    const response = await fetch("/api/clawbot/connections", { cache: "no-store" });
    if (response.ok) renderManager(await response.json());
  } catch (error) {
    document.querySelector("#capacity-detail").textContent = "状态服务暂时不可用";
  }
  setTimeout(pollManager, 1200);
}

async function pollDialogSession(targetSessionId) {
  try {
    const data = await fetchSession(targetSessionId, dialogQrCanvas, false);
    const labels = statusLabels[data.status] || [data.message, ""];
    dialogStatus.textContent = labels[0];
    if (["LOGGED_IN", "EXPIRED", "ERROR"].includes(data.status)) return;
  } catch (error) {
    dialogStatus.textContent = error.message;
    return;
  }
  setTimeout(() => pollDialogSession(targetSessionId), 1200);
}

async function addUser() {
  addUserButton.disabled = true;
  dialogQrCanvas.classList.remove("ready");
  dialogStatus.textContent = "正在生成二维码...";
  loginDialog.showModal();
  try {
    const roleQuery = activeRole && activeRole !== "DEFAULT"
      ? `?role=${encodeURIComponent(activeRole)}`
      : "";
    const response = await fetch(`/api/clawbot/connections${roleQuery}`, { method: "POST" });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "无法新增连接");
    await pollDialogSession(body.loginSessionId);
  } catch (error) {
    dialogStatus.textContent = error.message;
  } finally {
    addUserButton.disabled = false;
  }
}

addUserButton.addEventListener("click", addUser);
closeDialogButton.addEventListener("click", () => loginDialog.close());
mobilePanelToggle.addEventListener("click", () => {
  const open = connectionPanel.classList.toggle("mobile-open");
  mobilePanelToggle.setAttribute("aria-expanded", String(open));
});

initBackground();
poll();
pollManager();

