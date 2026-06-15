"""
跑一次配载 → 渲染 3D PNG 图片 (matplotlib).

用法:
  source .venv/bin/activate && python render_png.py
  → 生成 /tmp/stowage_3d_demo.png
"""
import matplotlib
matplotlib.use('Agg')                  # 不开 GUI
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
import numpy as np

from schemas import Container, Item, PackRequest
import solver


# ─── 真实业务场景示例：40HC 装 3 客户混拼 ───
container = Container(
    code="40HC-PREVIEW",
    length_cm=1200, width_cm=230, height_cm=260,
    max_weight_kg=26000,
)

items = []
# 客户 A — 5 件电器 (this_side_up)
for i in range(5):
    items.append(Item(
        sku=f"A-{i+1:02d}", customer_id="A-电商客户",
        length_cm=80, width_cm=60, height_cm=50,
        weight_kg=40, this_side_up=True,
    ))
# 客户 B — 12 件服装
for i in range(12):
    items.append(Item(
        sku=f"B-{i+1:02d}", customer_id="B-服装批发",
        length_cm=50, width_cm=40, height_cm=35,
        weight_kg=18,
    ))
# 客户 C — 8 件家电 (重)
for i in range(8):
    items.append(Item(
        sku=f"C-{i+1:02d}", customer_id="C-家电厂",
        length_cm=100, width_cm=70, height_cm=80,
        weight_kg=80, load_bearing=50,
    ))

req = PackRequest(
    container=container,
    items=items,
    route=["A-电商客户", "B-服装批发", "C-家电厂"],
    enable_lifo=True,
)

result = solver.solve(req)
print(f"装入: {result.fitted_count} 件, 利用率: {result.volume_utilization*100:.1f}%")

# ─── 用 matplotlib 画 3D 视图 ───
fig = plt.figure(figsize=(14, 8))
ax = fig.add_subplot(111, projection='3d')

cL, cW, cH = container.length_cm, container.width_cm, container.height_cm

# 柜线框
corners = [
    [0, 0, 0], [cL, 0, 0], [cL, cW, 0], [0, cW, 0],
    [0, 0, cH], [cL, 0, cH], [cL, cW, cH], [0, cW, cH],
]
edges = [
    (0,1),(1,2),(2,3),(3,0),
    (4,5),(5,6),(6,7),(7,4),
    (0,4),(1,5),(2,6),(3,7),
]
for a, b in edges:
    ax.plot([corners[a][0], corners[b][0]],
            [corners[a][1], corners[b][1]],
            [corners[a][2], corners[b][2]], 'k-', linewidth=1.5)

# 门（绿色透明面 — x = cL）
door_verts = [
    [[cL, 0, 0], [cL, cW, 0], [cL, cW, cH], [cL, 0, cH]]
]
door = Poly3DCollection(door_verts, alpha=0.15, facecolor='#10b981', edgecolor='#10b981')
ax.add_collection3d(door)

# 按客户分色
palette = {
    'A-电商客户': '#ef4444',
    'B-服装批发': '#3b82f6',
    'C-家电厂': '#22c55e',
    None: '#94a3b8',
}

def draw_box(ax, x, y, z, dx, dy, dz, color):
    verts = [
        [[x, y, z], [x+dx, y, z], [x+dx, y+dy, z], [x, y+dy, z]],            # bottom
        [[x, y, z+dz], [x+dx, y, z+dz], [x+dx, y+dy, z+dz], [x, y+dy, z+dz]],# top
        [[x, y, z], [x+dx, y, z], [x+dx, y, z+dz], [x, y, z+dz]],            # front
        [[x, y+dy, z], [x+dx, y+dy, z], [x+dx, y+dy, z+dz], [x, y+dy, z+dz]],# back
        [[x, y, z], [x, y+dy, z], [x, y+dy, z+dz], [x, y, z+dz]],            # left
        [[x+dx, y, z], [x+dx, y+dy, z], [x+dx, y+dy, z+dz], [x+dx, y, z+dz]],# right
    ]
    poly = Poly3DCollection(verts, alpha=0.85, facecolor=color, edgecolor='black', linewidth=0.5)
    ax.add_collection3d(poly)

for p in result.placements:
    color = palette.get(p.customer_id, '#94a3b8')
    draw_box(ax, p.x_cm, p.y_cm, p.z_cm,
             p.length_cm, p.width_cm, p.height_cm, color)

# 设视角
ax.set_xlim(0, cL)
ax.set_ylim(0, cW)
ax.set_zlim(0, cH)
ax.set_box_aspect([cL, cW, cH])  # 真实比例
ax.set_xlabel('Length (cm)')
ax.set_ylabel('Width (cm)')
ax.set_zlabel('Height (cm)')
ax.view_init(elev=20, azim=-50)

# 标题 + 图例
title = (f"3D 配载预览 - {container.code}\n"
         f"{result.fitted_count} 件装入 | 利用率 {result.volume_utilization*100:.1f}% | "
         f"重量 {result.weight_used_kg}/{result.weight_max_kg} kg")
ax.set_title(title, fontsize=12)

# 图例
from matplotlib.patches import Patch
legend_elements = []
for cust, col in palette.items():
    if cust is None: continue
    cnt = sum(1 for p in result.placements if p.customer_id == cust)
    if cnt > 0:
        legend_elements.append(Patch(facecolor=col, edgecolor='black',
                                      label=f"{cust} ({cnt}件)"))
legend_elements.append(Patch(facecolor='#10b981', alpha=0.3,
                              label='门(卸货方向)'))
ax.legend(handles=legend_elements, loc='upper left', fontsize=9)

plt.tight_layout()
out = '/tmp/stowage_3d_demo.png'
plt.savefig(out, dpi=140, bbox_inches='tight')
print(f"\n✓ 3D 视图已保存: {out}")
print(f"  ({result.fitted_count} 件货 / 3 客户 / LIFO 卸货顺序)")
