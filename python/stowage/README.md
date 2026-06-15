# 3D 配载求解微服务

xqt-saas 配载中心的 3D 装箱/拼柜求解器。基于 [jerry800416/3D-bin-packing](https://github.com/jerry800416/3D-bin-packing)（MIT，扩展了 enzoruiz/3dbinpacking 加入重心/方向/堆叠/可承重 4 类约束）。

## 接口

```
POST /pack          → JSON 结果（坐标 + 利用率 + 重心 + 警告）
POST /pack/ascii    → 文本俯视图 (运维肉眼看)
GET  /health
```

请求格式见 `schemas.py` 的 `PackRequest`：
- `container`: 柜内尺寸 + 载重上限
- `items`: 每件 sku/L×W×H/重量 + 4 类约束 (this_side_up / fragile / load_bearing / customer_priority)
- `route`: 客户卸货顺序（A→B→C, A 先卸）
- `enable_lifo`: 启用 LIFO 多客户卸货（先卸的装外侧）

## 本地跑

```bash
cd python/stowage
python -m venv .venv && source .venv/bin/activate
pip install -e .  # 或 pip install fastapi uvicorn pydantic numpy
python demo_test.py    # 跑示例: 40HC 装 30 件
# 服务模式:
uvicorn main:app --host=0.0.0.0 --port=8100
```

## 已实现的约束

✅ 尺寸 + 总重量硬约束
✅ 6 旋转方向（updown=False 时锁定 z 轴，对应 this_side_up）
✅ 堆叠稳定性（≥3/4 顶点底支撑，support_surface_ratio=0.75）
✅ 可承重 load_bearing 检查
✅ LIFO 多客户卸货顺序（route 倒序 + customer_priority 二级）
✅ 重心计算 + 4 象限分布警告（偏移 > 40% 警告）

## 集成路径

```
xqt-saas Java backend
  POST /api/acc/stowage/plan
       body = 配载方案 (柜 + items + route)
  ↓
  Spring Boot AccStowagePlanController
  ↓ HTTP
  Python FastAPI 服务（本目录）
  ↓
  jerry800416 求解器
  ↓
  返回 placements
  ↓
  落 stowage_plans + plan_items 表
  ↓
  前端 three.js 渲染 3D 透视
```

## TODO

- [ ] Java AccStowagePlanController
- [ ] DB schema: stowage_plans, stowage_plan_items
- [ ] 前端 three.js 3D 视图
- [ ] 拖拽手工微调 + 重求解
- [ ] 多柜分配（OR-Tools CP-SAT 配合）

## 依赖

- `jerry800416/3D-bin-packing` 主体（已 vendor 到 `py3dbp_jerry/`，避免 pip 装不到）
- fastapi, uvicorn, pydantic, numpy
- matplotlib 仅 plotBoxAndItems() 用，惰性导入
