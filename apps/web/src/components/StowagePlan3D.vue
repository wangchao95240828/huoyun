<script setup lang="ts">
/**
 * 3D 配载方案立体视图 — three.js
 *
 * props:
 *   plan: { container_*, items: [{ sku, customer_id, x_cm, y_cm, z_cm,
 *           placed_length_cm, placed_width_cm, placed_height_cm, placed, ... }] }
 *
 * 操作：鼠标拖拽旋转 / 滚轮缩放 / 右键平移
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import * as THREE from 'three';

const props = defineProps<{
  plan: any;
}>();

const containerEl = ref<HTMLDivElement | null>(null);
const customerColors = ref<Map<string, string>>(new Map());

let renderer: THREE.WebGLRenderer | null = null;
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let animId = 0;
let rotateX = -0.45, rotateY = 0.65;
let zoom = 1.0;
let isDragging = false;
let lastX = 0, lastY = 0;

const palette = [
  '#ef4444', '#f97316', '#eab308', '#22c55e', '#06b6d4',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6', '#a855f7',
];

function colorFor(cid: string | null): string {
  const key = cid || '_';
  let c = customerColors.value.get(key);
  if (!c) {
    c = palette[customerColors.value.size % palette.length];
    customerColors.value.set(key, c);
  }
  return c;
}

function buildScene() {
  if (!containerEl.value) return;

  // 清旧
  if (renderer) {
    renderer.dispose();
    if (containerEl.value.firstChild) containerEl.value.removeChild(containerEl.value.firstChild);
    cancelAnimationFrame(animId);
  }
  customerColors.value.clear();

  const w = containerEl.value.clientWidth;
  const h = containerEl.value.clientHeight;
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0xf1f5f9);

  // 用柜的真实尺寸（cm）作为单位，缩放为合理视野
  const cL = Number(props.plan.container_length_cm) || 1200;
  const cW = Number(props.plan.container_width_cm) || 230;
  const cH = Number(props.plan.container_height_cm) || 260;
  const scale = 1 / 30;  // 30cm = 1 unit
  const L = cL * scale, W = cW * scale, H = cH * scale;

  camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 1000);
  camera.position.set(L * 1.4, H * 1.4, W * 1.6);
  camera.lookAt(L / 2, H / 2, W / 2);

  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(w, h);
  renderer.setPixelRatio(window.devicePixelRatio);
  containerEl.value.appendChild(renderer.domElement);

  // 光照
  scene.add(new THREE.AmbientLight(0xffffff, 0.6));
  const dir = new THREE.DirectionalLight(0xffffff, 0.7);
  dir.position.set(L, H * 2, W);
  scene.add(dir);

  // 柜子（线框）
  const containerGeo = new THREE.BoxGeometry(L, H, W);
  const edges = new THREE.EdgesGeometry(containerGeo);
  const wireframe = new THREE.LineSegments(edges,
    new THREE.LineBasicMaterial({ color: 0x334155, linewidth: 2 }));
  wireframe.position.set(L / 2, H / 2, W / 2);
  scene.add(wireframe);

  // 地板（半透）
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(L, W),
    new THREE.MeshPhongMaterial({ color: 0xcbd5e1, side: THREE.DoubleSide, transparent: true, opacity: 0.5 }),
  );
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(L / 2, 0, W / 2);
  scene.add(floor);

  // 门标记（卸货方向）
  const doorMaterial = new THREE.MeshBasicMaterial({ color: 0x10b981, transparent: true, opacity: 0.3, side: THREE.DoubleSide });
  const door = new THREE.Mesh(new THREE.PlaneGeometry(W, H), doorMaterial);
  door.position.set(L, H / 2, W / 2);
  door.rotation.y = Math.PI / 2;
  scene.add(door);

  // 装入的箱子（绿/橙/红... 按客户分色）
  const items = (props.plan.items || []).filter((it: any) => it.placed);
  for (const it of items) {
    const ix = (Number(it.x_cm) || 0) * scale;
    const iy = (Number(it.y_cm) || 0) * scale;  // y 是柜宽（横向）
    const iz = (Number(it.z_cm) || 0) * scale;  // z 是柜高（竖向）
    const iL = (Number(it.placed_length_cm) || 30) * scale;
    const iW = (Number(it.placed_width_cm) || 30) * scale;
    const iH = (Number(it.placed_height_cm) || 30) * scale;

    const color = new THREE.Color(colorFor(it.customer_id));
    const mat = new THREE.MeshPhongMaterial({
      color,
      transparent: true,
      opacity: 0.85,
    });
    const box = new THREE.Mesh(new THREE.BoxGeometry(iL, iH, iW), mat);
    box.position.set(ix + iL / 2, iz + iH / 2, iy + iW / 2);
    scene.add(box);

    // 边缘描线
    const boxEdges = new THREE.LineSegments(
      new THREE.EdgesGeometry(box.geometry),
      new THREE.LineBasicMaterial({ color: 0x1e293b }),
    );
    boxEdges.position.copy(box.position);
    scene.add(boxEdges);
  }

  // 鼠标事件
  const dom = renderer.domElement;
  dom.style.cursor = 'grab';
  dom.addEventListener('mousedown', (e) => {
    isDragging = true; lastX = e.clientX; lastY = e.clientY;
    dom.style.cursor = 'grabbing';
  });
  window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    rotateY += (e.clientX - lastX) * 0.005;
    rotateX += (e.clientY - lastY) * 0.005;
    rotateX = Math.max(-1.2, Math.min(1.2, rotateX));
    lastX = e.clientX; lastY = e.clientY;
  });
  window.addEventListener('mouseup', () => {
    isDragging = false;
    dom.style.cursor = 'grab';
  });
  dom.addEventListener('wheel', (e) => {
    e.preventDefault();
    zoom *= e.deltaY > 0 ? 1.1 : 0.9;
    zoom = Math.max(0.3, Math.min(5, zoom));
  });

  // 动画循环
  function render() {
    if (!renderer || !scene || !camera) return;
    const dist = Math.max(L, W, H) * 1.8 * zoom;
    camera.position.x = L / 2 + dist * Math.cos(rotateX) * Math.sin(rotateY);
    camera.position.y = H / 2 + dist * Math.sin(rotateX);
    camera.position.z = W / 2 + dist * Math.cos(rotateX) * Math.cos(rotateY);
    camera.lookAt(L / 2, H / 2, W / 2);
    renderer.render(scene, camera);
    animId = requestAnimationFrame(render);
  }
  render();
}

onMounted(buildScene);
watch(() => props.plan, buildScene);

onBeforeUnmount(() => {
  cancelAnimationFrame(animId);
  if (renderer) renderer.dispose();
});

function previewUrl(): string {
  if (!props.plan?.id) return '';
  const base = (import.meta as any).env?.VITE_API_BASE || '';
  return `${base}/api/acc/stowage/plan/${props.plan.id}/preview.png`;
}

function legendList() {
  return Array.from(customerColors.value.entries()).map(([cid, color]) => {
    const cnt = (props.plan?.items || []).filter((it: any) =>
      (it.customer_id || '_') === cid && it.placed).length;
    return { customer: cid === '_' ? '(无客户)' : cid, color, count: cnt };
  });
}
</script>

<template>
  <div class="plan3d-wrap">
    <div ref="containerEl" class="plan3d-canvas" />
    <div class="plan3d-info">
      <div class="plan3d-section">
        <strong>方案 {{ plan.plan_no }}</strong>
        <span class="plan3d-badge" :class="plan.status?.toLowerCase()">{{ plan.status }}</span>
      </div>
      <div class="plan3d-section">
        <div>柜号: {{ plan.container_code }}</div>
        <div>柜内: {{ plan.container_length_cm }}×{{ plan.container_width_cm }}×{{ plan.container_height_cm }} cm</div>
        <div>载重上限: {{ plan.container_max_weight_kg }} kg</div>
      </div>
      <div class="plan3d-section">
        <div>✓ 装入: <strong>{{ plan.fitted_count }}</strong> 件</div>
        <div>✗ 未装: <strong>{{ plan.unfitted_count }}</strong> 件</div>
        <div>利用率: <strong>{{ ((plan.volume_utilization || 0) * 100).toFixed(1) }}%</strong></div>
        <div>使用重量: <strong>{{ plan.weight_used_kg }}</strong> / {{ plan.container_max_weight_kg }} kg</div>
      </div>
      <div class="plan3d-section">
        <div>重心: ({{ plan.gravity_center_x_cm }}, {{ plan.gravity_center_y_cm }}, {{ plan.gravity_center_z_cm }}) cm</div>
        <div v-if="plan.gravity_quadrants">4 象限重量分布:</div>
        <div v-if="plan.gravity_quadrants" style="font-size:11px;color:#64748b">
          Q1 左前 {{ Math.round((plan.gravity_quadrants[0] || 0) * 100) }}% /
          Q2 右前 {{ Math.round((plan.gravity_quadrants[1] || 0) * 100) }}%<br/>
          Q3 左后 {{ Math.round((plan.gravity_quadrants[2] || 0) * 100) }}% /
          Q4 右后 {{ Math.round((plan.gravity_quadrants[3] || 0) * 100) }}%
        </div>
      </div>
      <div class="plan3d-section plan3d-legend">
        <strong>图例 (按客户)</strong>
        <div v-for="l in legendList()" :key="l.customer" class="plan3d-legend-row">
          <span class="plan3d-color" :style="{ background: l.color }" />
          {{ l.customer }} ({{ l.count }} 件)
        </div>
      </div>
      <div class="plan3d-section" v-if="(plan.warnings || []).length">
        <strong style="color:#dc2626">⚠ 警告</strong>
        <div v-for="(w, i) in plan.warnings" :key="i" style="font-size:11px;color:#dc2626;line-height:1.4">
          {{ w }}
        </div>
      </div>
      <div class="plan3d-section" style="font-size:11px;color:#64748b">
        💡 鼠标拖拽旋转 / 滚轮缩放<br/>
        🟢 绿色面 = 门（卸货方向）
      </div>
      <div class="plan3d-section" v-if="plan?.id">
        <strong>服务端预览图</strong>
        <a :href="previewUrl()" target="_blank" rel="noopener" style="display:block;margin-top:6px">
          <img :src="previewUrl()" alt="3D 配载预览"
               style="width:100%;border:1px solid #e2e8f0;border-radius:4px;cursor:zoom-in"
               loading="lazy" />
        </a>
        <div style="font-size:11px;color:#94a3b8;margin-top:4px">
          点图放大新窗口打开 (可右键下载 / 嵌入邮件)
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plan3d-wrap {
  display: flex;
  height: 600px;
  background: #fff;
}
.plan3d-canvas {
  flex: 1;
  min-width: 0;
  background: #f1f5f9;
}
.plan3d-info {
  width: 260px;
  border-left: 1px solid #e2e8f0;
  overflow-y: auto;
  padding: 12px;
  font-size: 12px;
  color: #1e293b;
  background: #fff;
}
.plan3d-section {
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}
.plan3d-section:last-child { border-bottom: 0; }
.plan3d-section > div { margin: 2px 0; }
.plan3d-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}
.plan3d-badge.solved { background: #dbeafe; color: #1e40af; }
.plan3d-badge.approved { background: #d1fae5; color: #065f46; }
.plan3d-badge.draft { background: #f3f4f6; color: #6b7280; }
.plan3d-legend-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
}
.plan3d-color {
  width: 14px;
  height: 14px;
  border-radius: 2px;
  display: inline-block;
}
</style>
