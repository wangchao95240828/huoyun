# 新航线统一平台对照开发测试用例

版本：2026-05-06  
来源：痛点覆盖矩阵、新智慧只读抓取、ACC API 反推、系统架构和 API 开发文档  
用途：研发、联调、验收时逐条对照，证明新系统正在替代 ACC 和新智慧，而不是只做页面外壳

## 1. 测试目标

本测试用例集解决三件事：

1. **复刻对照**：新智慧没有源码，必须通过页面、筛选、请求 body、字段、状态、金额做人工和自动化对照。
2. **ACC 逻辑迁移**：ACC 有源码但旧逻辑复杂，必须用典型业务场景验证新系统字段、状态、费用、轨迹、余额是否可解释对齐。
3. **痛点闭环**：把痛点文档中的计费、对账、账本、RLS、幂等、SLA、退件、保险、提成等问题转为可执行测试。

测试结论不能只写“通过”。每个对照用例至少要记录：

| 字段 | 说明 |
| --- | --- |
| `case_no` | 用例编号 |
| `source` | XQT / ACC / PAINPOINT / SECURITY / API |
| `module` | 模块 |
| `external_filter` | 外部系统页面筛选或旧系统输入 |
| `internal_url` | 新系统页面或 API |
| `expected_result` | 期望字段、金额、状态、行数、权限结果 |
| `actual_result` | 新系统实际结果 |
| `diff` | 差异说明 |
| `status` | pending / passed / failed / blocked |
| `evidence` | 截图、接口响应、日志、数据库查询证据 |
| `customer_direction` | `SELLER_CUSTOMER` / `DOCUMENT_CUSTOMER` / `BOTH` |
| `source_system` | ACC / XQT / LOCAL |

## 2. 测试分层

| 层级 | 测试类型 | 主要回答 |
| --- | --- | --- |
| L1 | 页面复刻对照 | 新系统页面字段、筛选、状态是否和新智慧/ACC 对得上 |
| L2 | API 契约测试 | 请求 body、响应结构、分页、错误码、权限是否稳定 |
| L3 | 领域规则测试 | 计费、低消、偏远、燃油、保险、对账等规则是否正确 |
| L4 | 数据库测试 | RLS、唯一约束、幂等、账本不可变是否生效 |
| L5 | 端到端测试 | 从订单到费用、账单、核销、账本是否闭环 |
| L6 | 回归测试 | 旧系统典型场景和痛点场景每次迭代不回退 |

## 3. 优先级

| 优先级 | 范围 | 合并要求 |
| --- | --- | --- |
| P0 | 登录权限、新智慧财务 P0、核心计费/对账/账本、RLS、幂等 | 进入主分支前必须通过 |
| P1 | ACC 核心业务、仓库、轨迹、SLA、退件、保险、成本分摊 | 对应模块上线前必须通过 |
| P2 | 客户门户、邮件、POD、海外仓增值、提成深水区、OCR | 功能发布前通过 |

## 4. 测试数据原则

1. **不使用真实账号、密码、token 写入文档。**
2. 新智慧只允许只读对照，不点击新增、保存、审核、删除、导入、导出、生成、同步等动作。
3. 没有历史数据接入时，由业务人员在新智慧/ACC 页面选择 3-5 条样本，记录筛选条件和截图，再由研发在新系统构造同等种子数据。
4. 金额类测试必须记录币种、原币金额、本位币金额、汇率来源、四舍五入规则。
5. 账本类测试不能直接更新已过账数据，只能用冲正或调整分录。
6. 对照失败时不要立即改代码，先判断是字段映射错误、业务口径错误、样本数据错误，还是旧系统隐藏规则。

## 5. comparison_cases 记录模板

```json
{
  "caseNo": "XQT-FIN-001",
  "externalSystem": "XQT",
  "moduleCode": "financial_detail",
  "internalUrl": "/finance/ledger-records",
  "externalFilter": {
    "timeLimit": 0,
    "scenes": 1,
    "keywords": "SAMPLE"
  },
  "expectedResult": {
    "fields": ["serial_number", "payment_type", "audited", "money"],
    "rowCount": "manual-check",
    "amountRule": "金额、币种、审核状态与新智慧页面一致"
  },
  "actualResult": {},
  "status": "pending",
  "diff": []
}
```

## 6. P0 基础能力测试用例

| 用例编号 | 模块 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 | 自动化 |
| --- | --- | --- | --- | --- | --- | --- |
| DIR-001 | 客户方向 | 客户可标记为卖货客户 | 客户主档 | 创建/更新客户 `customerDirection=SELLER_CUSTOMER` | 客户、订单、财务查询均能按卖货客户过滤 | API/DB |
| DIR-002 | 客户方向 | 客户可标记为制单客户 | 客户主档 | 创建/更新客户 `customerDirection=DOCUMENT_CUSTOMER` | 客户 API、制单、余额、标签能力可按方向启用 | API/DB |
| DIR-003 | 客户方向 | 同一客户可同时支持两方向 | 客户主档 | 设置 `customerDirection=BOTH` 并配置 service modes | 菜单、权限、账单模板按服务模式区分 | API/前端 |
| BASE-001 | 登录 | 正确租户、用户名、密码可登录 | 本地租户、管理员用户 | `POST /api/auth/login` | 返回 token、用户、角色、权限，写 `user_sessions` 和登录审计 | API |
| BASE-002 | 登录 | 错误密码不能登录 | 已存在用户 | 使用错误密码登录 | 返回 401 或统一错误，不能写有效 session | API |
| BASE-003 | 会话 | 注销后 token 失效 | 已登录 token | `POST /api/auth/logout` 后访问 `/api/auth/me` | 受保护接口返回 401 | API |
| BASE-004 | 权限 | 无权限角色不能访问管理员接口 | 普通用户 | 访问 `/api/admin/users` | 返回 403，记录安全审计 | API |
| BASE-005 | RLS | 跨租户不能读数据 | 两个租户、各自数据 | 租户 A token 查询租户 B 资源 ID | 返回空或 404，不泄漏数据 | DB/API |
| BASE-006 | RLS | 插入数据必须带当前租户 | 已登录用户 | 创建业务对象 | 记录 `tenant_id` 为当前租户，不允许伪造其它租户 | API/DB |
| BASE-007 | 错误响应 | 参数错误格式统一 | 任意查询接口 | 传非法 pageSize、非法日期 | 返回 `ok=false`, `code=VALIDATION_ERROR`, `traceId` | API |
| BASE-008 | 幂等 | 同一幂等键重复写不重复入账 | 支持写接口 | 同一 `Idempotency-Key` 重复提交 | 第二次返回同一结果，不新增重复数据 | API/DB |

## 7. 新智慧财务 P0 对照测试

这些用例来自 `docs/xqt-finance-api-requests.md` 和 `docs/xqt-readonly-api-crawl.md`。外部系统只做只读查询；新系统必须使用自己的数据库和 `/api/finance/*` 返回结果。

### 7.1 财务流水 `financial_detail`

| 用例编号 | 外部接口 | 新系统接口 | 测试目标 | 外部 body / 新系统 filters | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| XQT-FIN-001 | `/rest/tms/aos/financial_detail/lists` | `POST /api/finance/ledger-records/search` | 默认列表字段对齐 | `{"timeLimit":0,"scenes":1}` | 新系统至少展示流水号、支付方式、审核状态、账户、金额、币种、支付时间 |
| XQT-FIN-002 | 同上 | 同上 | 关键字筛选 | `keywords` / `filters.keyword` | 两边同一关键字筛选后，样本行是否出现一致 |
| XQT-FIN-003 | 同上 | 同上 | 流水号筛选 | `serial_number` / `filters.serialNumber` | 精确流水号返回唯一或同等范围结果 |
| XQT-FIN-004 | 同上 | 同上 | 支付方式和审核状态 | `payment_type`, `audited` | 支付方式中文展示、审核状态枚举一致 |
| XQT-FIN-005 | 同上 | 同上 | 时间范围与金额筛选 | `pay_time`, `created_daterange`, `money` | 边界日期包含规则明确，金额区间不漏不多 |
| XQT-FIN-006 | 同上 | 同上 | 开票状态与账单号 | `invoiced`, `invoice_number` | 能从流水追溯到账单或开票状态 |

### 7.2 运单审计 `shipment`

| 用例编号 | 外部接口 | 新系统接口 | 测试目标 | 外部 body / 新系统 filters | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| XQT-SHP-001 | `/rest/tms/aos/shipment/lists` | `POST /api/finance/shipment-audits/search` | 默认字段对齐 | `{"timeLimit":0,"scenes":1}` | 展示运单号、提单号、服务、客户、应收、应付、利润、审核/核销状态 |
| XQT-SHP-002 | 同上 | 同上 | 运单号/提单号筛选 | `keywords`, `waybill_number`, `lading_number` | 关键单号能定位同一运单 |
| XQT-SHP-003 | 同上 | 同上 | 客户、服务、国家筛选 | `service`, `username`, `country` | 下拉 key/value 映射正确，页面中文显示正确 |
| XQT-SHP-004 | 同上 | 同上 | 销售/客服/财务归属 | `seller_id`, `servicer_id`, `finance_id`, `organization_id` | 权限范围和筛选结果一致 |
| XQT-SHP-005 | 同上 | 同上 | 日期范围 | `created_daterange`, `ship_daterange`, `delivered_daterange` | 日期边界、时区、空值处理一致 |
| XQT-SHP-006 | 同上 | 同上 | 金额区间 | `sell_charge_amount_start/end`, `cost_charge_amount_start/end`, `sell_profit_start/end` | 应收、应付、销售利润、总利润口径不混淆 |
| XQT-SHP-007 | 同上 | 同上 | 审核和核销状态 | `charge_audit`, `charge_paid` | 状态枚举与新智慧页面一致，并能追溯到费用明细 |

### 7.3 客户流水与客户账单

| 用例编号 | 外部接口 | 新系统接口 | 测试目标 | 筛选字段 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| XQT-AR-001 | `/rest/tms/aos/invoice_detail/lists` | `POST /api/finance/customer-charge-details/search` | 客户流水默认字段 | 基础 body | 展示客户、费用类型、运单号、账单号、金额、审核、核销、开票 |
| XQT-AR-002 | 同上 | 同上 | 客户与费用类型筛选 | `user_id`, `charge_type` | 同一客户、同一费用类型结果一致 |
| XQT-AR-003 | 同上 | 同上 | 运单/轨迹/客户参考号筛选 | `shipment_id`, `tracking_number`, `waybill_number`, `client_reference` | 能按任一业务单号定位费用 |
| XQT-AR-004 | 同上 | 同上 | 审核、核销、开票 | `audited`, `paid`, `invoice` | 三类状态不互相替代，页面展示清晰 |
| XQT-INV-001 | `/rest/tms/aos/invoice/lists` | `POST /api/finance/customer-invoices/search` | 客户账单默认字段 | 基础 body | 展示账单号、客户、币种、账单金额、已核销、未核销、到期日 |
| XQT-INV-002 | 同上 | 同上 | 金额和币种筛选 | `currency`, `money` | 原币金额和本位币金额不混淆 |
| XQT-INV-003 | 同上 | 同上 | 账单时间、核销时间、发货时间 | `daterange`, `pay_time`, `ship_time` | 时间口径与页面标签一致 |
| XQT-INV-004 | 同上 | 同上 | 标签和账单确认 | `tags`, `tags_not`, `invoice_confirm` | 标签包含/排除语义正确，账单确认状态可追溯 |

### 7.4 供应商流水与供应商账单

| 用例编号 | 外部接口 | 新系统接口 | 测试目标 | 筛选字段 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| XQT-AP-001 | `/rest/tms/aos/detail_partner/lists` | `POST /api/finance/partner-charge-details/search` | 供应商流水默认字段 | 基础 body | 展示供应商、费用类型、运单号、柜号、金额、审核、核销、账单 |
| XQT-AP-002 | 同上 | 同上 | 供应商和费用类型筛选 | `user_id`, `charge_type` | 供应商语义映射到 `partners`，不是混用客户 |
| XQT-AP-003 | 同上 | 同上 | 柜号和单号筛选 | `container_number`, `shipment_id`, `tracking_number`, `waybill_number` | 能从费用追溯到运单/柜/供应商账单 |
| XQT-PINV-001 | `/rest/tms/aos/invoice_partner/lists` | `POST /api/finance/partner-invoices/search` | 供应商账单默认字段 | 基础 body | 展示账单号、供应商、币种、金额、到期日、核销状态 |
| XQT-PINV-002 | 同上 | 同上 | 金额、创建、发货、到期筛选 | `money`, `created`, `ship_time`, `due_date` | 时间口径和金额区间结果一致 |
| XQT-PINV-003 | 同上 | 同上 | 标签筛选 | `tags` | 供应商账单标签与客户账单标签逻辑一致 |

### 7.5 账户、汇率、费用类型、审批

| 用例编号 | 外部接口 | 新系统接口 | 测试目标 | 筛选字段 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| XQT-ACCNT-001 | `/rest/tms/aos/financial_account/lists` | `POST /api/finance/accounts/search` | 账户列表字段 | `keywords`, `username`, `user_grade`, `created_daterange` | 账户归属、币种、余额、状态显示正确 |
| XQT-ACCNT-002 | `/rest/tms/aos/financial_account_record/lists` | `POST /api/finance/account-records/search` | 账户流水字段 | `id`, `account_id`, `pay_time`, `type`, `username`, `partner_id` | 账户流水能追溯到账户、客户/供应商、业务单据 |
| XQT-DICT-001 | `/rest/tms/aos/charge_type_mod/lists` | `POST /api/finance/charge-types/search` | 费用类型字典 | `name`, `code`, `type`, `is_show` | 费用编码、名称、类型、显示状态一致 |
| XQT-CUR-001 | `/rest/tms/aos/currency/lists` | `POST /api/finance/currencies/search` | 汇率列表 | 基础 body | 币种、汇率、精度、启用状态一致 |
| XQT-APR-001 | `/rest/tms/aos/charge_approval/lists` | `POST /api/finance/charge-approvals/search` | 费用审批列表 | `user_id`, `charge_type`, `serial_number`, `shipment_id`, `approval_time` | 审批单能关联费用、运单、申请人、审批状态 |

## 8. ACC 核心对照测试

这些用例来自 ACC API 反推。ACC 真实数据库未接通前，可用源码规则和人工构造样本；接通只读库后补齐 expected_result。

| 用例编号 | ACC 来源 | 新系统模块 | 测试目标 | 前置数据 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| ACC-ORD-001 | `PreOrder` / `Online` | 订单 | API 预报单映射 | 客户、产品、国家、收件地址、申报明细 | 新系统生成 `orders`、`shipments`、`declarations`，保留外部引用 |
| ACC-ORD-002 | `Modify` | 订单 | 未收货订单可修改 | 状态为未收货的预报单 | 可修改收件、重量、申报；已收货状态拒绝 |
| ACC-ORD-003 | `Submit` | 运单 | 提交后生成运单和渠道取号状态 | 可提交订单 | 状态、转单号、渠道账号、失败原因可追溯 |
| ACC-ORD-004 | `Cancel` | 运单 | 作废申请 | 已提交运单 | 生成作废记录，不直接删除运单 |
| ACC-ORD-005 | `Query` | 运单详情 | 查询订单、申报、收件、轨迹 | 已有运单 | 新系统详情字段能覆盖 ACC Query 关键字段 |
| ACC-LABEL-001 | `Label` / `getNewLabel` | 标签 | 面单文件查询和换标 | 已取号运单 | 能返回标签状态、文件引用、换标关系 |
| ACC-TRACK-001 | `Track` | 轨迹 | 综合轨迹聚合 | Express、Shipment、Transit、Stowage 过程样本 | 新系统 `tracking_events` 能按时间线展示 |
| ACC-PRICE-001 | `Price` | 计费 | 销售价试算 | 产品、国家、重量、客户价卡 | 新系统试算结果与 ACC 可解释对齐 |
| ACC-BAL-001 | `Balance` | 财务账户 | 客户余额查询 | 客户余额样本 | 账户余额、币种、方向和流水一致 |
| ACC-SYNC-001 | `Sync` | 配载/仓库 | 客户配载同步 | Stowage、Package 样本 | 新系统批次/箱关系不丢失 |

## 9. 痛点驱动领域测试

### 9.1 计费引擎 A 类

| 用例编号 | 痛点 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PAIN-A1-001 | 敏感品/产品词附加费 | 品名命中敏感词自动加收 | 申报品名包含敏感词；规则启用 | 创建运单并试算 | 生成敏感品费用，`charges.evidence` 记录命中词和规则版本 |
| PAIN-A2-001 | 附加费叠加 vs 取大 | 同组附加费按取大执行 | 同一箱同时命中两个同组规则 | 试算费用 | 只生成金额较大费用或标记被覆盖规则 |
| PAIN-A2-002 | 附加费叠加 vs 取大 | 不同组附加费可叠加 | 规则分属不同 group | 试算费用 | 多条费用同时生成，合计正确 |
| PAIN-A3-001 | 尺寸附加费识别即全收 | 任一箱超尺寸触发全票附加费 | 一票多箱，其中一箱超尺寸 | 试算费用 | 全票按规则加收，证据记录超尺寸箱 |
| PAIN-A4-001 | 单箱低消 | 单箱低于最低计费重 | 单箱实重 10kg，渠道低消 24kg | 试算费用 | 计费重按 24kg，保留原始重量和计费重 |
| PAIN-A5-001 | 偏远三档 | 地址命中偏远等级 | 邮编匹配 level 2 | 试算费用 | `shipments.remote_level=2`，生成对应偏远费 |
| PAIN-A6-001 | 多单位换算 | kg/lb/CBM/件共存 | 价卡含 lb、CBM、piece | 试算费用 | 单位换算和四舍五入可追溯 |
| PAIN-A8-001 | 燃油月度版本 | 按发货月份取燃油 | 4 月和 5 月燃油不同 | 分别试算两个发货日期 | 使用对应月份燃油版本，不串月 |
| PAIN-A9-001 | 保险独立计费 | 投保后生成保费 | 保险金额、保险费率 | 创建投保事件 | 生成保险费用，关联保单和 API 事件 |
| PAIN-A10-001 | 合并报关重复计费 | 多票合并只收一次 | 3 票同一报关组 | 生成报关费用 | 只生成一笔或按策略均摊，不重复收费 |
| PAIN-A12-001 | 反倾销/禁运黑名单 | 禁运品拦截 | 品名命中禁运 | 创建/提交运单 | 阻止提交，生成风险筛查结果和审批入口 |

### 9.2 对账与账单 B 类

| 用例编号 | 痛点 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PAIN-B1-001 | 渠道账单格式差异 | 不同模板导入到标准字段 | 两种渠道账单样本 | 上传导入 | 原始行保留，标准字段映射成功 |
| PAIN-B2-001 | 主/子单匹配不稳 | 主单和子单都能匹配 | 渠道账单只有子单号 | 执行对账 | 匹配到正确运单，置信度和匹配原因可见 |
| PAIN-B2-002 | 补收匹配 | 后补费用能关联原运单 | 原账单已对账，后续补收一行 | 再次导入并对账 | 识别为补收，不覆盖原账单 |
| PAIN-B3-001 | 原始账单直传解析 | 导入文件可追溯 | 上传文件 | 查看导入详情 | 有文件指纹、原始 JSON、解析状态 |
| PAIN-B4-001 | 费用关键字科目化 | 英文费用名映射标准费用 | 费用名包含 `REMOTE AREA` | 导入账单 | 映射到偏远费，未识别项进异常队列 |
| PAIN-B5-001 | 客户账单生成 | 锁定应收生成客户账单 | 多条已审核 AR 费用 | 生成客户账单 | 账单行、总额、币种、模板版本正确 |
| PAIN-B7-001 | 成本导入无需手工整理 | 原始账单直接形成对账任务 | 未整理 Excel/CSV 样本 | 上传导入 | 后台任务执行，错误行可下载或查看 |

### 9.3 时效、轨迹、退件 C/D 类

| 用例编号 | 痛点 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PAIN-C1-001 | 时效起止点写死 | SLA 起止点可配置 | SLA 规则起点为入仓、终点为签收 | 写入里程碑 | SLA 结果按配置计算 |
| PAIN-C3-001 | 中期时效未展示 | 分段 SLA 展示 | 头程、清关、尾程里程碑 | 查询运单 SLA | 展示每段耗时和异常段 |
| PAIN-C5-001 | 子单轨迹状态粗 | 子单轨迹独立归一 | 一票多箱多轨迹 | 导入轨迹 | 每个箱轨迹独立，运单汇总状态可解释 |
| PAIN-D1-001 | 退件二次制单割裂 | 退件和新单不断链 | 原单签收失败 | 创建退件和二次制单 | `shipment_relations` 关联原单、退件、新单 |

### 9.4 成本、币种、账本、提成 E/F/G/H 类

| 用例编号 | 痛点 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PAIN-E1-001 | 渠道报价表未进系统 | 成本价卡试算 AP | 渠道成本价卡 | 运单试算 | 生成预估成本，后续可对账 |
| PAIN-E2-001 | 预估 vs 实际差异告警 | 超阈值差异告警 | 预估 100，实际 130，阈值 10% | 导入实际账单 | 生成差异结果和告警 |
| PAIN-F1-001 | BL/柜成本批量分摊 | 柜级成本按重量分摊 | 一柜含 3 票，重量不同 | 发起分摊 | 分摊合计等于柜成本，单票成本按规则正确 |
| PAIN-G1-001 | 外币报价 vs 人民币记账 | 原币和本位币分离 | USD 账单，CNY 本位币汇率 | 核销并过账 | ledger 同时记录原币、本位币、汇率 |
| PAIN-G2-001 | 展示与记账口径混淆 | 汇兑损益独立 | 账单汇率和收款汇率不同 | 收款核销 | 生成汇兑损益分录，不改原账单金额 |
| PAIN-H1-001 | 提成靠 Excel | 月度提成自动计算 | 提成方案、已核销账单 | 生成月结提成 | 生成 `commission_lines`，可复核，不直接过账 |

### 9.5 平台能力和附加功能 I/J 类

| 用例编号 | 痛点 | 测试目标 | 前置数据 | 操作步骤 | 预期结果 |
| --- | --- | --- | --- | --- | --- |
| PAIN-I1-001 | 批量操作与后台异步 | 导入任务可重试可追踪 | 大文件导入 | 启动导入任务 | `background_jobs` 有状态、attempt、错误详情 |
| PAIN-I1-002 | 幂等 | 重复导入文件不重复入账 | 同一文件 hash | 重复上传 | 识别重复文件，返回已有任务或拒绝重复 |
| PAIN-J1-001 | 海外仓增值服务 | 增值服务生成费用 | 贴标/换箱服务单 | 完成服务 | 生成服务费用并关联运单/服务单 |
| PAIN-J2-001 | 旺季附加费 | 时间窗规则生效 | 旺季规则 11-12 月 | 试算 10 月和 11 月运单 | 仅 11 月命中旺季附加费 |
| PAIN-J3-001 | POD 申请 | POD 可追踪 | 已签收运单 | 提交 POD 申请 | 生成申请、附件状态、操作日志 |
| PAIN-J4-001 | 问题件工单 | 异常可流转 | 运单异常 | 创建问题件工单并处理 | 状态流转、责任人、处理记录完整 |

## 10. API 契约测试

| 用例编号 | API | 测试目标 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- |
| API-001 | 所有列表查询 | 分页一致 | `pageNum=1,pageSize=20` | 返回 `items`, `page.total` |
| API-002 | 所有列表查询 | pageSize 限制 | `pageSize=10000` | 返回校验错误或按最大值截断 |
| API-003 | 所有写接口 | 幂等键必需 | 不传 `Idempotency-Key` | 金额/库存/账单写接口拒绝或进入非幂等白名单 |
| API-004 | 所有受保护接口 | 未登录拒绝 | 无 Authorization | 返回 401 |
| API-005 | 对象查询 | 对象级授权 | 查询不属于权限范围的数据 | 返回 403/404，不泄漏对象存在性 |
| API-006 | 外部兼容 API | 不转发新智慧写请求 | 调用 `/api/external/xqt/aos/*/lists` | 只查本地库，不向 `XQT_BASE_URL` 发写请求 |
| API-007 | 错误响应 | traceId | 人为制造业务错误 | 响应和日志中有同一个 traceId |
| API-008 | 日期筛选 | 时区边界 | 查询当天 00:00-23:59 | 结果不跨天、不漏边界 |

## 11. 数据库和账本测试

| 用例编号 | 模块 | 测试目标 | 操作 | 预期结果 |
| --- | --- | --- | --- | --- |
| DB-001 | RLS | SELECT 隔离 | 设置租户 A 查询租户 B 数据 | 查询不到 |
| DB-002 | RLS | INSERT 隔离 | 尝试插入其它租户 ID | 被拒绝或被服务层覆盖为当前租户 |
| DB-003 | 幂等 | 幂等键唯一 | 同租户同 key 重复写 | 不重复执行业务 |
| DB-004 | 账本 | 复式平衡 | 生成一笔过账 | 借贷平衡，entry 至少两条 |
| DB-005 | 账本 | 已过账不可变 | 尝试更新 ledger entry | 数据库或服务层拒绝 |
| DB-006 | 账本 | 冲正 | 对已过账交易冲正 | 生成反向分录，原分录保留 |
| DB-007 | 外部映射 | 外部 ID 不作为主键 | 写入 ACC/XQT 外部对象 | 主表用内部 ID，外部 ID 在 `external_object_refs` |
| DB-008 | 对照用例 | comparison case 可追踪 | 写 expected/actual/diff | 状态和证据可查询 |

## 12. 端到端验收场景

| 用例编号 | 场景 | 步骤 | 预期结果 |
| --- | --- | --- | --- |
| E2E-001 | 新智慧财务只读复刻 | 选择新智慧财务流水样本 -> 在新系统创建对应数据 -> 用相同筛选查询 | 字段、金额、状态、行数可解释一致 |
| E2E-002 | 客户账单闭环 | 运单计费 -> 审核 AR -> 生成客户账单 -> 收款核销 -> 过账 | 可从账本追溯到收款、账单、费用、运单 |
| E2E-003 | 供应商账单对账 | 生成预估 AP -> 导入渠道账单 -> 自动匹配 -> 差异处理 -> 供应商账单 | 匹配置信度、差异原因、调整记录完整 |
| E2E-004 | ACC 下单迁移 | ACC 样本 PreOrder/Submit -> 新系统下单/提交 -> 标签/轨迹/费用查询 | 字段和状态可解释对齐 |
| E2E-005 | 高风险审批 | 禁运品或超尺寸 -> 系统拦截 -> 有权限审批 -> 继续流程 | 审批前不能继续，审批后有完整审计 |
| E2E-006 | 多币种核销 | USD 账单 -> CNY 收款 -> 汇率差异 -> 账本过账 | 原币、本位币、汇兑损益分录正确 |

## 13. 每日开发对照清单

研发每天收工前至少完成：

| 检查项 | 标准 |
| --- | --- |
| 新增接口 | 有请求 body、响应示例、错误码、权限点 |
| 新增页面 | 有对应字段映射和筛选测试 |
| 新增财务逻辑 | 有金额、币种、状态、证据字段 |
| 新增写操作 | 有幂等、审计、权限、失败回滚 |
| 新增外部映射 | 写入 `external_modules` / `external_field_mappings` 或文档 |
| 新增对照结果 | 写入或更新 `comparison_cases` |
| 修复差异 | 标记 diff 原因：字段映射/口径/样本/代码 bug |

## 14. 本周优先执行的 20 条

第一周先不要铺太散，优先跑下面 20 条：

1. BASE-001 登录成功。
2. BASE-003 注销后 token 失效。
3. BASE-004 无权限访问管理员接口返回 403。
4. BASE-005 RLS 跨租户读隔离。
5. XQT-FIN-001 财务流水默认字段。
6. XQT-FIN-002 财务流水关键字筛选。
7. XQT-FIN-004 财务流水支付方式和审核状态。
8. XQT-SHP-001 运单审计默认字段。
9. XQT-SHP-006 运单审计金额区间。
10. XQT-AR-001 客户流水默认字段。
11. XQT-INV-001 客户账单默认字段。
12. XQT-PINV-001 供应商账单默认字段。
13. XQT-ACCNT-001 账户列表字段。
14. XQT-DICT-001 费用类型字典。
15. PAIN-A4-001 单箱低消。
16. PAIN-A5-001 偏远三档。
17. PAIN-B2-001 主/子单匹配。
18. DB-004 复式账本平衡。
19. DB-005 已过账不可变。
20. E2E-002 客户账单闭环。

## 15. 通过标准

| 阶段 | 通过标准 |
| --- | --- |
| 本周 | P0 新智慧财务页面和查询 body 可对照，基础权限/RLS/API 契约通过 |
| 第 2 周 | ACC 核心订单、运单、费用、轨迹样本可对照，计费痛点 A/B 至少覆盖 70% |
| 第 3 周 | 账单、核销、过账、差异处理跑通，P0 端到端用例通过 |
| 上线前 | P0 全通过，P1 高风险用例通过，所有 failed/blocker 有明确处理计划 |
