# ACC 逻辑缺口 — 补充任务书（Claude 续作）

> 生成日期：2026-05-29
> 上一份：`docs/acc-logic-gap-claude-task-2026-05-28.md`
> 目标读者：Claude（接着上一份做未完成项）
> 当前分支：`feat/acc-full-migration-2026-05-26`
> 当前 HEAD：`5a217ae`（54 个 commit / 136 项后端测试全过）

---

## 0. 给 Claude 的第一句话

上一份任务书的 P0 七件事 + §3 列出的 §3.3#5 / §3.4#5 / §3.5 / §5.2 已经全部完成，并产出 4 篇 comparison case + 最终交付报告。**但任务书 §3.6 / §3.9 / §3.10#4 以及 §3.2#4 PRD sprint1 列的若干计费规则在最终报告里没有标完成**，本补充文档把这些遗漏项整理成可执行任务。

请不要把这些任务当成"已经做完的复测"——它们是真未做的，但因为不在 §8 七件事范围内，不在 §3 完成度自评里。每一项的"当前状态"小节都贴了代码定位和占位证据，**先确认 grep 结果再动手**。

---

## 1. 工作风格（沿用上一份）

1. 每完成一个任务，写一份 `docs/acc-comparison-<topic>-2026-05-29.md`，含端到端真实数据验证数字。
2. 改了 schema 一律新增 migration `041_*.sql` 起编号往后递增；不能改已有 migration。
3. service 层补的业务逻辑要有 mock 单测（Mockito）+ 真实数据库验证至少一条链路。
4. 改完 Java 后**必须** `./mvnw -q test` 全绿才能 commit。
5. 改完前端后**必须** `cd apps/web && npm run typecheck` 退出码 0 才能 commit。
6. commit message 沿用上一份风格：`feat/fix(scope): 标题（任务X 或 §X.Y#Z）`。
7. **不要**改 `docs/acc-logic-gap-claude-task-2026-05-28.md` 和 `docs/acc-migration-final-report-2026-05-28.md`，它们是已交付的快照。
8. **不要**改 `docker-compose.yml` 挂载范围（已被列入运维 runbook，不在你范围内）。
9. **不要**改 `application.yml` 默认 `strict-quote / strict-gateway`（同上）。

---

## 2. 任务清单（按优先级）

### P0 — 阻塞 ACC 等价的核心逻辑

- 任务 S1：多源轨迹聚合服务
- 任务 S2：Submit 失败的业务级补偿/冲正
- 任务 S3：RateEngine 补 6 条 PRD sprint1 规则

### P1 — 影响后台可见性 / 操作完整性

- 任务 S4：`AccOrdersController` 售卖费用/成本费用/分公司 join 落地
- 任务 S5：配载状态机自动联动
- 任务 S6：`fx_rate_snapshots` 覆盖全部金额动作

### P2 — 业务/数据权限层

- 任务 S7：员工 / 分公司 / 销售 数据可见性 RLS 扩展
- 任务 S8：`shipment_order_links` 设计与落地决策

### P3 — 真实 provider 接入预备

- 任务 S9：ZPL → PDF / 图片 → PDF 转换器

---

## 3. P0 任务详述

### 任务 S1：多源轨迹聚合服务（任务书 §3.6）

#### 当前状态

```22:35:apps/backend/src/main/java/com/xqt/saas/acc/AccTracksController.java
@RestController
@RequestMapping("/api/acc/tracks")
public class AccTracksController {
    // 字典/CRUD 风格的 list/create/update/delete，没有聚合逻辑
}
```

grep 验证：

```bash
rg "TrackingAggregat|aggregateTrack" apps/backend/src/main/java
# 无匹配
```

ACC 旧系统轨迹来源：`Express_Process / Transit_Process / Stowage_Process / Online_TrackNo / Express_TrackNo` 五张表的事件，统一对外呈现为一条时间线。

#### 目标

新增 `com.xqt.saas.tracking.TrackingAggregator`，对外提供：

- **客户视角**：`GET /api/customer-api/tracking/{trackingNo}/timeline`
  - 仅返回客户可见事件（status changed / 出库 / 揽收 / 派送 / 妥投 / 异常公开描述）
  - 不返回内部操作（人工备注、内部状态切换）
- **内部视角**：`GET /api/acc/shipments/{id}/timeline`
  - 全量事件，含操作人 / 来源表 / 内部备注

#### 实施步骤

1. 阅读现有源：
   - `tracking_events` 表（migration 001/006）
   - `acc_stowage_steps` / `transits` / `dispatches` / `forecasts` / `track_items`（migration 027）
   - 已有 `PublicTrackingController` 看现有 query 形态
2. 新建包 `com.xqt.saas.tracking`，含 `TrackingAggregator` service + `TrackingEvent` DTO（visibility = PUBLIC / INTERNAL）。
3. 实现 `aggregate(tenantId, trackingNo)`：UNION ALL 五个来源，统一字段映射，按 `event_time` 排序，标 `source`、`visibility`、`operator`、`statusCode`、`message`。
4. 客户端点放在 `customerapi` 包下，加签名鉴权；内部端点直接在 `AccShipmentsController` 加方法。
5. **不要**新建 tracks 表；用 UNION 视图或在 service 层 in-memory merge。如果性能不行再做物化视图（041 migration）。

#### 验收标准

- 真实数据库构造：一条 shipment 同时在 `tracking_events / stowage_steps / dispatches` 各写 1 条事件 → 客户端点返回 3 条按时间排序，内部端点返回 3 条 + operator。
- 客户端点不能泄漏 `operator` / 内部备注。
- 单测覆盖：3 源混合 + 排序 + visibility 过滤。
- 端到端 curl 验证写进 `docs/acc-comparison-tracking-aggregator-2026-05-29.md`。

---

### 任务 S2：Submit 失败的业务级补偿/冲正（任务书 §3.1#4）

#### 当前状态

```169:206:apps/backend/src/main/java/com/xqt/saas/customerapi/CustomerApiService.java
        try {
            ...
            quote = rateEngine.quote(principal.tenantId(), req);
            ...
            prepayAmount = quote == null ? estimatePrepayAmount(...) : quote.totalAmount();
        } catch (ApiException ex) { ... }
        ...
        if (prepayAmount.signum() > 0) {
            ...
            if (!repository.decrementBalance(balanceAccountId, prepayAmount)) {
                throw ApiException.badRequest("客户余额不足");
            }
        }
        ...
        // 取号 → 写 cartons / declarations / charges / shipment_order_links
        // 若中途任一步骤抛出，已经预扣的 balance 是否被回滚？
```

Spring `@Transactional` 默认会回滚 DB 写入，但**资金流水 ledger 已经写、carrier 已经发出真实请求时**——如果真实 provider（不是 Sandbox）已经下了号，DB rollback 后 provider 那一侧仍然存在挂着的子单号；如果是预扣减余额成功但 cartons insert 失败，rollback 会让余额回滚但 `balance_ledger` 那条 PREPAY 流水也跟着回滚——这没问题。

**真正的缺口**：

1. 渠道 provider 已成功取号 + DB 后续 insert 失败 → 需要把 provider 那边的子单号**主动作废**或记录 ORPHAN，否则会产生"幽灵单号"。
2. 取号失败 → 已经写的 `charges` AR/AP 行应在同一事务回滚（当前如果在 try/catch 外回滚可能漏）。
3. 没有 SubmitCompensationService 这一层，业务级补偿语义散落在 `CustomerApiService.submitOrder` 内部。

#### 目标

新增 `com.xqt.saas.customerapi.SubmitCompensationService`：

- 记录 submit 过程的"已发生副作用"：余额预扣 / charges 写入 / carrier 取号 / label 生成。
- 失败时按副作用倒序执行补偿：
  - carrier 已取号 → 调 `CarrierGateway.cancel(trackingNo)`（先 best-effort，失败记 ORPHAN）
  - label 已生成 → 删 `label_files`（DB rollback 会做）+ 删存储文件
  - DB rollback 由 `@Transactional` 完成

#### 实施步骤

1. 在 `CarrierGateway` 接口加 `cancel(tenantId, masterTrackingNo)` 默认实现 = no-op。
2. `SandboxCarrierGateway.cancel` 实现：本地 DB 标记 `acc_scale_records` 之外的 sandbox 状态；真实 adapter 后面照抄模板。
3. 新建 `SubmitCompensationContext`，记录 submit 步骤；`CustomerApiService.submitOrder` 顶层 try/catch 异常时调 `compensate(context)`。
4. 当 carrier 已取号且 DB 后续失败时，写一条 `acc_orphan_tracking_nos` 表（041 migration）+ 调 `CarrierGateway.cancel` best-effort。

#### 041 migration 草案

```sql
create table acc_orphan_tracking_nos (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  carrier_master_tracking_no text not null,
  provider_code text not null,
  reason text not null,
  raw_request jsonb,
  raw_response jsonb,
  created_at timestamptz not null default now(),
  cancel_attempted boolean not null default false,
  cancel_success boolean,
  cancel_response jsonb
);
create index idx_orphan_tracking_tenant on acc_orphan_tracking_nos(tenant_id, created_at desc);
```

#### 验收标准

- 单测：mock CarrierGateway 成功 + mock `repository.insertCarton` 抛 RuntimeException → assert `CarrierGateway.cancel` 被调用 + `acc_orphan_tracking_nos` 有 1 条。
- 端到端：构造一条会失败的请求，验证余额回滚 + ORPHAN 表有记录。
- comparison case：`docs/acc-comparison-submit-compensation-2026-05-29.md`。

---

### 任务 S3：RateEngine 补 6 条 PRD sprint1 规则（任务书 §3.2#4）

#### 当前状态

最终报告 §4.4 列出 RateEngine 已实现：客户专属价 / 组价 / 邮编 / 当日限额 / 货物限制 / 多段计费 / 偏远 / 佣金。

**未列出**的（来自 `docs/prd-sprint1-billing-engine.md`）：

1. **品名关键词附加费**：item name 含关键词触发 surcharge（如"电池"→ +XX 元/票）
2. **附加费取大不取累加**：多个 surcharge 命中时只取最大值
3. **按箱最低计费**：单箱 < 最低计费重量时按最低重量计费
4. **偏远三档**：normal / extended / out-of-area（当前只有 1 档 remote_rate_rules）
5. **KG / LB / CBM / PIECE 多计费单位混算**：渠道 A 按 KG，渠道 B 按 PIECE，要在同一引擎处理
6. **Dim Factor**：体积折重系数按渠道差异化（如 UPS 5000 vs FedEx 6000）

#### 实施步骤

1. 阅读 `acc/config/Freight.php::getFee` 找到这 6 条规则的原始实现。
2. 阅读 `docs/prd-sprint1-billing-engine.md` 看预期口径。
3. 041 migration 加表 / 字段：

```sql
-- 品名关键词附加费
create table rate_item_keyword_surcharges (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  channel_id uuid,
  keyword text not null,
  surcharge_amount numeric(12,4) not null,
  surcharge_unit text not null default 'PER_PIECE',
  priority int not null default 100,
  active boolean not null default true
);

-- 渠道 Dim Factor
alter table acc_channel_accounts add column if not exists dim_factor int default 5000;

-- 偏远三档
alter table remote_rate_rules add column if not exists tier text not null default 'normal';

-- 渠道计费单位
alter table acc_channels add column if not exists charge_unit text not null default 'KG';

-- 按箱最低计费
alter table rate_cards add column if not exists min_chargeable_weight numeric(10,3);
```

4. `RateEngine.quote()` 逐条实现：
   - 计算 chargeable weight = max(actual, volume / dim_factor)
   - 单箱 min_chargeable_weight 兜底
   - 关键词扫描 declaration items 命中 keyword
   - 多个 surcharge 收集后 `max()` 而非 `sum()`
   - 偏远查 `remote_rate_rules` 时按 tier 取费率
   - charge_unit 决定主基数（重量 / 件数 / 体积）

5. `RateEngineTests` 每条规则补 1-2 个单测。

#### 验收标准

- `RateEngineTests` 至少 20 项全过（当前 14 项）。
- 真实数据库构造典型样本：
  - 渠道 A（KG, dim=5000）+ 1 票 10kg/0.06m³ 实际计费重量 12kg
  - declaration 含"电池" → 触发 surcharge 50 + 触发 surcharge 30 → 命中规则后最终 +50 不是 +80
  - 偏远 tier=out-of-area → 费率 × 1.5
- comparison case：`docs/acc-comparison-rate-engine-full-2026-05-29.md`。

---

## 4. P1 任务详述

### 任务 S4：`AccOrdersController` 售卖费用/成本/分公司 join（任务书 §3.1#6）

#### 当前状态

```28:29:apps/backend/src/main/java/com/xqt/saas/acc/AccOrdersController.java
 * sellCharge/costCharge/branch 暂无业务字段聚合，先空值占位（待 charges/costs 落表后再 join）。
```

charges 表已经落齐（migration 030），但这个 join 没补。

#### 目标

`/api/acc/orders` list / detail 返回真实 sellCharge / costCharge / branch：
- sellCharge = SUM(charges where direction='AR' and shipment_id 关联)
- costCharge = SUM(charges where direction='AP' and shipment_id 关联)
- branch = shipments.branch_id → organizations.name

#### 实施步骤

1. 改 `AccOrdersController.list / detail` 的 SQL，LEFT JOIN charges 聚合两个方向 + LEFT JOIN organizations 拿 branch 名。
2. 删掉第 28 行那条 "暂无业务字段聚合" 注释。
3. 前端 `App.vue` orders tab 列定义本来就有这 3 列，确认显示正确即可。
4. 单测：构造 1 票 shipment 有 2 条 AR + 1 条 AP，验 sellCharge / costCharge 数值。

---

### 任务 S5：配载状态机自动联动（任务书 §3.9）

#### 当前状态

`AccStowagesController / AccStowageStepsController / AccTransitsController / AccDispatchesController / AccForecastsController` 都是 CRUD，没有自动状态推进。

#### 目标

实现 ACC `Stowage_Process` 级别的自动状态推进：

- 装箱单确认（stowage.status = CONFIRMED）→ 自动写 `tracking_events`（事件：DISPATCHED）+ 关联 shipments 状态推进
- 转运起运（transit.status = IN_TRANSIT）→ 写 tracking_events（事件：IN_TRANSIT）
- 派送出库（dispatch.status = OUT_FOR_DELIVERY）→ 写 tracking_events（事件：OUT_FOR_DELIVERY）
- 妥投（dispatch.status = DELIVERED）→ 写 tracking_events（事件：DELIVERED）+ shipments.status = DELIVERED + 触发利润结算事件

#### 实施步骤

1. 新建 `com.xqt.saas.stowage.StowageStateMachine`。
2. 在每个 controller 的状态变更端点（confirm / dispatch / deliver）调 state machine。
3. state machine 内部按状态查找关联 shipments，写 tracking_events + 更新 shipments.status。
4. 妥投时通过 `ApplicationEvent` 发 `ShipmentDeliveredEvent`，让 documentcharges 监听后做利润结算。
5. 单测覆盖每条状态迁移。

#### 验收标准

- 端到端：模拟 stowage confirm → 验证 tracking_events 多 1 条 DISPATCHED 事件 + shipments.status 推进。
- comparison case：`docs/acc-comparison-stowage-state-machine-2026-05-29.md`。

---

### 任务 S6：`fx_rate_snapshots` 覆盖全部金额动作（任务书 §3.4#6）

#### 当前状态

migration 030 创建了 `fx_rate_snapshots` 表，但是否覆盖以下 11 种金额动作没确认：

1. 预扣 PREPAY
2. 预扣释放 PREPAY_RELEASE
3. 收款 RECEIPT
4. 付款 PAYMENT
5. 退款 REFUND
6. 调账 ADJUST
7. 返利 REBATE
8. 罚款 FINE
9. 赔偿 REPARATION
10. 作废 VOID
11. 汇率差 FX_DIFF

#### 目标

每条 `balance_ledger` 写入时都自动捕获 fx 快照（when source_currency != target_currency）。

#### 实施步骤

1. 在 `DocumentChargeRepository.recordBalanceLedger`（资金流水唯一入口）顶上加 fx 捕获逻辑：
   - 查 `acc_currencies` 当前 `base_currency`
   - 如果 ledger.currency != base_currency → 查最新汇率 → 写 `fx_rate_snapshots` 一条
2. 单测 11 类 biz_type 各发一条 ledger，验证 fx_rate_snapshots 有对应记录。
3. comparison case：`docs/acc-comparison-fx-snapshot-coverage-2026-05-29.md`。

---

## 5. P2 任务详述

### 任务 S7：员工/分公司/销售 数据可见性 RLS（任务书 §3.10#4）

#### 当前状态

- migration 002 实现了 `tenant_id` 级 RLS，但是没有 user / branch / salesman 级别。
- 任何 tenant 内用户能看全 tenant 数据。
- ACC 旧系统按 `Employee.Type` + `Customer.salesman_id` + `Shipment.branch_id` 做行级隔离。

#### 目标

加一层 PostgreSQL RLS policy：

- 当前 user 是 `ROLE='SALESMAN'`：只能看 `customers.salesman_user_id = current_user`
- 当前 user 是 `ROLE='BRANCH_MANAGER'`：只能看 `shipments.branch_id = current_user.branch_id`
- 当前 user 是 `ROLE='ADMIN' / 'FINANCE'`：全 tenant 可见

#### 实施步骤

1. 041/042 migration：
```sql
alter table customers enable row level security;
create policy customers_sales_isolation on customers
  using (
    current_setting('app.user_role', true) in ('ADMIN', 'FINANCE')
    OR salesman_user_id::text = current_setting('app.user_id', true)
  );
-- 类似在 shipments / orders 加 branch 策略
```
2. 在 `AccTenantTxFilter` 设置完 `app.tenant_id` 之后，追加 `set local app.user_id = ?` 和 `set local app.user_role = ?`。
3. 单测：构造两个 salesman，互相看不到对方客户。
4. comparison case：`docs/acc-comparison-row-level-isolation-2026-05-29.md`。

⚠️ **谨慎**：这个改动可能让所有现有数据查询少返回行，需要先在 dev 测全量功能再 merge。

---

### 任务 S8：`shipment_order_links` 设计与决策（任务书 §3.1#5）

#### 当前状态

```bash
rg "shipment_order_links" apps/backend
# 无匹配
rg "shipment_order_links" db/migrations
# 无匹配
```

任务书要求 "Submit 后应确保 shipment_order_links 写入，不能只靠 customer_ref 软关联"，但这张表当前**完全不存在**。

#### 目标

两选一，先决策再实现：

**方案 A：建链接表**
- 041 migration 建 `shipment_order_links(shipment_id, order_id, link_type, created_at)`
- 适用于一对多 / 多对一关系（一票拆多单 / 多票合一单）

**方案 B：在 shipments 直接加 order_id 外键**
- 041 migration 加 `shipments.order_id uuid references orders(id)`
- 适用于一对一关系

#### 建议

先 grep `acc/api/PreOrder.php` / `Submit.php`，看 ACC 原始关系是 1:1 还是 1:N。如果 1:1 走方案 B，更简单；如果 1:N 走方案 A。

实现完后在 `CustomerApiService.submitOrder` 拿到 shipmentId 之后立刻 insert link / update foreign key。

#### 验收标准

- comparison case：`docs/acc-comparison-shipment-order-link-2026-05-29.md`，说明选了哪个方案 + 决策依据。

---

## 6. P3 任务详述

### 任务 S9：ZPL → PDF / 图片 → PDF 转换（任务书 §3.5#6）

#### 当前状态

```62:65:apps/backend/src/main/java/com/xqt/saas/labels/SandboxLabelGateway.java
            // 这里产一段 placeholder bytes 标识是 sandbox 出的非真实 PDF
            content = ("%SANDBOX_LABEL " + tracking + " " + ctx.shipmentNo() + "\n")
```

只是占位 bytes，没有真实 ZPL/图片转 PDF。真实 provider 接入时面单可能返回 ZPL 字符串 或 PNG/JPG bytes。

#### 目标

新增 `com.xqt.saas.labels.LabelFormatConverter`：

- `zplToPdf(zpl: String, widthMm: int, heightMm: int): byte[]`
- `imageToPdf(image: byte[], widthMm: int, heightMm: int): byte[]`

#### 实施步骤

1. 选库：
   - ZPL → PDF：调用 Labelary REST API（公共服务，可离线时降级）或本地 Java ZPL parser（如 `jZebra-ZPL`）
   - 图片 → PDF：用现有 PDFBox（已在 pom.xml）
2. 在 `LabelService.generate` 输出前判断 `artifact.fileExt`：
   - `.zpl` → 调 `LabelFormatConverter.zplToPdf` 转 PDF
   - `.png / .jpg` → 调 `LabelFormatConverter.imageToPdf` 转 PDF
3. 保留 source 字段标识原始格式，便于客户需要 ZPL 时回退。
4. 单测覆盖：构造一段已知 ZPL → 转出的 PDF 含期望文本（用 `PdfPageExtractor`）。
5. comparison case：`docs/acc-comparison-label-format-conversion-2026-05-29.md`。

---

## 7. 不做什么（重申）

| 不要做 | 原因 |
|---|---|
| 不要改 `docker-compose.yml` 挂载范围 | 运维 runbook 范围 |
| 不要改 `application.yml` 默认 `strict-quote` | 运维 runbook 范围 |
| 不要新建 `/api/iot/warehouse/parcel` | 业务决定暂缓 |
| 不要补 37 个主数据 tab 审核按钮 | 等运营策略决策 |
| 不要找真实 UPS/FedEx 凭证接 | 商务流程 |
| 不要改 `docs/acc-migration-final-report-2026-05-28.md` | 已交付快照 |

---

## 8. 端到端验收清单（最终）

完成全部 9 项任务后，本目录应新增：

```
docs/acc-comparison-tracking-aggregator-2026-05-29.md          (S1)
docs/acc-comparison-submit-compensation-2026-05-29.md          (S2)
docs/acc-comparison-rate-engine-full-2026-05-29.md             (S3)
docs/acc-comparison-stowage-state-machine-2026-05-29.md        (S5)
docs/acc-comparison-fx-snapshot-coverage-2026-05-29.md         (S6)
docs/acc-comparison-row-level-isolation-2026-05-29.md          (S7)
docs/acc-comparison-shipment-order-link-2026-05-29.md          (S8)
docs/acc-comparison-label-format-conversion-2026-05-29.md      (S9)
docs/acc-migration-final-report-supplement-2026-05-29.md       (整体补报)
```

migrations 新增（编号往后递增）：

```
041_acc_orphan_tracking_nos.sql                (S2)
042_rate_engine_full_rules.sql                 (S3)
043_row_level_isolation_policies.sql           (S7)
044_shipment_order_link.sql                    (S8)
```

后端测试目标：从 136 → **不少于 165 项**（每项任务至少 3 个新单测）。

`vue-tsc` 退出码必须 0。

---

## 9. 一句话目标

把"ACC 旧系统已无任何未迁移的核心业务能力"这句话从**自评**升级为**逐条对账可证伪**——本补充任务书 9 项做完后，再没有任务书 §3 / PRD sprint1 中明确点名的能力是"路径上未做"。

---

如有歧义请回到 ACC 旧系统 `acc/api/*.php` 和 `acc/config/Freight.php` 看原始行为，再决定新系统等价实现。
