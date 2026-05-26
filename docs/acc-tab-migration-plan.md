# ACC 80 tab 前后端对齐迁移计划

生成日期：2026-05-21
来源：`apps/web/src/App.vue` 的 `accTabs` 数组（78 个 tab）+ 现有数据库表盘点。

## 0. 当前进度

✅ 已实现端到端（共 **50** 个 tab）：
- 字典/主数据（早期）：`customers` / `channels` / `currencies`
- P1 业务核心：`orders` / `shipments`（含 `{id}/items`）/ `bills`（含 `{id}/items`）/ `payments` / `receiveds` / `charges` / `costs` / `profits`（含 `/summary` 聚合）/ `packages`
- P2 主数据：`suppliers` / `remotes` / `fuels` / `fee-types` / `acc-branches`（路由 `/api/acc/branches`）/ `departments`
- P2 主数据字典（022 批次）：`countries` / `postcodes` / `hscodes` / `bank-names` / `districts` / `customer-groups` / `warehouses` / `returns`
- P2 异常流 + 财务扩展（023 批次）：`collects` / `detains` / `asks` / `reparations` / `void-orders` / `fees` / `customer-fines` / `supplier-fines`
- P2 财务流水 + 字典（024 批次）：`customer-adjusts` / `supplier-adjusts` / `customer-refunds` / `supplier-refunds` / `customer-rebates` / `supplier-rebates` / `expense-categories` / `fee-item-types`
- P2 资金管理（025 批次）：`expenses` / `banks`（复用 financial_accounts）/ `transfers` / `dividends` / `borrowings` / `cycles` / `assets` / `received-sms`
✅ 路径基础对齐：`/health` → `/api/health`、`/api/sys/*` → `/api/admin/*`（仅 4 个）
✅ `/api/finance/dashboard`、`/api/finance/branches`、`/api/finance/dashboard/health` 已建
✅ **所有 49 个写型 tab 接入全 5 框架** + 1 个只读视图（void-orders）
✅ **复用类型基类模式**：acc_fines（c/s 2 种）+ acc_finance_txns（c/s × 3 type = 6 种）共 8 个 controller 共享 2 张表
✅ **跨表级联**：banks 删除前检查 expenses/transfers/dividends 关联，阻塞删除并显示具体计数
⏳ 剩余 **28 个 ACC tab** 待迁

## 1. 后端模板

新增任一 ACC tab 复用 `com.xqt.saas.acc.AccCustomersController` 这个模板，5 个端点：
- `GET /api/acc/<tab>` 列表分页（参数 page/pageSize/keyword/dateFrom/dateTo）
- `GET /api/acc/<tab>/{id}/raw` 原始详情
- `POST /api/acc/<tab>` 新建
- `PUT /api/acc/<tab>/{id}` 更新
- `DELETE /api/acc/<tab>/{id}` 删除

响应统一通过 `AccPaging.result(data, total)` 输出 `{data, total}`，匹配前端 `fetchAccData` 兼容分支。

## 2. tab 分类与状态

### 2.1 已建表，可直接对接（复用现有 schema）

| ACC tab | 后端表 | 备注 |
|---|---|---|
| customers ✅ | customers | 已实现（参考） |
| channels ✅ | channels | 已实现（参考） |
| currencies ✅ | finance_currency | 已实现（参考） |
| customer-groups | （建表） | 旧 `Customer_Group` 简单字典 |
| suppliers ✅ | partners (partner_type='SUPPLIER') | 已实现 |
| channel-accounts | （建表 channel_accounts） | 渠道账号 |
| products | services / rate_cards | 旧 `Product` 概念分拆 |
| countries | （建表 countries） | 国家字典 |
| zones | rate_card_lines.zone_code | 已有列，需聚合视图 |
| postcodes | （建表 postcodes） | 邮编 |
| remotes ✅ | remote_zones | 已实现 |
| fuels ✅ | fuel_surcharge_rates | 已实现 |
| hscodes | （建表 hs_codes） | HS 编码字典 |
| bank-names | （建表 bank_names） | 银行字典 |
| acc-branches ✅ | organizations | 已实现（路由 /api/acc/branches） |
| departments ✅ | organizations (org_type='department') | 已实现 |
| fee-types ✅ | charge_items | 已实现 |
| expense-categories | （建表） | 费用类别 |
| stowages | stowages | ✅ 已建（P3-1） |
| stowage-categories | stowage_categories | ✅ 已建（P3-1） |
| ports | stowage_ports | ✅ 已建（P3-1） |

### 2.2 业务核心，需建表 + 写完整业务逻辑

| ACC tab | 旧 PHP 类 | 估时（人天） | 优先级 |
|---|---|---|---|
| orders ✅ | Express.php (2453 行) | 5 | P1 完成 |
| shipments ✅ | 同 orders | 含在 orders | P1 完成 |
| packages ✅ | Online.php (4818 行) | 3 | P1 完成 |
| bills ✅ | CBill.php (86KB) | 5 | P1 完成 |
| payments ✅ | Bank.php | 2 | P1 完成 |
| receiveds ✅ | Collect.php | 2 | P1 完成 |
| charges ✅ | Charge.php (78KB) | 5 | P1 完成 |
| costs ✅ | Cost.php (88KB) | 5 | P1 完成 |
| profits ✅ | （视图聚合） | 2 | P1 完成（含 `/summary` 聚合） |
| commissions | Commission.php | 3 | P2 |
| collects | Collect.php | 2 | P2 |
| returns | Back.php | 2 | P2 |
| detains | Detain.php | 2 | P2 |
| asks | Ask.php | 2 | P2 |
| reparations | （新建） | 3 | P2 |
| transits | （新建 transits） | 2 | P2 |
| warehouses | warehouses | 复用 | P2 |
| dispatches | Dispatch.php | 3 | P2 |
| forecasts | （新建） | 2 | P3 |
| tracks | tracking_events | 复用 | P2 |
| stowage-steps | （新建） | 2 | P3 |
| void-orders | （视图） | 1 | P3 |
| quick-orders | （视图） | 1 | P3 |
| fees | Fee.php | 2 | P2 |
| customer-fines / supplier-fines | CFine.php | 2 | P3 |
| customer-adjusts / supplier-adjusts | CAdjust.php | 2 | P3 |
| customer-refunds / supplier-refunds | CRefund.php | 2 | P3 |
| customer-rebates / supplier-rebates | （新建） | 2 | P3 |
| expenses | Expenses.php (77KB) | 3 | P2 |
| banks | Bank.php (39KB) | 2 | P2 |
| transfers | （新建） | 1 | P3 |
| dividends | Dividend.php | 2 | P3 |
| borrowings | Borrowing.php (97KB) | 4 | P3 |
| assets | Assets.php (94KB) | 4 | P3 |
| cycles | Cycle.php | 2 | P3 |
| received-sms | （新建） | 1 | P3 |
| fee-item-types | （新建） | 1 | P3 |
| product-items | （新建） | 1 | P3 |
| potentials | （新建） | 2 | P3 |
| sold-tos | addresses | 复用 | P2 |
| notices | ClientNotice.php | 2 | P3 |
| employees | Employee.php (62KB) | 4 | P3 |
| wages | （新建） | 3 | P3 |
| attendances | Attence.php | 3 | P3 |
| socials / social-persons | （新建） | 3 | P3 |
| funds / fund-persons | （新建） | 3 | P3 |
| commission-rules | Commission.php | 2 | P3 |
| districts | District.php | 1 | P3 |
| logistics-interfaces | （新建） | 2 | P3 |
| tasks | （新建） | 2 | P3 |
| templates | （新建） | 1 | P3 |

**估算总和**：P1 ≈ 27 人天、P2 ≈ 30 人天、P3 ≈ 40 人天 = **～100 人天**。

## 3. 推荐执行顺序

按业务依赖 + 价值密度：

1. **P0 已完成**：customers / channels / currencies（模板就位）
2. **P1 业务核心**（27 天）：orders → shipments → packages → charges → costs → bills → payments → receiveds → profits
3. **P2 业务扩展**（30 天）：collects/returns/detains/asks 等异常流 + suppliers/sold-tos 等主数据 + dispatches/transits 等仓库
4. **P3 周边**（40 天）：人事 / 银行 / 资产 / 借贷 / 罚款 / 调账 / 退款 / 返利 等

## 4. 每个 tab 的复制模板（约 1.5 小时/个）

按 `AccCustomersController` 复制：

1. 确认或新建 DB 表（如果是新表，写一个 `db/migrations/0XX_acc_<tab>.sql`）
2. 复制 `AccCustomersController.java` → `Acc<Tab>Controller.java`
3. 改 `@RequestMapping("/api/acc/<tab>")`
4. 改 SQL（表名、列名、where 条件、search 字段）
5. 改 `project()` 把 DB 列名映射到前端 `accColumns[<tab>]` 期望的列
6. （可选）批量审核 / 业务操作端点：`audit-biz`、`undo-biz`、`batch-audit`
7. 跑 `./mvnw -DskipTests compile` + 手动验一遍 list / raw / POST / PUT / DELETE

## 5. 前端配套调整

每加一个新 tab：
- 不需要改前端代码（accTabs 数组已写死全部 78 个）
- 如果列名对不上，前端会显示空 — 此时改 `accColumns[<tab>]` 或后端 `project()`
- 表单字段（accFormFields）某些 tab 没定义 → 不能用 CRUD UI；用作只读列表即可

## 6. 关键不对齐与决策记录

- 旧 ACC 的 `Customer` 表有 contact/mobile/balance/settlement/branch/group/salesman 等"业务运营"字段；新 `customers` 表只有 code/name/credit_limit/account_mode/default_currency。
  - **决策**：业务运营字段进 `customer_contacts` / `customer_settlement_profiles`（已建）或 `addresses`。`AccCustomersController.project()` 拼接成前端期望的扁平结构。
- 旧 `Currency` 表有 symbol/rate/decimal；新 `finance_currency` 没有汇率列（汇率在 `exchange_rates` 或 `finance_currency_exchange`）。
  - **决策**：`/api/acc/currencies` 的 list 端点 join 当日汇率读 rate。symbol 加列。
- 旧 ACC 用整数 ID，新平台多为 UUID。前端 `accColumns[*]` 都用 `key: "id"` 不关心类型，OK。
- 前端 `apiFetch` 不带 tenant header；后端 `BearerAuthFilter` 从 token 解出 tenant_id 写 `app.current_tenant_id`，所以 controller SQL 直接读 `current_setting('app.current_tenant_id')::uuid` 即可。
