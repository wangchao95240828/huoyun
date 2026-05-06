# 主系统数据库表结构设计

版本：2026-05-06

## 设计目标

主系统不是 ACC 或新智慧的壳，也不是长期调用两套旧系统接口的中间层。主系统要拥有自己的业务数据库、流程状态、权限审批、账务闭环和运营页面，并且要同时服务卖货客户方向和制单客户方向。

ACC 和新智慧的作用是：

- ACC：提供已有源码，可反推旧业务逻辑、字段和边界条件。
- 新智慧：提供页面/API 样本，可反推页面、筛选、状态、财务口径。
- 主系统：用 `customer_direction` 承接卖货客户和制单客户两类业务方向，复刻并优化两套系统的核心能力，最终独立运行。

## 总体原则

1. 所有业务表必须有 `tenant_id`，支持 SaaS 多租户隔离。
2. 所有核心业务单据使用 UUID 主键，业务编号单独唯一。
3. 费用、账单、付款、账本分层，不把所有财务数据塞进一张流水表。
4. 运单是履约和财务的核心连接点，箱、轨迹、费用、账单、对账都围绕运单关联。
5. 金额保留原币和本位币能力，正式账本不可修改，只能冲销或调整。
6. 外部系统字段只进入 `external_*` 映射层，不污染主业务模型。
7. 写操作都要有操作日志；越权、改价、核销、审核要走审批和审计。

## 模块分层

| 层级 | 模块 | 核心职责 |
| --- | --- | --- |
| 基础层 | 租户、组织、用户、权限、审批 | 多租户、分公司、角色权限、审批流 |
| 主数据层 | 客户、供应商、承运商、服务、渠道、费用类型 | 统一客户、供应商、服务产品、费用字典 |
| 交易层 | 订单、运单、箱、申报、仓库、批次、轨迹 | 业务履约主流程 |
| 计费层 | 费率、规则、费用行、价格试算 | 应收、应付、销售成本、销售提成 |
| 财务层 | 账单、流水、核销、收付款、账本 | 财务闭环和不可变分录 |
| 对账层 | 供应商账单、账单导入、差异处理 | 新智慧/ACC 痛点里的账单与费用核对 |
| 外部样本层 | 外部系统、外部对象、字段映射 | 复刻过程中的来源追踪和字段对照 |

## 当前已有基础

项目当前迁移已经包含不少底座：

| 已有表组 | 表 |
| --- | --- |
| 多租户/用户 | `tenants`, `users`, `organizations` |
| 客户/渠道/价格 | `customers`, `carriers`, `channels`, `contracts`, `rate_cards`, `rate_card_lines` |
| 费用规则 | `charge_items`, `rule_versions`, `charge_rules`, `fuel_surcharge_rates`, `remote_zones` |
| 运单履约 | `shipments`, `cartons`, `declarations`, `customs_groups`, `customs_group_shipments` |
| 费用/账单 | `charges`, `customer_invoices`, `customer_invoice_lines`, `payments`, `billing_periods` |
| 供应商账单/对账 | `carrier_bill_imports`, `carrier_bill_lines`, `reconciliation_results`, `fee_mapping_dictionary` |
| 不可变账本 | `ledger_accounts`, `ledger_transactions`, `ledger_entries`, `posting_batches` |
| 权限审批 | `roles`, `permissions`, `user_roles`, `role_permissions`, `approval_policies`, `approval_requests`, `approval_decisions` |
| 轨迹/退件/异常 | `tracking_events`, `shipment_milestones`, `return_orders`, `relabel_tasks`, `exception_tickets`, `claim_reviews` |
| 异步/幂等 | `background_jobs`, `background_job_attempts`, `idempotency_keys`, `import_file_fingerprints` |

下面是目标主系统结构，包含已有表的定位，以及建议补齐的表。

## 1. 基础与组织权限

### 1.1 租户与组织

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `tenants` | SaaS 租户 | `code`, `name` |
| `organizations` | 总部、分公司、部门 | `parent_id`, `code`, `name`, `org_type`, `manager_id` |
| `users` | 内部员工账号 | `email`, `display_name`, `role_code`, `branch_id` |
| `roles` | 角色 | `code`, `name`, `system_role` |
| `permissions` | 权限点 | `resource`, `action`, `code` |
| `user_roles` | 用户角色授权 | `user_id`, `role_id`, `scope_type`, `scope_id` |
| `role_permissions` | 角色权限 | `role_id`, `permission_id` |

建议补齐：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `customer_accounts` | 客户门户账号 | `customer_id`, `username`, `password_hash`, `status`, `last_login_at` |
| `api_credentials` | 客户/API 调用凭证 | `owner_type`, `owner_id`, `access_key`, `secret_hash`, `status`, `last_used_at` |
| `user_sessions` | 登录会话 | `user_id`, `session_hash`, `expires_at`, `ip`, `user_agent` |

### 1.2 审批与审计

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `approval_policies` | 审批策略 | `resource`, `action`, `min_approvals`, `condition_json` |
| `approval_requests` | 审批单 | `requester_id`, `resource`, `action`, `target_id`, `status`, `payload` |
| `approval_decisions` | 审批动作 | `request_id`, `approver_id`, `decision`, `comment` |
| `audit_logs` | 操作审计 | `actor_id`, `entity_type`, `entity_id`, `action`, `before_data`, `after_data` |

建议所有高风险操作必须落 `approval_requests` + `audit_logs`：

- 改价、折扣、冲账、作废费用
- 审核/反审核
- 核销/反核销
- 开票/取消开票
- 客户额度调整
- 绕过风险筛查或 SOP

## 2. 主数据

### 2.1 客户与伙伴

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `customers` | 客户主档 | `code`, `name`, `customer_direction`, `account_mode`, `default_currency`, `credit_limit`, `branch_id`, `source_system`, `external_id` |
| `carriers` | 承运商/供应商主档 | `code`, `name`, `carrier_type` |

建议把新智慧中的供应商账单对象统一进 `partners`，不要长期复用 `carriers` 表承载所有供应商语义：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `partners` | 供应商/服务商/代理 | `code`, `name`, `partner_type`, `settlement_currency`, `status` |
| `partner_accounts` | 供应商收付款账户 | `partner_id`, `account_name`, `bank_name`, `currency`, `status` |
| `customer_contacts` | 客户联系人 | `customer_id`, `name`, `phone`, `email`, `role` |
| `customer_settlement_profiles` | 客户结算配置 | `customer_id`, `pay_type`, `credit_days`, `invoice_confirm_required`, `due_actions` |

建议 `customer_direction` 使用：

| 值 | 说明 |
| --- | --- |
| `SELLER_CUSTOMER` | 卖货客户，重点是货品、订单、仓库、发货、账单、利润、售后 |
| `DOCUMENT_CUSTOMER` | 制单客户，重点是 API 下单、制单、面单、轨迹、余额、预扣费 |
| `BOTH` | 同一客户同时存在两类业务方向 |

### 2.2 服务、渠道、费用字典

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `channels` | 运输渠道 | `code`, `name`, `lane`, `last_mile_method`, `primary_uom`, `active` |
| `contracts` | 客户合同 | `customer_id`, `code`, `currency`, `effective_from`, `effective_to`, `status` |
| `charge_items` | 标准费用类型 | `code`, `name`, `category`, `default_side`, `default_uom` |
| `value_added_services` | 增值服务 | `code`, `name`, `charge_item_id`, `default_uom` |

建议补齐：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `services` | 对客户展示的服务产品 | `code`, `name`, `service_type`, `channel_id`, `status` |
| `service_channel_links` | 服务与底层渠道关系 | `service_id`, `channel_id`, `priority`, `effective_from` |
| `tags` | 客户/账单/运单标签 | `scope`, `code`, `name`, `color`, `active` |
| `entity_tags` | 实体标签关联 | `entity_type`, `entity_id`, `tag_id` |

## 3. 订单、运单与箱级履约

### 3.1 订单与运单

建议新增 `orders` 作为客户下单入口，`shipments` 作为履约单。这样可以兼容 ACC 的 Online 订单、新智慧的 shipment/waybill，也能支持一个订单拆成多票运单。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `orders` | 客户订单/预报单 | `order_no`, `customer_id`, `customer_direction`, `order_entry_type`, `service_id`, `status`, `source_system`, `external_id`, `created_by` |
| `order_lines` | 订单明细 | `order_id`, `item_name`, `quantity`, `declared_value`, `metadata` |
| `shipments` | 运单/主业务单 | `shipment_no`, `customer_id`, `customer_direction`, `service_mode`, `contract_id`, `channel_id`, `status`, `customer_ref`, `seller_id`, `servicer_id` |
| `shipment_order_links` | 订单与运单关系 | `order_id`, `shipment_id`, `relation_type` |
| `cartons` | 箱/包裹 | `shipment_id`, `carton_no`, `tracking_no`, `actual_weight_kg`, `chargeable_weight_kg`, `cbm` |
| `declarations` | 报关申报 | `shipment_id`, `item_name`, `hs_code`, `quantity`, `value_amount` |

### 3.2 地址与仓库

建议补齐仓库、地址和入出库表，支撑新智慧 DOS 与 ACC 仓储操作复刻。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `warehouses` | 仓库/站点 | `code`, `name`, `warehouse_type`, `country`, `city`, `status` |
| `addresses` | 地址库 | `owner_type`, `owner_id`, `country`, `province`, `city`, `postal_code`, `address1`, `contact_name`, `phone` |
| `warehouse_receipts` | 收货单 | `receipt_no`, `warehouse_id`, `customer_id`, `status`, `received_at` |
| `warehouse_receipt_items` | 收货明细 | `receipt_id`, `carton_id`, `tracking_no`, `weight_kg`, `scan_status` |
| `picklists` | 拣货单 | `picklist_no`, `warehouse_id`, `status`, `assigned_to` |
| `picklist_items` | 拣货明细 | `picklist_id`, `shipment_id`, `carton_id`, `status` |
| `ladings` | 出货单/装车单 | `lading_no`, `warehouse_id`, `ship_batch_id`, `status`, `loaded_at` |
| `lading_items` | 出货明细 | `lading_id`, `shipment_id`, `carton_id`, `loaded_weight_kg` |
| `pallets` | 托盘 | `pallet_no`, `warehouse_id`, `status`, `weight_kg`, `cbm` |
| `pallet_items` | 托盘明细 | `pallet_id`, `carton_id`, `shipment_id` |
| `scan_events` | 扫描事件 | `warehouse_id`, `shipment_id`, `carton_id`, `scan_type`, `scanned_at`, `operator_id` |

## 4. 批次、柜、提单、轨迹

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `ship_batches` | 出运批次 | `batch_no`, `lane`, `carrier_id`, `origin_port`, `destination_port`, `status` |
| `containers` | 柜 | `container_no`, `container_type`, `seal_no`, `ship_batch_id`, `status` |
| `shipment_batch_links` | 运单装批次/柜 | `shipment_id`, `ship_batch_id`, `container_id`, `loaded_weight_kg`, `loaded_cbm` |
| `bls` | 提单 | `bl_no`, `carrier_id`, `container_no`, `departed_at`, `arrived_at` |
| `bl_shipments` | 提单与运单 | `bl_id`, `shipment_id`, `actual_weight_kg`, `cbm` |
| `shipment_milestones` | 标准里程碑 | `shipment_id`, `carton_id`, `milestone_code`, `occurred_at`, `source` |
| `tracking_events` | 原始轨迹事件 | `tracking_no`, `raw_status`, `normalized_status`, `event_time`, `raw_payload` |
| `tracking_status_mappings` | 轨迹状态映射 | `carrier_id`, `raw_status`, `normalized_status` |

## 5. 计费规则与费用

### 5.1 费率与规则

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `rate_cards` | 价卡头 | `contract_id`, `channel_id`, `side`, `version`, `currency`, `status` |
| `rate_card_lines` | 价卡明细 | `rate_card_id`, `zone_code`, `weight_from`, `weight_to`, `uom`, `unit_price` |
| `rule_versions` | 规则版本 | `code`, `effective_from`, `effective_to`, `status` |
| `charge_rules` | 附加费/低消/燃油等规则 | `charge_item_id`, `side`, `uom`, `unit_price`, `condition_json`, `combination_strategy` |
| `fuel_surcharge_rates` | 燃油月表 | `channel_id`, `year_month`, `rate` |
| `remote_zones` | 偏远/禁运区域 | `channel_id`, `country_code`, `postal_code_pattern`, `level` |

### 5.2 费用行

`charges` 是系统内部计费的唯一标准费用行，不等于正式账本，也不等于客户账单。

| 字段 | 说明 |
| --- | --- |
| `side` | `AR`, `AP`, `SELLER_COST`, `SELLER_COMMISSION` |
| `status` | `DRAFT`, `ESTIMATED`, `LOCKED`, `ADJUSTED`, `VOID` |
| `shipment_id` / `carton_id` | 费用归属 |
| `charge_item_id` | 标准费用类型 |
| `currency`, `quantity`, `unit_price`, `amount`, `tax_amount` | 金额计算 |
| `rule_snapshot`, `evidence` | 计费证据和规则快照 |
| `source`, `external_id` | ACC/XQT/LOCAL 来源追踪 |

建议补齐：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `charge_audit_events` | 费用审核轨迹 | `charge_id`, `audit_type`, `from_status`, `to_status`, `actor_id`, `reason` |
| `charge_adjustments` | 费用调整/冲账申请 | `charge_id`, `adjustment_type`, `amount_delta`, `reason`, `approval_request_id` |
| `shipment_charge_snapshots` | 运单审计页面快照 | `shipment_id`, `ar_total`, `ap_total`, `seller_cost_total`, `commission_total`, `gross_profit` |

## 6. 财务账单、流水、核销

### 6.1 客户应收

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `customer_invoices` | 客户账单 | `invoice_no`, `customer_id`, `currency`, `total_amount`, `status`, `issued_at`, `branch_id` |
| `customer_invoice_lines` | 客户账单明细 | `invoice_id`, `shipment_id`, `charge_id`, `amount` |
| `payments` | 客户收款 | `customer_id`, `currency`, `amount`, `received_at`, `reference_no` |

建议扩展 `customer_invoices` 字段：

| 字段 | 说明 |
| --- | --- |
| `invoice_date` | 账单日期 |
| `due_date` | 到期日 |
| `confirmed_at` | 客户确认时间 |
| `paid_amount` | 已核销金额 |
| `unpaid_amount` | 未核销金额 |
| `tax_status` | 开票状态 |
| `writeoff_status` | 核销状态 |
| `source`, `external_id` | 来源追踪 |

建议新增：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `receivable_settlements` | 应收核销单 | `settlement_no`, `customer_id`, `payment_id`, `status`, `settled_amount` |
| `receivable_settlement_lines` | 应收核销明细 | `settlement_id`, `invoice_id`, `charge_id`, `amount` |
| `tax_invoices` | 发票 | `tax_invoice_no`, `customer_id`, `invoice_id`, `currency`, `amount`, `status`, `issued_at` |

### 6.2 供应商应付

当前已有 `carrier_bill_imports`、`carrier_bill_lines` 更偏“导入文件与对账行”。主系统还需要正式的供应商账单模型。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `partner_invoices` | 供应商账单 | `invoice_no`, `partner_id`, `currency`, `total_amount`, `status`, `invoice_date`, `due_date`, `paid_amount` |
| `partner_invoice_lines` | 供应商账单明细 | `invoice_id`, `shipment_id`, `charge_id`, `amount`, `source_bill_line_id` |
| `partner_payments` | 供应商付款 | `partner_id`, `currency`, `amount`, `paid_at`, `reference_no`, `status` |
| `payable_settlements` | 应付核销单 | `settlement_no`, `partner_id`, `payment_id`, `status`, `settled_amount` |
| `payable_settlement_lines` | 应付核销明细 | `settlement_id`, `partner_invoice_id`, `charge_id`, `amount` |

### 6.3 财务流水与账户

新智慧的“财务流水、账户、账户流水”不应直接替代账本。建议拆成资金账户流水和正式账本两层。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `financial_accounts` | 公司/客户/供应商/员工账户 | `owner_type`, `owner_id`, `account_name`, `currency`, `account_type`, `status` |
| `financial_account_records` | 账户流水 | `account_id`, `direction`, `amount`, `currency`, `payment_type`, `business_time`, `source_type`, `source_id` |
| `ledger_accounts` | 会计科目 | `code`, `name`, `account_type`, `normal_balance` |
| `ledger_transactions` | 会计凭证 | `transaction_no`, `source_type`, `source_id`, `status`, `effective_at` |
| `ledger_entries` | 复式分录 | `transaction_id`, `account_id`, `direction`, `currency`, `amount` |

### 6.4 账期锁定与汇率

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `billing_periods` | 月结锁定 | `period_start`, `period_end`, `locked_ar`, `locked_ap`, `locked_seller` |
| `exchange_rates` | 汇率 | `rate_date`, `from_currency`, `to_currency`, `rate`, `rate_type`, `purpose` |

## 7. 对账、成本分摊、利润与提成

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `carrier_bill_imports` | 渠道账单导入批次 | `carrier_id`, `template_code`, `file_name`, `status` |
| `carrier_bill_lines` | 渠道账单原始行 | `shipment_no`, `tracking_no`, `freight_amount`, `surcharge_amount`, `raw_data` |
| `reconciliation_results` | 对账结果 | `bill_line_id`, `shipment_id`, `expected_charge_id`, `status`, `difference_type`, `confidence` |
| `cost_allocation_batches` | 成本分摊批次 | `bl_id`, `allocation_method`, `source_amount`, `status` |
| `cost_allocation_lines` | 成本分摊明细 | `batch_id`, `shipment_id`, `allocated_amount`, `currency` |
| `commission_plans` | 提成方案 | `basis`, `rate`, `effective_from` |
| `commission_runs` | 提成月结 | `period_month`, `status`, `plan_id` |
| `commission_lines` | 提成明细 | `run_id`, `salesperson_id`, `shipment_id`, `basis_amount`, `commission_amount` |

建议补齐：

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `profit_snapshots` | 利润快照 | `shipment_id`, `ar_amount`, `ap_amount`, `seller_cost_amount`, `gross_profit`, `snapshot_at` |
| `reconciliation_cases` | 对账异常处理单 | `case_no`, `result_id`, `owner_id`, `status`, `resolution_type`, `resolved_at` |

## 8. 风险、退件、异常与增值服务

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `prohibited_items` | 禁运/敏感品规则 | `keyword_pattern`, `country_code`, `channel_id`, `risk_level` |
| `risk_screening_results` | 风险筛查结果 | `shipment_id`, `risk_type`, `risk_level`, `decision` |
| `shipment_relations` | 运单关系 | `parent_shipment_id`, `child_shipment_id`, `relation_type` |
| `return_orders` | 退件单 | `return_no`, `original_shipment_id`, `status`, `reason` |
| `return_order_cartons` | 退件箱 | `return_order_id`, `carton_id`, `action`, `new_tracking_no` |
| `relabel_tasks` | 二次制单任务 | `return_order_id`, `original_carton_id`, `new_shipment_id`, `status` |
| `service_orders` | 增值服务单 | `service_order_no`, `shipment_id`, `service_id`, `status`, `quantity` |
| `pod_requests` | POD 申请 | `request_no`, `shipment_id`, `pod_type`, `status`, `file_url` |
| `exception_tickets` | 问题件工单 | `ticket_no`, `shipment_id`, `ticket_type`, `status`, `severity` |
| `claim_reviews` | 理赔/赔付审核 | `claim_no`, `shipment_id`, `requested_amount`, `approved_amount`, `status` |

## 9. 外部系统样本与字段映射

这层用于复刻和对照，不作为主业务依赖。它能解决“我们如何证明新系统字段来自哪里、是否对齐 ACC/新智慧”的问题。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `external_systems` | 外部系统登记 | `code`, `name`, `system_type`, `base_url`, `status` |
| `external_modules` | 外部模块/页面 | `external_system_id`, `module_code`, `page_path`, `api_path`, `method` |
| `external_field_mappings` | 字段映射 | `external_module_id`, `external_field`, `internal_table`, `internal_field`, `transform_rule` |
| `external_object_refs` | 主对象外部引用 | `entity_type`, `entity_id`, `external_system_id`, `external_id`, `external_no` |
| `external_payload_snapshots` | 只读样本快照 | `external_module_id`, `request_body`, `response_schema`, `sample_payload`, `captured_at` |
| `comparison_cases` | 人工对照用例 | `case_no`, `external_module_id`, `internal_url`, `external_filter`, `expected_result`, `actual_result`, `status` |

注意：`external_payload_snapshots.sample_payload` 只能保存脱敏样本，不保存账号、密码、Cookie、客户隐私明文。

## 10. 核心关系图

```text
tenant
  ├─ organizations ─ users ─ roles/permissions
  ├─ customers ─ contracts ─ rate_cards
  ├─ partners/carriers ─ channels/services
  ├─ orders ─ shipments ─ cartons ─ tracking_events
  │              ├─ declarations
  │              ├─ charges
  │              ├─ customer_invoice_lines ─ customer_invoices
  │              ├─ partner_invoice_lines ─ partner_invoices
  │              ├─ reconciliation_results ─ carrier_bill_lines
  │              └─ ledger_entries ─ ledger_transactions
  ├─ warehouses ─ receipts/picklists/ladings/pallets
  ├─ ship_batches ─ containers ─ shipment_batch_links
  └─ external_systems ─ external_modules ─ external_field_mappings
```

## 11. P0 必须落地的表

本周/第一阶段建议优先确认这些表：

| 优先级 | 表 | 原因 |
| --- | --- | --- |
| P0 | `customers`, `partners`, `users`, `organizations` | 所有业务归属 |
| P0 | `services`, `channels`, `charge_items` | 新智慧服务、费用类型、渠道复刻 |
| P0 | `orders`, `shipments`, `cartons` | ACC 与新智慧的运单主线 |
| P0 | `charges`, `charge_audit_events` | 运单审计、客户流水、供应商流水核心 |
| P0 | `customer_invoices`, `customer_invoice_lines` | 客户账单复刻 |
| P0 | `partner_invoices`, `partner_invoice_lines` | 供应商账单复刻 |
| P0 | `financial_accounts`, `financial_account_records` | 财务流水和账户流水复刻 |
| P0 | `billing_periods`, `exchange_rates` | 月结锁定、汇率 |
| P0 | `ledger_*` | 正式账本底座 |
| P0 | `external_*`, `comparison_cases` | 对照新智慧/ACC 的复刻证据 |

## 12. 建议新增迁移顺序

### 008 主数据补齐

- `partners`
- `partner_accounts`
- `services`
- `service_channel_links`
- `tags`
- `entity_tags`
- `customer_contacts`
- `customer_settlement_profiles`

### 009 订单与仓配

- `orders`
- `order_lines`
- `shipment_order_links`
- `warehouses`
- `addresses`
- `warehouse_receipts`
- `warehouse_receipt_items`
- `picklists`
- `picklist_items`
- `ladings`
- `lading_items`
- `pallets`
- `pallet_items`
- `scan_events`

### 010 财务复刻核心

- `partner_invoices`
- `partner_invoice_lines`
- `partner_payments`
- `receivable_settlements`
- `receivable_settlement_lines`
- `payable_settlements`
- `payable_settlement_lines`
- `tax_invoices`
- `financial_accounts`
- `financial_account_records`
- `charge_audit_events`
- `charge_adjustments`
- `shipment_charge_snapshots`
- `profit_snapshots`

### 011 外部样本与对照

- `external_systems`
- `external_modules`
- `external_field_mappings`
- `external_object_refs`
- `external_payload_snapshots`
- `comparison_cases`

## 13. 与新智慧财务模块映射

| 新智慧页面 | 主系统表 |
| --- | --- |
| 财务流水 `financial_detail` | `financial_account_records`, `ledger_transactions`, `ledger_entries` |
| 运单审计 `shipment` | `shipments`, `charges`, `shipment_charge_snapshots`, `charge_audit_events` |
| 应收报表 `user_report` | `customer_invoices`, `customer_invoice_lines`, `receivable_settlements` |
| 应付报表 `partner_report` | `partner_invoices`, `partner_invoice_lines`, `payable_settlements` |
| 运价维护 `rates` | `rate_cards`, `rate_card_lines`, `charge_rules` |
| 客户流水 `invoice_detail` | `charges` where `side = 'AR'`, `customer_invoice_lines` |
| 客户账单 `invoice` | `customer_invoices`, `customer_invoice_lines` |
| 供应商流水 `detail_partner` | `charges` where `side = 'AP'`, `partner_invoice_lines` |
| 供应商账单 `invoice_partner` | `partner_invoices`, `partner_invoice_lines` |
| 销售成本流水 `detail_seller` | `charges` where `side = 'SELLER_COST'` |
| 销售提成流水 `detail_seller_commission` | `charges` where `side = 'SELLER_COMMISSION'`, `commission_lines` |
| 账户 `financial_account` | `financial_accounts` |
| 账户流水 `financial_account_record` | `financial_account_records` |
| 汇率 `currency` | `exchange_rates` |
| 费用类型 `charge_type_mod` | `charge_items` |
| 月结单 `lock_invoice_time` | `billing_periods` |
| 费用审批 `charge_approval` | `approval_requests`, `approval_decisions`, `charge_adjustments` |
| 审批 `approval` | `approval_requests`, `approval_decisions` |

## 14. 与 ACC 业务映射

| ACC 概念 | 主系统表 |
| --- | --- |
| `Customer` | `customers`, `customer_contacts`, `customer_settlement_profiles` |
| `Customer_API` | `api_credentials`, `customer_accounts` |
| `Online` 预报/订单 | `orders`, `order_lines`, `shipments` |
| `Express` 运单 | `shipments` |
| `Express_Item` / 包裹 | `cartons` |
| `Online_Package*` | `cartons`, `warehouse_receipts`, `scan_events` |
| `Express_Charge` | `charges` |
| `Customer_Balance` | `financial_accounts`, `financial_account_records`, `ledger_entries` |
| `Express_TrackNo` / `Online_TrackNo` | `tracking_events`, `shipment_milestones` |
| `Express_Status` / `Express_Process` | `shipment_milestones`, `audit_logs` |
| `Stowage` | `ship_batches`, `containers`, `shipment_batch_links` |

## 15. 下一步建议

1. 先评审本文档中的 P0 表，确认业务命名和字段口径。
2. 把当前 `carrier_*` 供应商账单概念拆清楚：导入账单行 vs 正式供应商账单。
3. 新增 `partners`，避免承运商、供应商、代理、服务商全部挤在 `carriers`。
4. 新增 `orders`，避免客户预报单和履约运单都塞进 `shipments`。
5. 新增 `financial_accounts` 和 `financial_account_records`，复刻新智慧账户/流水，同时保持 `ledger_*` 作为正式账本。
6. P0 migration 只做结构和索引，不急着实现所有写流程。
