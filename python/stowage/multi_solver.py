"""
多柜分配 — OR-Tools CP-SAT + 3D bin packing 两层求解。

策略：
  Step 1 (CP-SAT 1D 体积+重量约束)：决定每个 item 分到哪个柜
        - 目标: 最少柜数；同客户尽量同柜（cohesion）
        - 硬约束: 每柜累计体积 ≤ 柜容；每柜累计重量 ≤ max_weight
  Step 2 (jerry800416 3D)：对每个柜跑 LIFO 求解，输出具体摆放坐标

注意：CP-SAT 用的是 1D 累计（体积+重量），不保证 3D 几何可装入；
      所以 Step 1 留 15% 余量(packing_factor)，Step 2 才是真实 3D 验证。
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field, PositiveFloat
from ortools.sat.python import cp_model

from schemas import Container, Item, PackRequest, PackResult
import solver


class MultiPackRequest(BaseModel):
    """多柜分配请求 — 给定货物 + N 种柜规格(可选数量)，自动决定用几个柜 + 哪个柜放哪个货。"""
    containers: list[Container] = Field(
        min_length=1,
        description="可用柜规格 (每种规格一个对象，code 唯一区分)"
    )
    container_max_count: dict[str, int] = Field(
        default_factory=dict,
        description="每种柜 code 最多用多少个，留空 = 不限"
    )
    items: list[Item] = Field(min_length=1)
    route: list[str] = Field(default_factory=list)
    enable_lifo: bool = Field(default=True)
    packing_factor: float = Field(
        default=0.85,
        ge=0.5, le=1.0,
        description="1D 累计 → 3D 实际利用率折扣 (默认 85%)"
    )
    customer_cohesion_weight: float = Field(
        default=1.0,
        description="同客户尽量同柜的惩罚权重；0 = 不要求"
    )


class MultiPackResult(BaseModel):
    total_containers_used: int
    plans: list[PackResult]                      # 每个柜一个方案
    assignment_solver_status: str                 # 'OPTIMAL' / 'FEASIBLE' / 'INFEASIBLE'
    assignment_objective: float                   # CP-SAT 目标值
    unfitted_overall: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


def solve_multi(req: MultiPackRequest) -> MultiPackResult:
    # 展开可用柜实例 (Container, container_index)
    container_instances: list[tuple[Container, int]] = []
    for ct in req.containers:
        max_n = req.container_max_count.get(ct.code, 99)
        # 上限给个合理数字：货量 / 单柜体积 + 2 个保险柜
        n_estimate = _estimate_containers_needed(req.items, ct)
        max_n = min(max_n, n_estimate + 2)
        for _ in range(max_n):
            container_instances.append((ct, len(container_instances)))

    if not container_instances:
        raise ValueError("没有可用柜")

    # ─── Step 1: CP-SAT 分柜 ───
    model = cp_model.CpModel()
    n_items = len(req.items)
    n_bins = len(container_instances)

    # x[i][b] = 1 if item i 放柜 b
    x = {}
    for i in range(n_items):
        for b in range(n_bins):
            x[(i, b)] = model.NewBoolVar(f"x_{i}_{b}")

    # used[b] = 1 if 柜 b 至少有 1 件货
    used = [model.NewBoolVar(f"used_{b}") for b in range(n_bins)]

    # 每件必须装一个柜
    for i in range(n_items):
        model.Add(sum(x[(i, b)] for b in range(n_bins)) == 1)

    # used[b] = OR(x[i][b] for all i)
    for b in range(n_bins):
        model.AddMaxEquality(used[b], [x[(i, b)] for i in range(n_items)])

    # 容量约束 (体积 mm³ 整数化避免浮点)
    scale = 1
    for b, (ct, _) in enumerate(container_instances):
        cap_vol = int(ct.length_cm * ct.width_cm * ct.height_cm * req.packing_factor * scale)
        cap_wgt = int(ct.max_weight_kg * 1000)  # kg → g
        # 累计体积
        model.Add(
            sum(int(it.length_cm * it.width_cm * it.height_cm * scale) * x[(i, b)]
                for i, it in enumerate(req.items)) <= cap_vol
        )
        # 累计重量
        model.Add(
            sum(int(it.weight_kg * 1000) * x[(i, b)]
                for i, it in enumerate(req.items)) <= cap_wgt
        )

    # 软约束: 同客户尽量同柜 (按客户分组)
    cust_groups: dict[str, list[int]] = {}
    for i, it in enumerate(req.items):
        cid = it.customer_id or "_NOCUST"
        cust_groups.setdefault(cid, []).append(i)

    # 同客户跨柜数 = 该客户的 used_bin 数 - 1
    cohesion_penalty = []
    if req.customer_cohesion_weight > 0:
        for cid, idxs in cust_groups.items():
            if len(idxs) <= 1: continue
            for b in range(n_bins):
                has_cust = model.NewBoolVar(f"has_{cid}_{b}")
                model.AddMaxEquality(has_cust, [x[(i, b)] for i in idxs])
                cohesion_penalty.append(has_cust)

    # 目标: 用柜数最少 + 客户分散罚
    objective_terms = [used[b] * 100 for b in range(n_bins)]
    if cohesion_penalty:
        cohesion_w = int(req.customer_cohesion_weight * 10)
        objective_terms += [p * cohesion_w for p in cohesion_penalty]
    model.Minimize(sum(objective_terms))

    # 求解
    cp_solver = cp_model.CpSolver()
    cp_solver.parameters.max_time_in_seconds = 30.0
    status = cp_solver.Solve(model)
    status_str = cp_solver.StatusName(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        # 分柜失败：可能体积/重量超柜
        warnings = [f"CP-SAT 分柜失败: {status_str}，请检查货物是否超过任一可用柜的容量"]
        return MultiPackResult(
            total_containers_used=0,
            plans=[],
            assignment_solver_status=status_str,
            assignment_objective=0.0,
            unfitted_overall=[it.sku for it in req.items],
            warnings=warnings,
        )

    # ─── Step 2: 每个 used 柜调 jerry800416 做 3D ───
    plans: list[PackResult] = []
    unfitted_overall: list[str] = []
    warnings: list[str] = []
    for b in range(n_bins):
        if cp_solver.Value(used[b]) == 0: continue
        ct, _ = container_instances[b]
        bin_items = [req.items[i] for i in range(n_items)
                     if cp_solver.Value(x[(i, b)]) == 1]
        # 为这柜起独立 code，避免多个同型号混淆
        used_count = sum(1 for p in plans if p.container_code.startswith(ct.code))
        bin_code = f"{ct.code}#{used_count + 1}"
        bin_container = Container(
            code=bin_code,
            length_cm=ct.length_cm, width_cm=ct.width_cm, height_cm=ct.height_cm,
            max_weight_kg=ct.max_weight_kg,
        )
        sub_req = PackRequest(
            container=bin_container,
            items=bin_items,
            route=req.route,
            enable_lifo=req.enable_lifo,
        )
        sub_result = solver.solve(sub_req)
        plans.append(sub_result)
        unfitted_overall.extend(sub_result.unfitted)

    if unfitted_overall:
        warnings.append(
            f"3D 校验后仍 {len(unfitted_overall)} 件无法装入；"
            f"提示: 调低 packing_factor (当前 {req.packing_factor}) 或加柜"
        )

    return MultiPackResult(
        total_containers_used=len(plans),
        plans=plans,
        assignment_solver_status=status_str,
        assignment_objective=cp_solver.ObjectiveValue(),
        unfitted_overall=unfitted_overall,
        warnings=warnings,
    )


def _estimate_containers_needed(items: list[Item], ct: Container) -> int:
    """粗估需要几个柜 (用于设 CP-SAT 上限，省内存)。"""
    total_vol = sum(i.length_cm * i.width_cm * i.height_cm for i in items)
    total_wgt = sum(i.weight_kg for i in items)
    cap_vol = ct.length_cm * ct.width_cm * ct.height_cm * 0.85
    cap_wgt = ct.max_weight_kg
    n_vol = int(total_vol / cap_vol) + 1
    n_wgt = int(total_wgt / cap_wgt) + 1
    return max(n_vol, n_wgt, 1)
