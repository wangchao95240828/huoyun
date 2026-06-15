"""
3D 配载预览 PNG 渲染 — matplotlib + Noto Sans SC (中文支持).

不依赖求解器: 接 Java 后端传来的已落库的 plan + items, 直接画 PNG.
"""
from __future__ import annotations
import os
from io import BytesIO
from typing import Optional

import matplotlib
matplotlib.use('Agg')                  # 无 GUI 环境
import matplotlib.pyplot as plt
from matplotlib import font_manager
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from matplotlib.patches import Patch

# 嵌入中文字体 (启动时一次性注册)
_FONT_PATH = os.path.join(os.path.dirname(__file__), 'NotoSansSC-Regular.ttf')
_FONT_PROP = None
if os.path.exists(_FONT_PATH):
    font_manager.fontManager.addfont(_FONT_PATH)
    _FONT_PROP = font_manager.FontProperties(fname=_FONT_PATH)
    plt.rcParams['font.family'] = _FONT_PROP.get_name()
    plt.rcParams['axes.unicode_minus'] = False


# 客户分色: 10 色环绕
_PALETTE = [
    '#ef4444', '#3b82f6', '#22c55e', '#f59e0b', '#a855f7',
    '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6',
]


def render_plan(plan: dict, items: list[dict]) -> bytes:
    """渲染配载方案为 PNG bytes.

    plan 必填字段: container_code, container_length_cm, container_width_cm,
                   container_height_cm, container_max_weight_kg,
                   fitted_count, volume_utilization, weight_used_kg
    items 每行: sku, customer_id (str or None), x_cm, y_cm, z_cm,
                placed_length_cm, placed_width_cm, placed_height_cm, placed (bool)
    """
    cL = float(plan.get('container_length_cm', 1200))
    cW = float(plan.get('container_width_cm', 230))
    cH = float(plan.get('container_height_cm', 260))

    fig = plt.figure(figsize=(14, 8))
    ax = fig.add_subplot(111, projection='3d')

    # 1) 柜线框
    _draw_wire_box(ax, 0, 0, 0, cL, cW, cH, 'black', linewidth=1.6)

    # 2) 门 (x=cL 处的绿色透明面, 标识卸货方向)
    door = [[[cL, 0, 0], [cL, cW, 0], [cL, cW, cH], [cL, 0, cH]]]
    ax.add_collection3d(Poly3DCollection(
        door, alpha=0.20, facecolor='#10b981', edgecolor='#10b981'))

    # 3) 客户分色
    cust_color: dict[str, str] = {}
    cust_counts: dict[str, int] = {}
    for it in items:
        if not it.get('placed'): continue
        cid = it.get('customer_id') or '(无客户)'
        if cid not in cust_color:
            cust_color[cid] = _PALETTE[len(cust_color) % len(_PALETTE)]
        cust_counts[cid] = cust_counts.get(cid, 0) + 1

    # 4) 画货物
    for it in items:
        if not it.get('placed'): continue
        x = float(it.get('x_cm', 0))
        y = float(it.get('y_cm', 0))
        z = float(it.get('z_cm', 0))
        l = float(it.get('placed_length_cm', 30))
        w = float(it.get('placed_width_cm', 30))
        h = float(it.get('placed_height_cm', 30))
        cid = it.get('customer_id') or '(无客户)'
        color = cust_color.get(cid, '#94a3b8')
        _draw_solid_box(ax, x, y, z, l, w, h, color, alpha=0.85)

    # 5) 设视角和坐标
    ax.set_xlim(0, cL)
    ax.set_ylim(0, cW)
    ax.set_zlim(0, cH)
    try:
        ax.set_box_aspect([cL, cW, cH])
    except Exception:
        pass
    ax.set_xlabel('长 Length (cm)')
    ax.set_ylabel('宽 Width (cm)')
    ax.set_zlabel('高 Height (cm)')
    ax.view_init(elev=20, azim=-55)

    # 6) 标题
    util = float(plan.get('volume_utilization', 0)) * 100
    wt_used = plan.get('weight_used_kg', 0)
    wt_max = plan.get('container_max_weight_kg', 0)
    title = (f"3D 配载预览 — {plan.get('container_code', '')}\n"
             f"{plan.get('fitted_count', 0)} 件装入 | 利用率 {util:.1f}% | "
             f"重量 {wt_used}/{wt_max} kg")
    ax.set_title(title, fontsize=11)

    # 7) 图例
    legend = []
    for cid, color in cust_color.items():
        legend.append(Patch(
            facecolor=color, edgecolor='black',
            label=f"{cid} ({cust_counts[cid]} 件)"))
    legend.append(Patch(
        facecolor='#10b981', alpha=0.35,
        label='门 (卸货方向, LIFO 先卸客户在此侧)'))
    ax.legend(handles=legend, loc='upper left', fontsize=9, framealpha=0.9)

    plt.tight_layout()
    buf = BytesIO()
    plt.savefig(buf, format='png', dpi=130, bbox_inches='tight')
    plt.close(fig)
    return buf.getvalue()


def _draw_wire_box(ax, x, y, z, dx, dy, dz, color, linewidth=1.5):
    corners = [
        [x, y, z], [x+dx, y, z], [x+dx, y+dy, z], [x, y+dy, z],
        [x, y, z+dz], [x+dx, y, z+dz], [x+dx, y+dy, z+dz], [x, y+dy, z+dz],
    ]
    edges = [(0,1),(1,2),(2,3),(3,0),(4,5),(5,6),(6,7),(7,4),(0,4),(1,5),(2,6),(3,7)]
    for a, b in edges:
        ax.plot([corners[a][0], corners[b][0]],
                [corners[a][1], corners[b][1]],
                [corners[a][2], corners[b][2]],
                color=color, linewidth=linewidth)


def _draw_solid_box(ax, x, y, z, dx, dy, dz, color, alpha=0.85):
    verts = [
        [[x, y, z], [x+dx, y, z], [x+dx, y+dy, z], [x, y+dy, z]],
        [[x, y, z+dz], [x+dx, y, z+dz], [x+dx, y+dy, z+dz], [x, y+dy, z+dz]],
        [[x, y, z], [x+dx, y, z], [x+dx, y, z+dz], [x, y, z+dz]],
        [[x, y+dy, z], [x+dx, y+dy, z], [x+dx, y+dy, z+dz], [x, y+dy, z+dz]],
        [[x, y, z], [x, y+dy, z], [x, y+dy, z+dz], [x, y, z+dz]],
        [[x+dx, y, z], [x+dx, y+dy, z], [x+dx, y+dy, z+dz], [x+dx, y, z+dz]],
    ]
    ax.add_collection3d(Poly3DCollection(
        verts, alpha=alpha, facecolor=color, edgecolor='black', linewidth=0.4))
