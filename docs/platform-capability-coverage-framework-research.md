# 新航线统一平台功能覆盖与框架选型调研

版本：2026-05-07  
结论级别：架构决策建议  
目标：确认新平台覆盖 ACC、新智慧、痛点能力和常见 SaaS 能力，并选择适合 Java/Spring Boot 路线的框架组合

## 1. 结论

新平台必须不是“ACC + 新智慧的菜单拼接”，而是一个新的物流/TMS/财务 SaaS 主系统。更准确地说，ACC 和新智慧代表两条客户方向的旧系统样本：**卖货客户方向**和**制单客户方向**。平台要覆盖四类能力：

1. **卖货客户方向能力**：货品、销售订单、仓库、发货、客户账单、供应商成本、利润、售后、POD。
2. **制单客户方向能力**：API 下单、批量制单、取号、面单、轨迹、运费试算、余额、预扣费、客户 API。
3. **ACC / 新智慧旧系统能力**：作为两类方向的字段、流程、状态、金额口径和对照来源。
4. **通用 SaaS 平台能力**：租户、用户、角色、菜单、权限、组织、字典、参数、审计、导入导出、任务、通知、文件、API 凭证、数据权限、工作流、可观测性。

框架建议：

> **推荐采用“Spring Boot 3 + Spring Modulith + PostgreSQL RLS + Vue3/Element Plus 若依风格前端 + 自研领域模块”的组合。**

若依/若依 Plus 可以作为后台管理和 SaaS 基础能力参考，但不建议整套替换当前项目。原因是我们的核心难点在 ACC/XQT 业务复刻、财务账本、自动对账、计费规则、租户 RLS 和外部对照，不是普通 CRUD。

## 2. 新平台功能总蓝图

| 一级模块 | 覆盖来源 | 核心能力 | 优先级 |
| --- | --- | --- | --- |
| 客户方向模型 | 业务口径修正 | `SELLER_CUSTOMER`, `DOCUMENT_CUSTOMER`, `BOTH`, `source_system`, `service_mode` | P0 |
| 租户与系统治理 | SaaS 通用、若依、架构验证 | 租户、用户、角色、权限、菜单、部门、岗位、字典、参数、登录日志、操作日志 | P0 |
| 主数据中心 | ACC、新智慧、SaaS 通用 | 客户、供应商、渠道、服务、产品、费用类型、币种、国家地区、仓库、员工、组织 | P0 |
| 订单与运单 | ACC、新智慧 CSOS | 预报、下单、运单、提单、货箱、快递单、预约取件、单证、POD、查货集 | P0/P1 |
| 仓库作业 | ACC、新智慧 DOS | 收货、出货、托盘、小包装箱、打托派送、扫描、拣货日志、库存 | P1 |
| 计费规则 | ACC、痛点 | 价卡、附加费、低消、偏远、燃油、保险、分抛、合并报关、旺季费 | P0 |
| 财务中心 | ACC、新智慧 AOS、痛点 | 财务流水、客户流水、客户账单、供应商流水、供应商账单、账户、账户流水、审批、汇率、月结 | P0 |
| 对账与成本 | 痛点、新智慧、SaaS 通用 | 渠道账单导入、字段映射、科目化、主/子单匹配、补收、差异处理 | P0/P1 |
| 正式账本 | 架构验证、财务痛点 | 复式分录、过账、冲正、汇兑损益、不可变审计 | P0 |
| 轨迹与 SLA | ACC、新智慧 CSOS、痛点 | 轨迹中台、状态映射、里程碑、SLA、赔付内部参考 | P1 |
| 退件与异常 | ACC、痛点 | 退件、二次制单、问题件、赔偿、理赔、异常工单 | P1 |
| 保险 | 新智慧、痛点 | 保险单、投保 API、保单回传、保费入账、理赔 | P1 |
| 销售提成 | ACC、新智慧、痛点 | 销售成本流水、销售提成流水、提成单、月度提成计算 | P1/P2 |
| 外部复刻中心 | ACC、新智慧 | external systems、modules、field mappings、object refs、comparison cases | P0 |
| 客户门户/API | ACC、新智慧 CSOS、SaaS 通用 | 客户 API 凭证、下单、查价、查轨迹、查账单、余额 | P1/P2 |
| 平台工程能力 | SaaS 通用 | 文件、导入导出、后台任务、幂等、通知、Webhooks、监控、告警 | P0/P1 |

## 3. ACC 功能覆盖矩阵

| ACC 功能域 | 已识别能力 | 新平台模块 | 覆盖策略 | 优先级 |
| --- | --- | --- | --- | --- |
| 快件订单 | 订单列表、详情、轨迹、快速下单、作废订单、预报包裹 | 订单与运单 | `orders` + `shipments` + `tracking_events`，保留 ACC 外部引用 | P0 |
| 出货与配载 | 出货、出货明细、配载、配载包裹、总单/留仓、转运 | 履约/仓库/批次 | `ship_batches`, `containers`, `shipment_batch_links`, `warehouse_records` | P1 |
| 仓库 | 仓库、入库、出库、扫描检查、装箱单 | 仓库作业 | 仓库扫描事件 + 箱级库存 | P1 |
| 退件/扣件/问题件/赔偿 | returns、detains、asks、reparations | 异常与退件 | `return_orders`, `exception_tickets`, `claim_reviews` | P1 |
| 应收应付 | charges、costs、unpaid、batch audit | 财务中心 | `charge_items`, `charge_audit_events`, `receivables`, `payables` | P0 |
| 客户账单 | bills、bill items、generate、reload、audit | 客户账单 | `customer_invoices`, `customer_invoice_lines`, 账单版本 | P0 |
| 收付款 | payments、receiveds、quick received、banks、transfers | 资金与核销 | `payments`, `financial_accounts`, `financial_account_records`, `ledger_entries` | P0 |
| 利润 | profits、profit-report、overdue、summary | 利润与对账 | 应收、应付、实际成本、账本聚合 | P1 |
| 提成 | commissions、calculate、audit | 提成 | `commission_runs`, `commission_lines` | P1 |
| 客户/供应商/渠道 | customers、suppliers、channels、channel accounts | 主数据中心 | `customers`, `partners`, `channels`, `service_accounts` | P0 |
| 价格/费用/燃油/偏远 | products、fees、fee-types、fuels、remotes、zones、postcodes | 计费规则 | `rate_cards`, `rate_card_lines`, `charge_rules`, `remote_zones` | P0 |
| 员工组织 | employees、wages、attendances、branches、departments | 组织管理 | `organizations`, `departments`, `staff`, 可后置工资考勤 | P1/P2 |
| 客户 API | auth、balance、products、query、price、order | 客户门户/API | `api_credentials`, 客户下单/查价/查轨迹 | P1 |
| 标签/轨迹 | labels、tracking、track event、new label | 轨迹与文件 | `tracking_events`, `shipment_labels`, 对象存储 | P1 |
| 报表 | product/monthly/country/customer/employee reports | 报表中心 | PostgreSQL 聚合视图或报表模块 | P2 |
| 任务/模板/通知 | tasks、templates、notices、SMS | 平台工程能力 | 后台任务、消息模板、通知中心 | P1 |

结论：ACC 功能点可以完整覆盖，但不能照搬 ACC 表。ACC 字段进入 adapter 和 `external_*`，最终沉淀到订单、运单、仓库、财务、账本等主系统模型。

## 4. 新智慧功能覆盖矩阵

新智慧抓取到 109 个菜单/动作节点、88 个可读页面和 88 个自动加载端点。新平台应按 `csos/aos/dos` 三个域覆盖。

### 4.1 CSOS：客户侧单据/用户合同

| 新智慧页面 | 新平台模块 | 覆盖状态 | 优先级 |
| --- | --- | --- | --- |
| 运单 `shipment` | 订单与运单 | 必须覆盖 | P0 |
| 提单 `waybill` | 批次/提单/柜 | 必须覆盖 | P1 |
| 工单 `ticket` | 问题件/工单 | 覆盖 | P1 |
| 快递单 `outer_shipment` | 外部快递单/渠道单 | 覆盖 | P1 |
| 保险单 `insure` | 保险 | 覆盖 | P1 |
| 预约取件 `booking` | 上门揽收/预约 | 覆盖 | P2 |
| 单证 `declaration` | 报关/单证 | 覆盖 | P1 |
| 货箱/邮包/小包 | 箱级履约 | 必须覆盖 | P0/P1 |
| POD 管理 | POD | 覆盖 | P2 |
| 轨迹跟踪 | 轨迹中台 | 必须覆盖 | P1 |
| 用户/用户等级/合同/结算方式 | 客户/合同/结算主数据 | 必须覆盖 | P0 |
| 客户 API 对接 | 客户 API 凭证 | 覆盖 | P1 |

### 4.2 AOS：财务/主数据/规则/系统

| 新智慧页面 | 新平台模块 | 覆盖状态 | 优先级 |
| --- | --- | --- | --- |
| 财务流水 | 财务账户流水/账本流水 | 必须覆盖 | P0 |
| 运单审计 | 运单费用审计 | 必须覆盖 | P0 |
| 应收报表/客户流水/客户账单 | 应收/客户账单 | 必须覆盖 | P0 |
| 应付报表/供应商流水/供应商账单 | 应付/供应商账单 | 必须覆盖 | P0 |
| 销售成本/销售提成/提成单 | 销售成本与提成 | 覆盖 | P1 |
| 账户/账户流水 | 资金账户 | 必须覆盖 | P0 |
| 汇率 | 汇率管理 | 必须覆盖 | P0 |
| 费用类型 | 费用字典 | 必须覆盖 | P0 |
| 月结单 | 月结锁定 | 覆盖 | P1 |
| 费用审批/审批 | 审批中心 | 覆盖 | P1 |
| 运价维护/服务/供应商/线路/区域/仓库/机场港口 | 价卡与主数据 | 必须覆盖 | P0/P1 |
| API 配置/API 映射 | 外部集成配置 | 覆盖 | P1 |
| 地址库/快递分区/号段/偏远查询/税金 | 规则与地址服务 | 覆盖 | P1 |
| 事件&规则/超长超重/运费计算器 | 计费规则引擎 | 必须覆盖 | P0 |
| 系统设置/模块设置/操作日志/后台任务/模板/标识/员工/组织/角色 | SaaS 系统治理 | 必须覆盖 | P0/P1 |

### 4.3 DOS：仓库作业

| 新智慧页面 | 新平台模块 | 覆盖状态 | 优先级 |
| --- | --- | --- | --- |
| 仓库收货 `picklist` | 入库/收货 | 必须覆盖 | P1 |
| 仓库出货 `lading` | 出库/发货 | 必须覆盖 | P1 |
| 托盘管理 `pallet` | 托盘/批次 | 覆盖 | P1 |
| 打托派送 `pallet_delivery` | 派送/卡派 | 覆盖 | P1 |
| 小包换标日志 | 换标/日志 | 覆盖 | P2 |
| 小包装箱 `packing` | 装箱 | 覆盖 | P1 |
| 常用标签/拣货日志 | 仓库工具与日志 | 覆盖 | P1/P2 |

结论：新智慧的功能边界已基本纳入新平台。第一阶段重点不是全量写操作，而是 P0 财务和运单审计页面的字段、筛选、状态、金额口径复刻。

## 4.4 客户方向覆盖矩阵

| 客户方向 | 核心链路 | 重点模块 | 旧系统样本 |
| --- | --- | --- | --- |
| 卖货客户 `SELLER_CUSTOMER` | 货品/订单 -> 仓库 -> 发货 -> 客户账单 -> 利润/售后 | 订单、仓库、运单、客户账单、供应商账单、POD、问题件、提成 | ACC/XQT 中对应卖货客户页面和字段 |
| 制单客户 `DOCUMENT_CUSTOMER` | API/批量制单 -> 取号/面单 -> 轨迹 -> 预扣费/余额 -> 账单 | 客户 API、制单、标签、轨迹、余额、运费试算、批量导入 | ACC/XQT 中对应制单客户页面和字段 |
| 双方向客户 `BOTH` | 同一客户既有卖货履约又有制单需求 | 统一客户主档 + 多服务模式 + 独立结算配置 | 通过 `external_object_refs` 合并旧系统客户引用 |

## 5. 常见 SaaS 功能点覆盖矩阵

| SaaS 功能 | 新平台是否需要 | 新平台设计落点 | 优先级 |
| --- | --- | --- | --- |
| 多租户 | 必须 | `tenants` + 所有业务表 `tenant_id` + PostgreSQL RLS | P0 |
| 用户/角色/权限 | 必须 | `users`, `roles`, `permissions`, Spring Security | P0 |
| 菜单/按钮权限 | 必须 | `menus`, `permissions.code`, 前端动态路由 | P0 |
| 组织/部门/岗位 | 必须 | `organizations`, `departments`, staff | P0/P1 |
| 数据权限 | 必须 | 租户 RLS + 部门/客户/销售归属 scope | P0/P1 |
| 字典管理 | 必须 | 费用类型、状态、服务、标签等字典 | P0 |
| 参数配置 | 必须 | 系统参数、财务参数、外部接口配置 | P0/P1 |
| 操作日志/登录日志 | 必须 | `audit_logs`, `auth_login_events` | P0 |
| 审批流 | 必须 | `approval_requests`, `approval_decisions`；复杂后接工作流引擎 | P1 |
| 文件/对象存储 | 必须 | 面单、导入文件、账单、保单、POD 附件 | P1 |
| 导入/导出 | 必须 | Excel/CSV 导入，后台任务，文件指纹 | P0/P1 |
| 后台任务 | 必须 | `background_jobs`, `background_job_attempts`, Redis | P0 |
| 幂等 | 必须 | `idempotency_keys`，金额/账单/导入写操作强制 | P0 |
| 通知中心 | 需要 | 短信、邮件、企业通知、站内信 | P1 |
| API 凭证 | 需要 | 客户 API、外部系统 API key、签名 | P1 |
| Webhook | 需要 | 客户/承运商事件推送 | P2 |
| 租户套餐/功能开关 | 建议 | tenant plan、feature flags、module entitlements | P1/P2 |
| 审计报表 | 必须 | 财务操作、审批、过账、导入、对账报告 | P0/P1 |
| 可观测性 | 必须 | 日志、traceId、metrics、任务监控 | P1 |
| 代码生成 | 可选 | 普通主数据 CRUD 可用，核心业务手写 | P2 |
| 低代码表单/报表 | 可选 | 新智慧配置类页面可借鉴，核心逻辑不可低代码化 | P2 |

## 6. 框架调研

### 6.1 RuoYi / RuoYi-Vue3

定位：成熟 Java 后台管理脚手架，适合系统管理、菜单权限、字典、日志、代码生成、定时任务等。

适合我们的地方：

- 若依后台体验成熟，适合我们前端“若依风格”目标。
- 系统管理功能完整，可以参考其用户、角色、菜单、字典、参数、操作日志。
- 普通主数据 CRUD 可以参考若依的代码生成思路。

不适合直接整套替换的地方：

- 默认更偏 MySQL/MyBatis 体系，我们已采用 PostgreSQL + RLS + Spring JDBC/自研领域模型。
- 若依不是为“强租户隔离 + 物流财务账本 + 外部系统复刻”设计。
- 直接替换会打断当前 Spring Boot 主后端和数据库设计。

结论：**用其前端后台壳和系统管理思想，不整套替换。**

### 6.2 RuoYi-Vue-Plus

定位：若依增强版，通常包含 Spring Boot 3、Vue3、Sa-Token、MyBatis-Plus、多租户等现代化改造。

适合我们的地方：

- 比原始若依更接近现代 Spring Boot 3/Vue3 技术栈。
- 自带多租户、数据权限、登录鉴权、缓存、OSS、定时任务等能力的参考价值更高。
- 可以借鉴其模块组织和 SaaS 基础设施。

风险：

- 多租户多为应用层/插件层方案，我们需要 PostgreSQL RLS 作为数据库底线。
- Sa-Token/MyBatis-Plus 与我们当前 Spring Security/Spring JDBC 路线不同。
- 直接迁入会造成认证、数据访问、SQL 风格大范围重构。

结论：**作为“实现参考库”，不作为主架构替换。**

### 6.3 JEECG Boot

定位：低代码企业应用平台，强项是在线表单、代码生成、报表、大屏、常规后台。

适合我们的地方：

- 适合配置类、报表类、主数据类页面快速生成。
- 对“新智慧系统设置/模块设置/模板/报表”这类页面有参考价值。

风险：

- 平台较重，低代码机制会和我们强领域模型、财务账本、对账状态机冲突。
- 核心 TMS/财务逻辑不能低代码化，否则规则和审计会失控。

结论：**不建议作为主框架；可参考报表/低代码思路。**

### 6.4 JHipster

定位：国际化全栈应用生成器，支持 Spring Boot、前端框架、微服务、OAuth/OIDC 等。

适合我们的地方：

- 工程规范、生成器、测试、微服务演进理念成熟。
- 如果从零做国际化 SaaS，会是一个候选。

风险：

- 中文后台生态、若依风格、物流行业页面不匹配。
- 生成器会引入较多约定，对当前已有项目迁移收益不高。
- 微服务能力当前阶段不是主要矛盾。

结论：**不采用。**

### 6.5 Spring Modulith

定位：Spring 官方模块化单体支持，适合在单体内部建立清晰模块边界，后续可演进。

适合我们的地方：

- 与 Spring Boot 主线天然兼容。
- 能把 `auth/admin/masterdata/order/shipment/finance/ledger/external` 做成明确模块。
- 适合三人三周先做模块化单体，避免过早微服务。
- 后续可以用模块边界、事件、集成测试和文档约束架构。

风险：

- 它不是后台管理框架，不提供若依那种页面和系统管理模块。
- 需要团队遵守模块边界。

结论：**推荐作为后端模块边界框架。**

### 6.6 Flowable / Camunda

定位：BPMN 工作流/流程编排引擎。

适合我们的地方：

- 费用审批、财务调整、绕 SOP、禁运覆盖、理赔审批如果变复杂，可接入工作流。
- 可以让审批流程可视化、可配置、可审计。

风险：

- 第一阶段引入会增加复杂度。
- 当前可以先用 `approval_requests/approval_decisions` 表实现轻量审批。

结论：**P1 后再评估；P0 不引入。**

## 7. 框架选型评分

评分：5 分最好。

| 方案 | ACC/XQT 复刻适配 | SaaS 基础能力 | 财务账本适配 | PostgreSQL RLS 适配 | 团队落地速度 | 总评 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 当前 Spring Boot + 自研领域模型 | 5 | 3 | 5 | 5 | 4 | 推荐主线 |
| Spring Boot + Spring Modulith | 5 | 3 | 5 | 5 | 4 | 推荐增强 |
| 选择性吸收 RuoYi/RuoYi-Vue3 | 4 | 4 | 3 | 3 | 5 | 推荐前端/管理能力 |
| 参考 RuoYi-Vue-Plus | 4 | 5 | 3 | 3 | 4 | 可参考，不整体迁移 |
| 整套切 RuoYi | 2 | 4 | 2 | 2 | 3 | 不推荐 |
| JEECG Boot 主框架 | 2 | 5 | 2 | 2 | 3 | 不推荐主线 |
| JHipster 主框架 | 2 | 4 | 3 | 3 | 2 | 不推荐 |
| Flowable/Camunda 作为核心 | 1 | 2 | 3 | 3 | 2 | 只适合作审批插件 |

## 8. 推荐技术组合

### 8.1 后端

| 层 | 推荐 |
| --- | --- |
| 主框架 | Spring Boot 3.x |
| 模块边界 | Spring Modulith |
| 安全 | Spring Security + 自有 `users/roles/permissions` |
| 数据访问 | Spring JDBC / NamedParameterJdbcTemplate；复杂 SQL 可后续引入 jOOQ |
| 数据库 | PostgreSQL + RLS |
| 缓存/任务状态 | Redis |
| 异步任务 | 当前 `background_jobs` + Redis，后续再评估 MQ |
| 工作流 | P0 自研轻量审批，P1 评估 Flowable/Camunda |
| 外部复刻 | `adapter.acc`, `adapter.xqt`, `external_*`, `comparison_cases` |

### 8.2 前端

| 层 | 推荐 |
| --- | --- |
| 主框架 | Vue 3 + Vite |
| UI | Element Plus，若依风格布局 |
| 图标 | lucide 或 Element Plus icons |
| 权限 | 基于 `permissions.code` 的菜单/按钮权限 |
| 页面规范 | 若依式查询表单、表格工具栏、分页、状态标签、批量动作 |
| 复杂表单 | 财务和订单核心表单手写，不用低代码生成 |

### 8.3 平台能力

| 能力 | 实现建议 |
| --- | --- |
| 菜单/字典/参数/日志 | 参考若依，实现到我们自己的表和 API |
| 代码生成 | 只用于主数据普通 CRUD |
| 多租户 | PostgreSQL RLS，不依赖前端或 ORM 插件保证 |
| 数据权限 | RLS + 部门/客户/销售归属 scope |
| 审批 | 先自研轻量审批，后续再引擎化 |
| 报表 | P0 用 SQL 聚合视图，P2 再评估 BI/低代码报表 |

## 9. 分阶段落地路线

### 阶段 0：立即确认架构边界

1. 保留现有 Spring Boot 主后端，不整体切换若依。
2. 前端继续若依风格改造。
3. 把平台能力拆成 `admin/system/platform` 模块。
4. 把 ACC/XQT 放入 `adapter` 和 `external`，不进入核心表主键。

### 阶段 1：补齐 SaaS 基础能力

| 模块 | 功能 |
| --- | --- |
| 用户角色权限 | 用户、角色、权限、菜单、按钮权限 |
| 租户 | 租户信息、租户隔离、租户状态 |
| 字典参数 | 费用类型、状态、币种、服务、系统参数 |
| 日志审计 | 登录日志、操作日志、错误日志 |
| 文件任务 | 文件上传、导入任务、任务状态、幂等键 |

### 阶段 2：复刻 ACC 和新智慧 P0

| 来源 | 功能 |
| --- | --- |
| 新智慧 | 财务流水、运单审计、客户流水、客户账单、供应商流水、供应商账单、账户、费用类型 |
| ACC | 订单、运单、费用、账单、收付款、客户/供应商/渠道、客户 API 基础 |
| 痛点 | 低消、偏远、燃油、渠道账单导入、主子单对账、账本不可变 |

### 阶段 3：增强为完整 SaaS

1. 客户门户。
2. API 凭证和 Webhook。
3. 套餐/功能开关。
4. 审批工作流引擎。
5. 报表中心和经营看板。
6. SLA、退件、保险、提成、POD、问题件闭环。

## 10. 决策建议

最终建议一句话：

> **主框架用现有 Spring Boot 模块化单体，后端引入 Spring Modulith 的模块边界思想；前端和系统管理吸收若依/若依 Plus；ACC、新智慧、财务账本、对账、计费规则全部坚持自研领域模型。**

不建议：

1. 不建议整套切换若依。
2. 不建议现在上 RuoYi-Cloud 或微服务。
3. 不建议用 JEECG/低代码承载核心财务和 TMS 规则。
4. 不建议把新智慧接口作为生产运行时依赖。
5. 不建议把 ACC 表结构照搬成主系统结构。

## 11. 参考链接

| 框架/资料 | 链接 |
| --- | --- |
| RuoYi 官方文档 | https://doc.ruoyi.vip/ |
| RuoYi-Vue3 GitHub | https://github.com/yangzongzhuan/RuoYi-Vue3 |
| RuoYi-Vue-Plus | https://gitee.com/dromara/RuoYi-Vue-Plus |
| JEECG Boot | https://www.jeecg.com/ |
| JHipster | https://www.jhipster.tech/ |
| Spring Modulith | https://spring.io/projects/spring-modulith |
| Flowable | https://www.flowable.com/open-source/docs/ |
| Camunda | https://docs.camunda.io/ |
