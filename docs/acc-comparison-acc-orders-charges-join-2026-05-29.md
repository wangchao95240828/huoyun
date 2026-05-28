# Comparison Case：AccOrdersController sellCharge/costCharge/branch join（任务 S4）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S4 + 任务书 §3.1#6

## 1. 问题

`AccOrdersController.java:28` 注释写 "sellCharge/costCharge/branch 暂无业务字段聚合，先空值占位"，
但实际 list 端点 SQL **已经实现**：
- sellCharge = `SUM(charges WHERE side='AR' AND settlement_status<>'VOID')` LEFT JOIN by shipments.customer_ref
- costCharge = `SUM(charges WHERE side='AP' AND settlement_status<>'VOID')` 同上
- branch = `LEFT JOIN organizations org ON org.id = o.branch_id`

charges 表自 migration 030 起补齐 settlement_status 字段，列前提满足。仅 doc 注释、test 缺失。

## 2. 实现

### 注释订正
`AccOrdersController.java` 头部 javadoc 改为说明实际聚合逻辑（删除 "暂无业务字段聚合" 误导句）。

### 单元测试 `AccOrdersListProjectionTest`（+2）
- `listProjectsSellAndCostAndBranch`：mock jdbc 返回 sell_charge=280 / cost_charge=180 / branch_name=上海分公司，
  验证 project() 映射到前端字段 `sellCharge / costCharge / branch / product / trackNo / piece / chargeWeight`
- `listNullBranchProjectsEmptyString`：branch_name=null 时投影为空串而非 null

辅助：`stubAndCall` 用 `any(Object[].class)` 一把匹配可变参数，避免 Mockito 7-arg 显式 stub 噪音。

### 测试技术细节
- AccPaging.result 返回 key 是 `"data"` 而非 `"items"`——测试初版踩坑
- HashMap 而非 Map.of：jdbc 真实返回行含 null 值（audited_at / audit_name / track_no），Map.of 禁 null

## 3. 测试结果

全工程 **154 测试全过**（+2 vs S3 结束时 152）。

## 4. 关键设计

### customer_ref join 而非 order_id join
charges 通过 shipments.customer_ref 关联 orders.customer_ref，避免 orders ↔ shipments 多对一的强耦合。
ACC 原系统按 OrderNo 字符串关联，本系统沿用 customer_ref 作为业务关联键。

### settlement_status <> 'VOID'
作废费用（migration 030 trigger 自动设为 VOID）不计入 sellCharge / costCharge 聚合。
反审、删除、人工冲销都会触发 VOID 路径——保证报表口径与财务台账一致。

### branch via orders.branch_id
当前 orders.branch_id 默认 null（charges.branch_id 也独立维护）。
real-world: 由 SubmitOrderService 在审核入库时按 customer.default_branch_id 填入；
现有报表先按 orders.branch_id 取值，未填则前端显示空串。

## 5. 前端

`apps/frontend/src/App.vue` orders tab 列定义本来就有这 3 列，无需改动。
