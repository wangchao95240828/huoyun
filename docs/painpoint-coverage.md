# 痛点覆盖矩阵

盘点日期：2026-04-27

## 结论

当前已经把所有已识别痛点纳入模块设计和数据库模型。需要注意：这表示“系统模型已覆盖”，不等于所有 API、页面、算法和外部集成都已实现。

- 财务 MVP 主链路已纳入架构和数据库：计费规则、应收、应付、渠道账单导入、自动对账、保险、成本分摊、利润、提成、RLS、账本、异步任务、权限审批。
- 运营域痛点已补模型：批次/柜、时效/SLA、轨迹、退件二次制单、海外仓增值服务、POD、问题件、赔付内部参考。
- 剩余工作是服务/API、前端页面、算法和外部系统 adapter 的实现。

## 覆盖状态说明

- `已覆盖`: 数据模型和架构均有明确落点，可进入服务/API 实现。
- `部分覆盖`: 有基础字段或可复用底座，但缺专门对象、状态机或业务表。
- `模型已补`: 通过 `006_full_painpoint_modules.sql` 已补足数据库对象，等待 API/页面实现。

## A. 计费引擎类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| A1 | 敏感品 / 产品词附加费 | 已覆盖 | `charge_rules.condition_json`, `rule_versions`, `declarations`, `charges.evidence` | 同义词词库维护 UI、命中服务 |
| A2 | 附加费叠加 vs 取大 | 已覆盖 | `combination_group`, `combination_strategy`, `scope`, `priority` | 规则执行器 |
| A3 | 尺寸附加费识别即全收 | 已覆盖 | `cartons` 尺寸字段、`oversize_flags`, `charge_rules` | 超尺寸计算服务 |
| A4 | 按箱最低计费重 / 单箱低消 | 已覆盖 | `channels.min_weight_per_carton`, `cartons.chargeable_weight_*`, `rate_card_lines` | 法国 UPS 24kg 组合型低消公式配置 |
| A5 | 偏远三档 | 已覆盖 | `remote_zones.level`, `shipments.remote_level` | 地址标准化、偏远命中服务 |
| A6 | kg/lb/CBM/件共存 | 已覆盖 | `billing_uom`, `rate_card_lines.uom`, `charge_items.default_uom` | 单位换算服务 |
| A7 | 分抛规则多版本 | 已覆盖 | `channels.dim_factor`, `channels.dim_split_ratio` | 公式版本化更细粒度配置 |
| A8 | 燃油月度/版本 | 已覆盖 | `fuel_surcharge_rates` | 官网抓取或手工录入流程 |
| A9 | 保险独立计费项 | 已覆盖 | `insurance_policies`, `insurance_events`, `charge_items.INSURANCE` | 保险 API adapter、理赔流程 |
| A10 | 合并报关重复计费 | 已覆盖 | `customs_groups`, `customs_group_shipments` | 均摊生成费用服务 |
| A11 | 一票一件 / 单票单件 | 已覆盖 | `charge_rules.condition_json`, `cartons` | 规则条件表达式标准化 |
| A12 | 反倾销 / 禁运黑名单 | 已覆盖 | `prohibited_items`, `risk_screening_results`, `remote_zones.EMBARGO`, `approval_policies` | 筛查服务 |

## B. 对账与账单类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| B1 | 渠道账单格式差异 | 已覆盖 | `carrier_bill_imports`, `carrier_bill_lines`, `import_file_fingerprints` | 模板定义表、列映射 UI |
| B2 | 主/子单、补收匹配不稳 | 已覆盖 | `shipments`, `cartons.tracking_no`, `carrier_master_tracking_no`, `reconciliation_results.confidence` | 匹配算法 |
| B3 | 原始账单直传解析 | 部分覆盖 | `background_jobs`, `carrier_bill_imports`, `carrier_bill_lines` | OCR/大模型解析管道、附件表 |
| B4 | 费用关键字科目化 | 已覆盖 | `fee_mapping_dictionary`, `charge_items`, `carrier_bill_lines.fee_description` | 科目映射 UI |
| B5 | 客户账单生成发送 | 部分覆盖 | `customer_invoices`, `customer_invoice_lines`, `invoice_templates` | 账单文件、邮件任务、客户门户 |
| B6 | 客户账单样式多模板 | 已覆盖 | `invoice_templates`, `customer_invoices.template_code` | 模板设计器 |
| B7 | 成本导入需手工整理 | 已覆盖 | `carrier_bill_imports`, `carrier_bill_lines`, `reconciliation_results`, `background_jobs` | 模板配置服务 |

## C. 时效与轨迹类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| C1 | 时效起止点写死 | 已覆盖 | `shipment_milestones`, `sla_rules`, `sla_results` | SLA 计算服务 |
| C2 | 关键时间点来源分散 | 已覆盖 | `shipment_milestones.source`, `responsible_role`, `background_jobs` | 录入 UI 和接口 adapter |
| C3 | 中期时效未展示 | 已覆盖 | `sla_rules`, `sla_results`, `shipment_milestones` | 分段展示 API |
| C4 | 自动赔付需内部参考 | 已覆盖 | `compensation_rules`, `claim_reviews` | 内部审核流程 |
| C5 | 子单轨迹状态粗 | 已覆盖 | `cartons.tracking_no`, `tracking_events`, `tracking_status_mappings` | 承运商 API adapter |
| C6 | PS 抓取脆弱 | 已覆盖 | `background_jobs`, `tracking_events` | API 优先、脚本兜底策略 |

## D. 单号生命周期 / 退件类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| D1 | 退件二次制单割裂 | 已覆盖 | `shipment_relations`, `return_orders`, `return_order_cartons`, `relabel_tasks` | 二次制单服务 |
| D2 | 两套现有系统能力不对齐 | 部分覆盖 | API/数据库可接入 | 需旧系统字段字典、同步策略、双写任务 |

## E. 预估 vs 实际成本类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| E1 | 渠道报价表未进系统 | 已覆盖 | `rate_cards.side=AP`, `channel_cost_policies`, `delivery_quote_options` | 成本价卡导入 |
| E2 | 预估 vs 实际差异告警 | 部分覆盖 | `reconciliation_results`, `background_jobs` | 阈值规则表、通知任务 |

## F. 成本分摊类

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| F1 | BL/柜成本不能批量分摊 | 已覆盖 | `bls`, `bl_shipments`, `cost_allocation_batches`, `cost_allocation_lines` | 分摊服务 |
| F2 | 多柜/多批次字段不齐 | 已覆盖 | `ship_batches`, `containers`, `shipment_batch_links`, `bls`, `bl_shipments` | 批次/柜操作 UI |

## G. 财务 / 多币种

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| G1 | 报价外币 vs 结汇人民币 | 已覆盖 | `exchange_rates`, `contracts.exchange_formula`, `charges.currency`, `payments.exchange_rate`, `ledger_entries.currency` | 汇率来源 adapter |
| G2 | 展示与记账口径混淆 | 部分覆盖 | `customer_invoices`, `payments`, `ledger_entries` | 明确账单展示金额、记账金额、汇兑损益分录 |

## H. 销售提成

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| H1 | 提成靠 Excel | 已覆盖 | `commission_plans`, `commission_runs`, `commission_lines`, `ledger_entries`, `charges` | 提成计算服务 |

## I. 组织效率 / 平台能力

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| I1 | 缺批量操作与后台异步 | 已覆盖 | `background_jobs`, `background_job_attempts`, `approval_*`, `audit_logs` | 批量模板表、通知 |
| I2 | 与旧商业系统差异 | 部分覆盖 | `background_jobs`, `audit_logs` | 旧系统 adapter、双写/同步日志 |
| I3 | 两种收费模式同系统同看板 | 已覆盖 | `shipments.service_mode`, 财务统一表 | 不同模式计费节点状态机 |

## J. 附加功能

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| J1 | 海外仓增值服务 | 已覆盖 | `value_added_services`, `service_orders`, `charge_items`, `charge_rules` | 服务单页面 |
| J2 | 旺季附加费 | 已覆盖 | `rule_versions.effective_from/to`, `charge_rules` | 时间窗 UI |
| J3 | POD 申请 | 已覆盖 | `pod_requests` | 附件存储 adapter |
| J4 | 异常问题件工单 | 已覆盖 | `exception_tickets`, `claim_reviews`, `approval_requests` | 工单工作流 |

## 四项补充痛点

| 编号 | 痛点 | 状态 | 当前落点 | 仍需补强 |
| --- | --- | --- | --- | --- |
| 补充 1 | 卡派转快递异常尺寸/重量预警及比价 | 已覆盖 | `delivery_quote_options`, `cartons`, `charge_rules`, `approval_*` | 风险弹窗和比价服务 |
| 补充 2 | 拆柜/派送卡派报价集成 | 已覆盖 | `rate_cards.side=AP`, `channels.last_mile_method`, `delivery_quote_options` | 2-3 家代理比价 UI |
| 补充 3 | 保险 API 对接与保费同步 | 已覆盖 | `insurance_policies`, `insurance_events`, `charges`, `background_jobs` | 保险公司 adapter |
| 补充 4 | 成本规则前置录入和自动对账 | 已覆盖 | `channel_cost_policies`, `charges`, `carrier_bill_lines`, `reconciliation_results` | 差异报告导出 |

## 已补进数据库对象

以下对象已通过 `006_full_painpoint_modules.sql` 补入：

1. `ship_batches`, `containers`: 多柜/多批次、时效、成本分摊中间层。
2. `shipment_milestones`, `sla_rules`, `sla_results`: 时效起止点和多段展示。
3. `tracking_events`, `tracking_status_mappings`: 主/子单轨迹中台。
4. `shipment_relations`, `return_orders`, `relabel_tasks`: 退件二次制单。
5. `fee_mapping_dictionary`, `invoice_templates`: 账单科目化和客户账单模板市场。
6. `exchange_rates`: 合同汇率、记账汇率、汇兑损益。
7. `commission_runs`, `commission_lines`: 月度提成核算。
8. `value_added_services`, `pod_requests`, `exception_tickets`, `claim_reviews`: 增值服务、POD、问题件和理赔。
9. `prohibited_items`, `risk_screening_results`: 反倾销/禁运黑名单和筛查证据。
