# 全功能模块设计

## 目标

本设计把所有已识别痛点纳入模块边界，确保后续研发不是只围绕财务表开发，而是可以覆盖计费、对账、时效、轨迹、退件、增值服务、保险、问题件、提成和旧系统迁移的完整 SaaS 功能。

## 模块总览

| 模块 | 解决痛点 | 核心表 |
| --- | --- | --- |
| 租户与权限中心 | 多租户隔离、对象级权限、绕 SOP 审批 | `tenants`, `users`, `roles`, `permissions`, `approval_*`, `audit_logs` |
| 客户/合同/渠道主数据 | 客户价、渠道成本价、两种收费模式 | `customers`, `contracts`, `carriers`, `channels`, `rate_cards` |
| 费率与规则引擎 | 敏感品、低消、分抛、燃油、偏远、附加费叠加/取大 | `charge_rules`, `rule_versions`, `fuel_surcharge_rates`, `remote_zones`, `charge_items` |
| 订单/运单/箱级履约 | 主单、子单、箱级重量体积、两种收费模式 | `shipments`, `cartons`, `declarations` |
| 风险筛查 | 反倾销、禁运、品名黑名单、覆盖审批 | `prohibited_items`, `risk_screening_results`, `approval_requests` |
| 报关合并 | 合并报关只收一次、均摊/挂主票 | `customs_groups`, `customs_group_shipments`, `charges` |
| 派送比价与成本预估 | 卡派转快递风险、LTL/快递双轨报价 | `delivery_quote_options`, `rate_cards`, `channel_cost_policies` |
| 保险 | 投保 API、电子保单、保费自动同步 | `insurance_policies`, `insurance_events`, `charges`, `background_jobs` |
| 应收账单 | 客户账单、模板、版本、多币种展示 | `customer_invoices`, `customer_invoice_lines`, `invoice_templates` |
| 渠道成本与自动对账 | 渠道账单导入、字段映射、科目化、主/子单匹配、补收 | `carrier_bill_imports`, `carrier_bill_lines`, `fee_mapping_dictionary`, `reconciliation_results` |
| 不可变账本 | 正式过账、复式分录、冲销调整、汇兑损益 | `ledger_accounts`, `ledger_transactions`, `ledger_entries`, `posting_batches` |
| BL/柜/批次成本分摊 | 多柜多批次归集、按重量/方数分摊 | `ship_batches`, `containers`, `shipment_batch_links`, `bls`, `bl_shipments`, `cost_allocation_batches`, `cost_allocation_lines` |
| 时效与 SLA | 起止点配置、中期时效、内部赔付参考 | `shipment_milestones`, `sla_rules`, `sla_results`, `compensation_rules` |
| 轨迹中台 | 主/子单轨迹、状态映射、接口/脚本兜底 | `tracking_events`, `tracking_status_mappings`, `background_jobs` |
| 退件与二次制单 | 原主单不断链、子单重派、二次制单 | `shipment_relations`, `return_orders`, `return_order_cartons`, `relabel_tasks` |
| 增值服务/POD/问题件 | 海外仓服务、POD 申请、异常工单、理赔 | `value_added_services`, `service_orders`, `pod_requests`, `exception_tickets`, `claim_reviews` |
| 多币种与汇率 | 合同汇率、记账汇率、原币/本位币 | `exchange_rates`, `contracts`, `payments`, `ledger_entries` |
| 销售提成 | 提成方案、月度汇总、冲回调整 | `commission_plans`, `commission_runs`, `commission_lines` |
| 异步任务与幂等 | 批量导入、后台计算、保险回调、通知 | `background_jobs`, `background_job_attempts`, `idempotency_keys`, `import_file_fingerprints` |

## 核心流程

### 1. 下单到费用试算

1. 客户或操作创建 `shipments`、`cartons`、`declarations`。
2. 风险筛查模块生成 `risk_screening_results`，禁运直接拦截，允许有权限角色覆盖。
3. 规则引擎根据合同、渠道、箱级数据、品名和邮编生成 AR/AP `charges`。
4. 快递/LTL 比价写入 `delivery_quote_options`，超长/超重必须确认风险后才能继续。

### 2. 出运到时效

1. 操作建立 `ship_batches`、`containers`，通过 `shipment_batch_links` 关联运单。
2. 系统或人工录入 `shipment_milestones`。
3. `sla_rules` 计算 `sla_results`，客户侧只展示实际天数，赔付金额只进入内部 `claim_reviews`。

### 3. 轨迹与退件

1. 承运商 API/EDI/导入任务写入 `tracking_events`。
2. `tracking_status_mappings` 归一为标准状态。
3. 退件时创建 `return_orders`，重派时用 `shipment_relations` 关联原单和新单，避免客户主线断裂。

### 4. 成本导入与对账

1. 渠道账单上传生成 `carrier_bill_imports` 和后台任务。
2. 解析后的每行进入 `carrier_bill_lines`，保留原始 JSON。
3. `fee_mapping_dictionary` 将英文/渠道费用名映射到标准 `charge_items`。
4. 对账引擎按主单、子单、客户参考号、日期窗口、金额容差匹配，写入 `reconciliation_results`。
5. 差异进入异常队列，处理状态包括待确认、申诉中、已调整。

### 5. 账单、收款、过账

1. 应收费用锁定后生成 `customer_invoices`。
2. `invoice_templates` 控制总金额、差价、多渠道汇总、双币种模板。
3. 收款进入 `payments`，汇率来自 `exchange_rates`。
4. 正式入账写入 `ledger_transactions` 和 `ledger_entries`，已过账数据不可修改，只能冲销或调整。

### 6. 提成

1. 月结创建 `commission_runs`。
2. 按 `commission_plans` 读取应收、销售成本、实际成本和回款状态。
3. 生成 `commission_lines`，财务主管复核后确认。

## 接口边界建议

- `/api/rates/*`: 价卡、规则版本、试算。
- `/api/shipments/*`: 运单、箱、申报、里程碑。
- `/api/risk-screening/*`: 禁运/反倾销筛查。
- `/api/delivery-quotes/*`: 快递/LTL 比价和风险确认。
- `/api/insurance/*`: 投保、保单回调、保费入账。
- `/api/invoices/*`: 客户账单、模板、导出。
- `/api/carrier-bills/*`: 成本账单导入、字段映射。
- `/api/reconciliation/*`: 自动匹配、差异处理。
- `/api/ledger/*`: 过账、冲销、分录查询。
- `/api/sla/*`: SLA 规则、时效结果、内部赔付参考。
- `/api/tracking/*`: 轨迹事件、状态映射。
- `/api/returns/*`: 退件、二次制单。
- `/api/service-orders/*`: 增值服务、POD、问题件。
- `/api/commissions/*`: 提成月结。
- `/api/jobs/*`: 后台任务、导入进度。

## MVP 到全量的交付顺序

P0 财务闭环：
计费规则、箱级低消、偏远三档、应收账单、渠道账单导入、自动对账、不可变账本、RLS、权限审批。

P1 运营闭环：
批次/柜、时效/SLA、轨迹中台、退件二次制单、派送比价、保险 API。

P2 增强能力：
客户门户、账单模板市场、POD、海外仓增值服务、问题件工单、提成月结、旧系统双写。
