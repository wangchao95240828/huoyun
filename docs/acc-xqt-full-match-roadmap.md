# ACC 与新智慧完全匹配实施矩阵

生成日期：2026-05-07  
适用范围：新航线统一平台从零开发两套客户流程，并以 ACC、新智慧作为功能、字段、状态、金额口径和页面交互对照来源。

## 1. 结论

可以做到“完全匹配”，但这里的完全匹配不是复制旧系统数据库或长期调用旧系统接口，而是做到：

1. 旧系统关键页面在新系统有对应页面或工作台。
2. 旧系统关键 POST/GET 请求在新系统有对应业务 API。
3. 请求 body 字段、默认值、枚举、筛选语义能解释映射。
4. 列表字段、详情字段、状态、金额、权限范围能和旧系统样本对照。
5. 写操作结果能在新系统主库、审计日志、财务链路中追溯。
6. 每个 P0 模块都有 comparison case，记录旧系统样本、新系统结果、差异和验收结论。

因此，目标不是“旧系统一比一表结构克隆”，而是“业务表现和结果可对照一致，新系统内部模型更规范”。

## 2. 匹配等级

| 等级 | 名称 | 判定标准 |
| --- | --- | --- |
| L0 | 未开始 | 只有需求，没有旧系统字段/body，也没有新接口 |
| L1 | 资料已采集 | 已有 ACC 源码反推或新智慧抓包/body 文档 |
| L2 | API 已定义 | 已在 `docs/api-development-guide.md` 写出新接口、body、字段说明 |
| L3 | 接口已实现 | Spring Boot 已有可运行接口，并能读写主库 |
| L4 | 字段已对齐 | 旧字段到新字段、枚举、默认值、筛选语义已建立映射 |
| L5 | 样本已通过 | 至少 3-5 条旧系统样本与新系统结果通过人工/接口对照 |
| L6 | 可替代 | 页面、权限、审计、财务追溯、异常边界均通过 UAT，可以替代旧系统使用 |

## 3. 两套流程总映射

| 旧系统来源 | 新系统客户方向 | 新系统服务模式 | 主接口前缀 | 当前结论 |
| --- | --- | --- | --- | --- |
| 新智慧 XQT | `SELLER_CUSTOMER` | `SELLER_FULFILLMENT` | `/api/seller/*`, `/api/warehouse/*`, `/api/finance/*` | 方向匹配，财务 body 已采集，Spring Boot 业务实现待补齐 |
| ACC | `DOCUMENT_CUSTOMER` | `DOCUMENT_SHIPPING` | `/api/document/*`, `/api/customer-api/*`, `/api/labels/*`, `/api/tracking/*` | 方向匹配，源码反推已完成，制单核心能力待补齐 |

## 4. 当前真实代码状态

| 模块 | 当前 Spring Boot 实现 | 匹配等级 | 说明 |
| --- | --- | --- | --- |
| 登录认证 | `/api/auth/login`, `/api/auth/me`, `/api/auth/logout` | L3 | 已可运行，后续补密码策略、菜单路由、登录日志展示 |
| 用户/角色/权限 | `/api/admin/users`, `/api/admin/roles`, `/api/admin/permissions`, `/api/admin/audit-logs` | L3 | 基础后台能力已实现 |
| 业务流程 | `GET /api/business-flows` | L3 | 已返回卖货流程和制单流程配置 |
| 卖货订单 | `/api/seller/orders/search`, CRUD | L3 | 基础订单 CRUD 已有，但未完整覆盖新智慧运单审计字段和筛选 |
| 制单订单 | `/api/document/orders/search`, CRUD | L3 | 基础订单 CRUD 已有，但未完整覆盖 ACC 下单、取号、面单、余额、轨迹 |
| 财务中心 | Spring Boot 未完整实现，Fastify 有原型看板 | L1-L2 | 新智慧财务 POST body 已采集，目标接口已写文档，待迁入 Spring Boot |
| 仓库/标签/轨迹/余额 | 未完整实现 | L1-L2 | 已有文档和数据库设计，待开发 |

## 5. 新智慧 XQT 卖货流程匹配矩阵

| XQT 页面/接口 | 新系统目标接口 | 匹配目标 | 当前等级 | 必须补齐 |
| --- | --- | --- | --- | --- |
| 运单审计 `/rest/tms/aos/shipment/lists` | `POST /api/seller/orders/search`, `POST /api/finance/shipment-audits/search` | 运单、提单、服务、客户、国家、销售/客服/财务归属、金额区间、审核/核销状态 | L2/L3 | Spring Boot 增强查询 body，补齐 XQT 关键筛选和金额字段 |
| 财务流水 `/rest/tms/aos/financial_detail/lists` | `POST /api/finance/ledger-records/search` | 流水号、支付方式、账户、审核、开票、金额、币种、时间 | L2 | 实现 Spring Boot 财务流水接口和字段映射 |
| 客户流水 `/rest/tms/aos/invoice_detail/lists` | `POST /api/finance/customer-charge-details/search` | 客户、费用类型、运单号、账单号、审核、核销、开票 | L2 | 实现费用明细查询、状态映射、旧字段对照 |
| 客户账单 `/rest/tms/aos/invoice/lists` | `POST /api/finance/customer-invoices/search` | 账单号、客户、币种、金额、已核销、未核销、到期日、标签 | L2 | 实现客户账单列表和账单确认/标签筛选 |
| 供应商流水 `/rest/tms/aos/detail_partner/lists` | `POST /api/finance/partner-charge-details/search` | 供应商、费用类型、柜号、运单、金额、账单 | L2 | 实现 AP 明细和供应商映射 |
| 供应商账单 `/rest/tms/aos/invoice_partner/lists` | `POST /api/finance/partner-invoices/search` | 供应商账单号、币种、金额、发货/到期时间、标签 | L2 | 实现供应商账单和时间口径 |
| 账户 `/rest/tms/aos/financial_account/lists` | `POST /api/finance/accounts/search` | 账户归属、币种、余额、状态 | L2 | 实现金融账户列表 |
| 账户流水 `/rest/tms/aos/financial_account_record/lists` | `POST /api/finance/account-records/search` | 账户流水、客户/供应商、业务单据、支付时间 | L2 | 实现账户流水和业务追溯 |
| 费用类型 `/rest/tms/aos/charge_type_mod/lists` | `POST /api/finance/charge-types/search` | 费用编码、名称、类型、显示状态 | L2 | 实现费用字典接口 |
| 汇率 `/rest/tms/aos/currency/lists` | `POST /api/finance/currencies/search` | 币种、汇率、精度、启用状态 | L2 | 实现汇率接口 |

## 6. ACC 制单流程匹配矩阵

| ACC 能力/API | 新系统目标接口 | 匹配目标 | 当前等级 | 必须补齐 |
| --- | --- | --- | --- | --- |
| 订单列表/详情 | `POST /api/document/orders/search`, `GET /api/document/orders/{id}` | 订单状态、客户、产品、地址、申报、重量、渠道、费用 | L3 | 扩展字段模型和详情结构 |
| 客户 API 下单 | `POST /api/customer-api/orders` | API 鉴权、客户下单 body、收件地址、申报明细、产品/渠道选择 | L2 | 实现客户 API 凭证、签名、幂等、下单 |
| 运费试算 | `POST /api/document/rates/quote` | 价格、燃油、偏远、附加费、币种、余额校验 | L2 | 实现计费规则引擎和 ACC 价格样本对照 |
| 提交/审核/取号 | `POST /api/document/orders/{id}/submit` | 订单校验、生成运单、渠道取号、失败原因、状态流 | L2 | 实现 submit 状态机和渠道取号抽象 |
| 面单生成 | `POST /api/labels/generate` | 面单状态、文件引用、下载地址、失败重试 | L2 | 实现标签文件模型和生成流程 |
| 换标 | `POST /api/labels/relabel` | 原单号、新单号、换标记录、费用/轨迹关系 | L2 | 实现换标任务和审计 |
| 面单下载 | `GET /api/labels/{id}/download` | PDF/图片下载、权限、文件有效性 | L2 | 实现对象存储或本地文件服务 |
| 轨迹查询 | `POST /api/tracking/query` | 多来源轨迹聚合、时间线、状态映射 | L2 | 实现 tracking_events 查询和状态映射 |
| 余额查询 | `GET /api/customer-api/balance` | 客户余额、可用额度、冻结/预扣、币种 | L2 | 实现账户余额和预扣费用 |
| 制单费用 | `POST /api/finance/document-charges/search` | 应收运费、费用状态、审核/核销、订单追溯 | L2 | 实现制单费用查询 |
| 制单账单 | `POST /api/finance/document-invoices/search` | 客户账单、付款、余额扣减、月结 | L2 | 实现制单账单闭环 |

## 7. 完全匹配验收维度

每个旧系统页面/API 需要按下列维度验收：

| 维度 | 验收方式 |
| --- | --- |
| 页面字段 | 新系统列表/详情字段能覆盖旧页面核心字段，字段名称可中文化但语义一致 |
| 请求 body | 旧 body 字段有新 body 映射；保留默认值、枚举、数组/字符串差异说明 |
| 筛选语义 | 同一筛选条件能在新系统返回同等业务范围 |
| 状态流 | 旧状态到新状态有映射，不能丢失待审、已审、已核销、取消、失败等关键状态 |
| 金额口径 | 应收、应付、成本、利润、核销、余额、币种和汇率可逐层追溯 |
| 权限范围 | 不同角色能看到的数据范围和操作按钮符合旧系统习惯，并强于旧系统审计 |
| 操作审计 | 新增、修改、删除、审核、提交、导入等写操作必须记录操作人和 before/after |
| 错误处理 | 旧系统常见失败原因在新系统有明确错误码和可读提示 |
| 对照样本 | 每个 P0 页面至少 3-5 条样本通过 comparison case |

## 8. 实施顺序

### 阶段 1：冻结 P0 匹配范围

1. 新智慧只覆盖已抓取的财务 P0、运单审计、客户账单、供应商账单、账户流水。
2. ACC 先覆盖客户 API 下单、内部制单、运费试算、提交取号、面单、轨迹、余额、费用。
3. 所有旧系统字段进入 `external_field_mappings`，不直接变成主表字段名。

### 阶段 2：补 Spring Boot API

优先补齐：

1. `/api/finance/ledger-records/search`
2. `/api/finance/shipment-audits/search`
3. `/api/finance/customer-charge-details/search`
4. `/api/finance/customer-invoices/search`
5. `/api/finance/partner-charge-details/search`
6. `/api/finance/partner-invoices/search`
7. `/api/customer-api/orders`
8. `/api/document/rates/quote`
9. `/api/document/orders/{id}/submit`
10. `/api/labels/generate`
11. `/api/tracking/query`
12. `/api/customer-api/balance`

### 阶段 3：建立字段映射和对照样本

1. 从 `docs/xqt-finance-api-requests.md` 导入 XQT body 字段。
2. 从 `docs/acc-api-reverse-db-design.md` 导入 ACC 字段和状态规则。
3. 在 `external_systems`、`external_modules`、`external_field_mappings`、`comparison_cases` 中保存证据。
4. 每个对照用例记录旧系统截图/请求、新系统请求、expected、actual、diff。

### 阶段 4：页面和 UAT 对照

1. 前端按若依风格做列表、筛选、详情、操作按钮。
2. 业务人员在旧系统选择样本，新系统构造同场景数据。
3. 对照失败先定位字段映射、状态口径、金额口径或样本差异，再改代码。

## 9. 必须由业务确认的边界

没有历史数据接入时，完全匹配需要业务人员提供或确认：

1. 新智慧每个 P0 页面 3-5 条样本截图和筛选条件。
2. ACC 制单、取号、面单、余额、轨迹、费用各 3-5 个典型场景。
3. 金额口径：利润、销售成本、核销、预扣、余额的业务解释。
4. 异常边界：取消、失败、退件、补收、二次制单、改地址、换标。
5. 哪些旧系统缺陷需要保留习惯，哪些要在新系统中优化。

## 10. 下一步开发入口

| 任务 | 代码区域 | 文档依据 |
| --- | --- | --- |
| 卖货财务 Spring Boot API | `apps/backend/src/main/java/com/xqt/saas/finance` | `docs/xqt-finance-api-requests.md`, `docs/api-development-guide.md` |
| 制单客户 API | `apps/backend/src/main/java/com/xqt/saas/customerapi` | `docs/acc-api-reverse-db-design.md` |
| 计费试算 | `apps/backend/src/main/java/com/xqt/saas/rates` | `docs/painpoint-coverage.md`, `docs/acc-api-reverse-db-design.md` |
| 标签与轨迹 | `apps/backend/src/main/java/com/xqt/saas/labels`, `tracking` | `docs/acc-api-reverse-db-design.md` |
| 对照中心 | `apps/backend/src/main/java/com/xqt/saas/external` | `external_*`, `comparison_cases` |

## 11. 一句话执行原则

每开发一个页面或 API，都必须回答四个问题：

1. 它对照 ACC 还是新智慧？
2. 旧系统请求 body 和页面字段是什么？
3. 新系统 body、字段、状态、金额如何映射？
4. 用哪一个 comparison case 证明它已经匹配？
