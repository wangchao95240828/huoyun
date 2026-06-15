"""跑一个超 1 柜的场景: 40HC 装不下, 看自动分柜结果"""
from schemas import Container, Item
from multi_solver import MultiPackRequest, solve_multi


# 1 个 40HC 装不下的 200 件大重货
items = []
for i in range(50):
    items.append(Item(
        sku=f"A-{i+1:03d}", customer_id="A",
        length_cm=100, width_cm=80, height_cm=60, weight_kg=120,
    ))
for i in range(80):
    items.append(Item(
        sku=f"B-{i+1:03d}", customer_id="B",
        length_cm=80, width_cm=60, height_cm=50, weight_kg=80,
    ))
for i in range(70):
    items.append(Item(
        sku=f"C-{i+1:03d}", customer_id="C",
        length_cm=60, width_cm=50, height_cm=40, weight_kg=40,
    ))

# 可用柜：40HC + 20HC (混搭)
containers = [
    Container(code="40HC", length_cm=1200, width_cm=230, height_cm=260, max_weight_kg=26000),
    Container(code="20GP", length_cm=590, width_cm=230, height_cm=240, max_weight_kg=21500),
]

req = MultiPackRequest(
    containers=containers,
    container_max_count={"40HC": 5, "20GP": 5},
    items=items,
    route=["A", "B", "C"],
    enable_lifo=True,
    packing_factor=0.85,
    customer_cohesion_weight=1.0,
)

print(f"输入: {len(items)} 件货 (A:50/B:80/C:70)")
print(f"可用柜: 40HC × 5, 20GP × 5")
print(f"路线: A→B→C, LIFO 启用")
print(f"packing_factor: 0.85 (1D→3D 余量)")
print()

result = solve_multi(req)

print(f"=== 求解结果 ===")
print(f"CP-SAT 状态: {result.assignment_solver_status}")
print(f"目标值: {result.assignment_objective}")
print(f"用了 {result.total_containers_used} 个柜")
print()
for i, plan in enumerate(result.plans):
    print(f"  柜 {i+1}: {plan.container_code}")
    print(f"    装入 {plan.fitted_count} 件, 利用率 {plan.volume_utilization:.1%}, "
          f"重量 {plan.weight_used_kg}/{plan.weight_max_kg} kg")
    # 按客户统计
    by_cust = {}
    for p in plan.placements:
        c = p.customer_id or "_"
        by_cust[c] = by_cust.get(c, 0) + 1
    print(f"    客户分布: {by_cust}")

if result.unfitted_overall:
    print(f"\n⚠ 仍 {len(result.unfitted_overall)} 件无法装入: {result.unfitted_overall[:10]}...")

for w in result.warnings:
    print(f"⚠ {w}")
