# Comparison Case：shipment_order_links 关联表（任务 S8）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S8 + 任务书 §3.1#5

## 1. 决策：方案 A（链接表）

### 选 A 的依据
- ACC 原系统 Online 拆/合单：`Online.Source = "Order/Stowage"` 字段允许同一订单产生多张 Online.bill
- 仓库主管手工合并多客户的 Online 到一个 Stowage，N:1 客观存在
- 新系统未来必然要复刻拆 / 合单（Sprint2 路线图已列）
- **customer_ref 字符串软关联**：
  - 无 UNIQUE 约束可加
  - 改 customer_ref 后历史关联断裂
  - 拆/合时无法表达多关联

### 不选 B 的理由
- `shipments.order_id uuid` 只能表达 1:1，硬塞 1:N 会让首单"赢"，第 2+ shipment 无法关联
- 后期补 N:1 还要再加一张表 → 不如一次到位

## 2. 实现

### migration 045
```sql
create table shipment_order_links (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  shipment_id uuid not null references shipments(id) on delete cascade,
  order_id uuid not null references orders(id) on delete cascade,
  link_type text not null default 'SUBMIT'
    check (link_type in ('SUBMIT', 'SPLIT', 'MERGE', 'REPLACE')),
  created_at timestamptz not null default now(),
  created_by text,
  unique (tenant_id, shipment_id, order_id)
);
```
- `link_type` 区分 4 种业务场景，事后可分析拆/合占比
- `unique(tenant_id, shipment_id, order_id)` 防重复
- `on delete cascade` 双向：shipment 或 order 物理删除时 link 自动清理
- RLS 沿用 `app_tenant_matches(tenant_id)`

### CustomerApiRepository.insertShipmentOrderLink
幂等：`ON CONFLICT (tenant_id, shipment_id, order_id) DO NOTHING`，
同一 shipment-order 重复提交不报错（适合 retry 场景）。

### CustomerApiService.submitOrder 接入
在 `insertShipment` 拿到 `shipmentId` 之后立即 `insertShipmentOrderLink(tenantId, shipmentId, orderId, "SUBMIT")`。
- 当前 1:1 主路径走 link_type=SUBMIT
- 后续 split / merge 实现时调用同方法传不同 link_type

## 3. 测试

`SubmitRateIntegrationTest.submitWritesShipmentOrderLink`：
- mock 后调 submitOrder
- 验证 `insertShipmentOrderLink(TENANT, shipmentId, orderId, "SUBMIT")` 被调一次

### 全工程
**179 测试全过**（+1 vs S7 结束时 178）。

## 4. 后续路线图（不在本任务范围）

| 场景 | 实现要点 | link_type |
|------|---------|-----------|
| 客户改派渠道 → 重新出运 | 旧 shipment 状态 → CLOSED；新 shipment 建 REPLACE link | REPLACE |
| 仓库分包发货 | 一 order → 多 shipment | SPLIT |
| 多客户合并发运 | 多 order → 一 shipment | MERGE |

业务侧实现这些功能时只需调 `insertShipmentOrderLink` 传对应 link_type，
不必再改 schema。

## 5. ACC 对照

| ACC | 新系统 |
|-----|--------|
| `Online.Source = "Order/{N}"` 字符串拼装 | `shipment_order_links` 强关联 |
| 拆单：手工在 Online 复制行 + 改 Source | INSERT 多条 SPLIT link |
| 合单：手工 SELECT 多 Online 到 Stowage | INSERT 多条 MERGE link |
| 改派：手动改 Order.Express_ID | INSERT REPLACE link，旧链路保留 |
| 失效订单关联：物理删除 | `ON DELETE CASCADE` 自动 |
