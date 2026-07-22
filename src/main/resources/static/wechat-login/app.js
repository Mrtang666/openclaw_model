import * as THREE from "/webjars/three/0.169.0/build/three.module.js";

const canvas = document.querySelector("#particle-canvas");
const qrCanvas = document.querySelector("#qr-canvas");
const message = document.querySelector("#status-message");
const detail = document.querySelector("#status-detail");
const sessionId = new URLSearchParams(location.search).get("session");
const statusLabels = {
  WAITING: ["请使用微信扫描二维码", "二维码保持固定，背景粒子可以拖动交互"],
  SCANNED: ["已扫码，请在微信中确认登录", "请在手机微信中确认登录"],
  LOGGED_IN: ["微信登录成功", "机器人正在建立消息连接"],
  EXPIRED: ["二维码已过期", "请重新启动机器人获取新的二维码"],
  ERROR: ["登录状态获取失败", "请查看 IDEA 日志后重试"]
};

let renderer;
let scene;
let camera;
let cloud;
let clock;
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

function drawQr(matrix, matrixSize) {
  if (!Array.isArray(matrix) || !matrix.length || !matrixSize) throw new Error("二维码矩阵为空");
  const size = 768;
  const context = qrCanvas.getContext("2d", { alpha: false });
  qrCanvas.width = size;
  qrCanvas.height = size;
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
  qrCanvas.classList.add("ready");
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

async function fetchSession() {
  if (!sessionId) throw new Error("缺少登录会话");
  const response = await fetch(`/api/wechat-login/${encodeURIComponent(sessionId)}`, { cache: "no-store" });
  if (!response.ok) throw new Error("登录会话不存在或已过期");
  const data = await response.json();
  if (!qrCanvas.classList.contains("ready")) drawQr(data.matrix, data.matrixSize);
  setStatus(data.status);
  return data.status;
}

async function poll() {
  try {
    const status = await fetchSession();
    if (["LOGGED_IN", "EXPIRED", "ERROR"].includes(status)) return;
  } catch (error) {
    message.textContent = error.message;
    detail.textContent = "请返回 IDEA 查看机器人日志";
  }
  setTimeout(poll, 1500);
}

initBackground();
poll();

