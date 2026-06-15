"""跑一个真实场景：40HC 整柜 + 3 个客户共 30 件货 + LIFO"""
import json
import schemas
import solver
from main import _ascii_view


# 40HC 集装箱 (内径约 1203 x 235 x 269 cm)，载重 26t
container = schemas.Container(
    code="40HC-DEMO",
    length_cm=1200, width_cm=230, height_cm=260,
    max_weight_kg=26000,
)

# 3 个客户的货，路线 [A, B, C]：A 最先卸（IL）→ 应装在最外侧
items = []

# 客户 A: 5 件大箱 (电子产品)
for i in range(5):
    items.append(schemas.Item(
        sku=f"A-{i+1:02d}", customer_id="A",
        length_cm=80, width_cm=60, height_cm=50,
        weight_kg=40, this_side_up=True,
    ))

# 客户 B: 12 件中箱 (服装)
for i in range(12):
    items.append(schemas.Item(
        sku=f"B-{i+1:02d}", customer_id="B",
        length_cm=50, width_cm=40, height_cm=35,
        weight_kg=18, fragile=False,
    ))

# 客户 C: 8 件大重箱 (家电，最先装最内侧)
for i in range(8):
    items.append(schemas.Item(
        sku=f"C-{i+1:02d}", customer_id="C",
        length_cm=100, width_cm=70, height_cm=80,
        weight_kg=80, load_bearing=50,
    ))

# 加 5 件零散件 (无 customer)
for i in range(5):
    items.append(schemas.Item(
        sku=f"X-{i+1:02d}",
        length_cm=30, width_cm=30, height_cm=30,
        weight_kg=8,
    ))

req = schemas.PackRequest(
    container=container,
    items=items,
    route=["A", "B", "C"],   # A 先卸
    enable_lifo=True,
)

print(f"输入: 柜 {container.length_cm}×{container.width_cm}×{container.height_cm} cm / {container.max_weight_kg} kg max")
print(f"      {len(items)} 件货物 (A:5 B:12 C:8 X:5)")
print(f"      路线: A → B → C (LIFO: C 最先装、A 最先卸)")
print()

result = solver.solve(req)
print(_ascii_view(result, container))
print()
print("=== JSON 结构示例 (前 3 件) ===")
print(json.dumps([p.model_dump() for p in result.placements[:3]], indent=2, ensure_ascii=False))
