"""
3D 配载求解器 — 包 py3dbp + LIFO 多客户卸货顺序 + 重心计算。

py3dbp 用 First-Fit-Decreasing + 6 旋转。我们的包装策略：
1. LIFO 排序：route 倒序 → 先卸客户排最后（最外侧 = 最先取出）
2. 同客户内：按体积/重量降序（大件先装，避免后期塞不进）
3. 方向锁：this_side_up=True 时关闭 6 旋转的 z 轴方向
4. 重量验证：装柜后扫描坐标，找重心 + 4 象限分布
"""
from __future__ import annotations
import logging
from py3dbp_jerry import Packer, Bin, Item as P3Item

from schemas import (
    PackRequest, PackResult, Placement, Item as ApiItem,
)

logger = logging.getLogger(__name__)


def solve(req: PackRequest) -> PackResult:
    # 1) LIFO 排序：先卸的客户 → 排最后（让他们的箱子靠门）
    items_sorted = _sort_for_lifo(req.items, req.route, req.enable_lifo)

    # 2) 建 py3dbp Bin + Items
    bin_ = Bin(
        partno=req.container.code,
        WHD=(req.container.length_cm, req.container.width_cm, req.container.height_cm),
        max_weight=req.container.max_weight_kg,
        corner=0, put_type=1,
    )
    packer = Packer()
    packer.addBin(bin_)
    for it in items_sorted:
        # py3dbp Item: name, WHD, weight, level=0, loadbear=0, updown=True, color
        # updown=True 允许 z 轴方向翻转；True_side_up=True 时关闭
        p3 = P3Item(
            partno=it.sku,
            name=it.sku,
            typeof='cube',
            WHD=(it.length_cm, it.width_cm, it.height_cm),
            weight=it.weight_kg,
            level=it.customer_priority,
            loadbear=it.load_bearing,
            updown=not it.this_side_up,  # True = 可六面摆放；this_side_up=True 时 = False
            color='red' if it.fragile else 'gray',
        )
        # 携带 customer_id 用于结果输出
        p3._customer_id = it.customer_id  # type: ignore[attr-defined]
        packer.addItem(p3)

    # 3) 求解：bigger_first=True 让大件先放，distribute_items=False 单柜
    packer.pack(
        bigger_first=True,
        distribute_items=False,
        fix_point=True,           # 启用真实重力贴底
        check_stable=True,        # 要求 ≥3/4 顶点有底支撑
        support_surface_ratio=0.75,
        binding=[],
        number_of_decimals=req.number_of_decimals,
    )

    # 4) 收 placements + 算重心 / 象限
    placements: list[Placement] = []
    weight_used = 0.0
    cog_x = cog_y = cog_z = 0.0
    quadrant_w = [0.0, 0.0, 0.0, 0.0]
    half_l = req.container.length_cm / 2
    half_w = req.container.width_cm / 2
    for it in bin_.items:
        pos = it.position
        w, h, d = _rotated_whd(it)
        cx = float(pos[0]) + w / 2
        cy = float(pos[1]) + h / 2
        cz = float(pos[2]) + d / 2
        weight = float(it.weight)
        weight_used += weight
        cog_x += cx * weight
        cog_y += cy * weight
        cog_z += cz * weight
        # 4 象限：以柜底中心切十字
        if cx < half_l and cy < half_w:
            quadrant_w[0] += weight  # Q1: 左前
        elif cx >= half_l and cy < half_w:
            quadrant_w[1] += weight  # Q2: 右前
        elif cx < half_l and cy >= half_w:
            quadrant_w[2] += weight  # Q3: 左后
        else:
            quadrant_w[3] += weight  # Q4: 右后
        placements.append(Placement(
            sku=it.partno,
            customer_id=getattr(it, '_customer_id', None),
            x_cm=float(pos[0]),
            y_cm=float(pos[1]),
            z_cm=float(pos[2]),
            length_cm=w, width_cm=h, height_cm=d,
            rotation_type=it.rotation_type,
            weight_kg=weight,
        ))

    if weight_used > 0:
        cog_x /= weight_used
        cog_y /= weight_used
        cog_z /= weight_used
        total_w = sum(quadrant_w) or 1.0
        gravity_quadrants = [w / total_w for w in quadrant_w]
    else:
        gravity_quadrants = [0.0, 0.0, 0.0, 0.0]

    # 5) 警告
    warnings: list[str] = []
    if gravity_quadrants:
        max_q = max(gravity_quadrants)
        min_q = min(gravity_quadrants)
        if max_q - min_q > 0.40:
            warnings.append(
                f"重心偏移过大: 4 象限重量分布 {[f'{q:.0%}' for q in gravity_quadrants]}，"
                "理想各 25%。建议手动调整重物位置。"
            )
    if bin_.unfitted_items:
        warnings.append(f"{len(bin_.unfitted_items)} 件未装入，建议加柜或拆单。")

    volume_total = req.container.length_cm * req.container.width_cm * req.container.height_cm
    volume_used = sum(
        p.length_cm * p.width_cm * p.height_cm for p in placements
    )

    return PackResult(
        container_code=req.container.code,
        fitted_count=len(placements),
        unfitted_count=len(bin_.unfitted_items),
        volume_utilization=round(volume_used / volume_total, 4) if volume_total else 0.0,
        weight_used_kg=round(weight_used, 2),
        weight_max_kg=req.container.max_weight_kg,
        gravity_center=(round(cog_x, 1), round(cog_y, 1), round(cog_z, 1)),
        gravity_quadrants=[round(q, 4) for q in gravity_quadrants],
        placements=placements,
        unfitted=[ui.partno for ui in bin_.unfitted_items],
        warnings=warnings,
    )


def _sort_for_lifo(items: list[ApiItem], route: list[str],
                    enable_lifo: bool) -> list[ApiItem]:
    """LIFO 排序: 先卸的客户排最后（最后装 = 最外侧 = 最先取出）。"""
    if not enable_lifo or not route:
        # 按体积降序（py3dbp bigger_first 已经做了，但保险起见）
        return sorted(items, key=lambda i: -(i.length_cm * i.width_cm * i.height_cm))

    # route = [客户A, 客户B, 客户C]，A 最先卸 → 应该最后装 → 排序值最大
    route_idx = {cid: idx for idx, cid in enumerate(route)}
    last_idx = len(route)  # 不在 route 的放最前

    def sort_key(it: ApiItem):
        # 先按 route 倒序：route 最后的客户 (idx 大) → 先装 → key 小
        cust_priority = -route_idx.get(it.customer_id or "", last_idx)
        # 同客户内按 customer_priority 升序（小的先卸 → 后装）
        vol = -(it.length_cm * it.width_cm * it.height_cm)
        return (cust_priority, it.customer_priority, vol)

    return sorted(items, key=sort_key)


def _rotated_whd(it) -> tuple[float, float, float]:
    """根据 rotation_type 返回实际 WHD."""
    rt = it.rotation_type
    w, h, d = float(it.width), float(it.height), float(it.depth)
    # py3dbp 6 个旋转: WHD/WDH/HWD/HDW/DWH/DHW
    if rt == 0: return w, h, d
    if rt == 1: return w, d, h
    if rt == 2: return h, w, d
    if rt == 3: return h, d, w
    if rt == 4: return d, w, h
    if rt == 5: return d, h, w
    return w, h, d
