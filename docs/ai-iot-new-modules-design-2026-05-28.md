# AI 与仓库 IoT 新模块设计

生成日期：2026-05-28  
仓库：`/Users/chaowang/新航线/xqt-saas`
最近更新：2026-05-29（按 ACC 迁移完成后的最新代码状态修订）

## 0. 最新代码基线（2026-05-29）

当前系统已经不再是“只具备基础 CRUD / 规划中”的状态，ACC 旧系统核心业务逻辑已经基本迁移到新 Spring Boot + Vue 平台：

- 后端测试：最新交付记录为 **202/202 通过**（含 7 项 E2E 异常处理回归测试）。
- 数据库 migration：已到 `047_branch_visibility_fallback.sql`，生产部署需应用 `001-047` 全量脚本。
- ACC 主业务：订单、报价、Submit、渠道取号、面单、费用、账单、收付款、资金流水、审核副作用、利润、第三方推单 `sumy.php`、电子秤 `Scale.php`、轨迹聚合、配载/转运/派送联动均已接入主路径。
- 生产部署：已补 `apps/backend/Dockerfile`、`apps/web/Dockerfile`、`deploy/docker-compose.prod.yml`、`deploy/deploy.sh`；生产 compose 默认开启 `RATES_STRICT_QUOTE=true` 与 `APP_CARRIER_STRICT_GATEWAY=true`。
- 权限隔离：`AuthPrincipal.branchId`、JWT、`RequestContext`、RLS 已打通；`users.branch_id` / `customers.branch_id` 需要由运营补齐。
- 面单格式：ZPL / PNG / JPG 转 PDF 已接入 `LabelService`；复杂 ZPL 完美还原建议生产接 Labelary 或真实 label provider。

因此，本说明书中的 AI / 仓库 IoT 模块应定位为 **在 ACC 业务闭环之上的增强模块**，不是主业务迁移的前置条件。

### 0.1 当前必须配置的账号和基础资料

为了把一条真实业务跑通，至少需要：

| 类别 | 必填内容 | 用途 |
| --- | --- | --- |
| 租户与后台用户 | `tenants`、`users`、角色、`users.branch_id` | 登录、审核、分公司/销售权限 |
| 客户资料 | `customers`、`customer_group_id`、`salesman_user_id`、`branch_id` | 下单、权限、客户价/组价 |
| 客户资金账户 | `financial_accounts(owner_type='CUSTOMER')`、余额、币种 | Submit 预扣、收款、退款、调账 |
| 渠道账号 | `channels`、`acc_channel_accounts.provider_code`、API key/secret/endpoint | 取号、面单、渠道限额 |
| 供应商账号 | `partners`、供应商资金账户 | AP 成本、供应商账单、付款 |
| 价卡 | `rate_cards`、`rate_card_lines`、客户价/组价、偏远/限制/关键词规则 | 报价、Submit 扣款、AR/AP 拆行 |
| 财务基础 | 公司银行账户、`exchange_rates`、`tenants.base_currency` | 汇率快照、账单核销、利润 |
| 设备账号 | `scale_devices(plugin_code,hid)` | 电子秤称重接口 |

演示环境可用 `provider_code=SANDBOX` 跑通；生产环境必须配置真实 UPS/FedEx/label provider 凭证。

## 1. 总体结论

在最新 ACC 迁移完成后，后续建议新增 4 个增强业务模块：

| 模块 | 建议后端包 | 建议 API 前缀 | 优先级 |
| --- | --- | --- | --- |
| 智能客服 | `ai.support` | `/api/ai/customer-service/*` | P1 |
| 客户发票识别 | `ai.invoicerecognition` | `/api/document/invoice-recognition/*` | P1 |
| 3D 智能装箱 | `packing3d` | `/api/packing/3d/*` | P1 |
| 仓库 IoT 推送 | `iot.warehouse` | `/api/iot/warehouse/parcel` | P0（业务已决定暂缓，不阻塞 ACC 主路径上线） |

原则：

1. 内部后台接口继续使用系统 Bearer Token。
2. 客户 API 继续走 `/api/customer-api/*`。
3. 外部仓库设备推送不走 Bearer Token，使用设备签名、时间戳、防重放和 IP 白名单。
4. AI 模块不要直接把 LLM 输出写入核心业务表，先写草稿/建议，再由用户确认。
5. IoT 推送接口必须幂等，设备重复推送同一个箱号不能重复入库、重复扣费或重复打印。
6. 所有新增模块必须复用现有 ACC 主链路：`RateEngine`、`LabelService`、`TrackingAggregator`、`StowageStateMachine`、`balance_ledger`、`shipment_order_links`，不要另起一套平行逻辑。

## 2. 智能客服模块

### 2.1 目标

智能客服可以自动搜索客户订单、运单轨迹、包裹状态、费用状态，并生成可直接回复客户的答案。

典型问题：

- “我的单号 10000001 到哪里了？”
- “这个包裹为什么还没上网？”
- “客户说少一箱，帮我查一下。”
- “这票有没有欠费或者异常？”

### 2.2 数据来源

优先使用新系统内部数据：

- `orders`
- `shipments`
- `packages` / `cartons`
- `tracking_events`
- `TrackingAggregator` 聚合后的内部/客户时间线
- `charges`
- `balance_ledger`
- `shipment_order_links`
- `customer_invoices`
- `publictracking`
- `customerapi` 订单查询结果

最新代码已提供多源轨迹聚合和客户/内部视角分层，智能客服不应直接拼 SQL 查多张轨迹表，优先调用已有受控服务。

不要让 LLM 直接查库。建议新增 `CustomerServiceSearchService`，只暴露受控查询工具。

### 2.3 建议接口

#### 后台客服聊天

```http
POST /api/ai/customer-service/chat
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "customerId": "uuid",
  "question": "客户问 10000001 到哪里了",
  "context": {
    "orderNo": "10000001",
    "trackingNo": "",
    "language": "zh-CN"
  }
}
```

返回：

```json
{
  "ok": true,
  "data": {
    "answer": "这票当前已完成拣货，最新轨迹是...",
    "confidence": 0.86,
    "citations": [
      {
        "type": "tracking_event",
        "id": "uuid",
        "summary": "2026-05-28 10:31 已到达仓库"
      }
    ],
    "suggestedActions": [
      {
        "type": "open_order",
        "label": "查看订单",
        "target": "10000001"
      }
    ]
  },
  "error": null,
  "errorCode": null
}
```

#### 客户自助问答

```http
POST /api/customer-api/support/chat
Authorization: Bearer <customer-api-token>
Content-Type: application/json
```

客户自助问答必须限制在当前客户自己的订单范围内，不能跨客户查询。

### 2.4 落库建议

新增表：

- `ai_conversations`
- `ai_messages`
- `ai_tool_calls`
- `ai_answer_feedback`

关键字段：

- `tenant_id`
- `customer_id`
- `user_id`
- `question`
- `answer`
- `model`
- `tool_call_count`
- `confidence`
- `citations_json`
- `created_at`

### 2.5 安全要求

1. LLM 回答必须带引用来源，不能凭空编造轨迹。
2. 找不到订单时，回答“没有查到”，不能猜。
3. 客户自助接口必须强制按 `customerId` 过滤。
4. 不把身份证、手机号、完整地址等敏感信息直接给 LLM，除非业务确实需要。

## 3. 客户发票识别模块

### 3.1 目标

客户上传 PDF、图片或 Excel 发票后，系统自动识别并转换成统一模板，用于对账、导入费用、生成账单草稿。

### 3.2 统一模板

建议统一输出：

```json
{
  "invoiceNo": "INV-10001",
  "invoiceDate": "2026-05-28",
  "customerName": "ABC Trading",
  "currency": "USD",
  "totalAmount": 123.45,
  "lines": [
    {
      "orderNo": "10000001",
      "trackingNo": "1Z999",
      "chargeType": "FREIGHT",
      "description": "运费",
      "quantity": 1,
      "unitPrice": 100.00,
      "amount": 100.00
    }
  ],
  "warnings": [
    "第 3 行没有识别到运单号"
  ]
}
```

### 3.3 建议接口

#### 上传识别

```http
POST /api/document/invoice-recognition/jobs
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

字段：

- `file`: PDF / JPG / PNG / XLSX
- `customerId`: 可选
- `templateHint`: 可选，客户模板名

返回：

```json
{
  "ok": true,
  "data": {
    "jobId": "uuid",
    "status": "PENDING"
  },
  "error": null,
  "errorCode": null
}
```

#### 查询识别结果

```http
GET /api/document/invoice-recognition/jobs/{jobId}
Authorization: Bearer <token>
```

返回：

```json
{
  "ok": true,
  "data": {
    "jobId": "uuid",
    "status": "COMPLETED",
    "normalizedInvoice": {},
    "confidence": 0.91,
    "warnings": []
  },
  "error": null,
  "errorCode": null
}
```

#### 确认导入

```http
POST /api/document/invoice-recognition/jobs/{jobId}/confirm
Authorization: Bearer <token>
Content-Type: application/json
```

用途：

- 用户确认识别结果。
- 系统把结果写入 `charges`、`customer_invoices` 或对账草稿表。

### 3.4 落库建议

新增表：

- `invoice_recognition_jobs`
- `invoice_recognition_lines`
- `invoice_templates`

状态：

- `PENDING`
- `PROCESSING`
- `NEEDS_REVIEW`
- `COMPLETED`
- `FAILED`
- `CONFIRMED`

### 3.5 实现建议

1. 第一版先支持 PDF / 图片 OCR + Excel 解析。
2. Excel 必须优先使用结构化解析，不要交给 LLM 猜。
3. LLM 只用于字段映射、异常说明、非结构化 PDF 提取。
4. 导入前必须人工确认。
5. 确认后写入正式费用时必须复用 `documentcharges` 服务层，确保 AR/AP、收款核销、利润、`balance_ledger` 与 `fx_rate_snapshots` 口径一致。
6. 不允许发票识别模块直接更新 `financial_accounts.balance`；任何余额变化必须经过现有 ledger 入口。

## 4. 3D 智能装箱模块

### 4.1 目标

输入货物尺寸、重量、箱型、渠道限制，输出推荐装箱方案，减少体积重、减少箱数，并符合渠道尺寸重量限制。

### 4.2 建议接口

#### 同步试算

```http
POST /api/packing/3d/plan
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "shipmentNo": "10000001",
  "channelCode": "KH003",
  "units": {
    "weight": "kg",
    "dimension": "cm"
  },
  "items": [
    {
      "sku": "SKU001",
      "name": "商品 A",
      "quantity": 2,
      "weight": 1.2,
      "length": 20,
      "width": 10,
      "height": 8,
      "stackable": true,
      "rotatable": true,
      "fragile": false
    }
  ],
  "cartonTypes": [
    {
      "code": "BOX-L",
      "length": 60,
      "width": 40,
      "height": 40,
      "maxWeight": 25
    }
  ],
  "constraints": {
    "maxCartons": 10,
    "allowSplitSku": true,
    "optimizeFor": "VOLUME_WEIGHT"
  }
}
```

返回：

```json
{
  "ok": true,
  "data": {
    "status": "SUCCESS",
    "cartons": [
      {
        "cartonNo": "10000001U001",
        "cartonType": "BOX-L",
        "weight": 2.4,
        "length": 60,
        "width": 40,
        "height": 40,
        "volumeUtilization": 0.58,
        "items": [
          {
            "sku": "SKU001",
            "quantity": 2,
            "position": {
              "x": 0,
              "y": 0,
              "z": 0
            }
          }
        ]
      }
    ],
    "unpackedItems": [],
    "warnings": []
  },
  "error": null,
  "errorCode": null
}
```

#### 异步任务

复杂订单使用异步：

```http
POST /api/packing/3d/jobs
GET /api/packing/3d/jobs/{jobId}
POST /api/packing/3d/jobs/{jobId}/apply
```

### 4.3 算法建议

第一版不要追求最优解，先做可解释、稳定的启发式算法：

1. 按不可旋转、易碎、重货优先排序。
2. 按箱型从小到大尝试。
3. 使用 First Fit Decreasing + 3D 空间切割。
4. 对每个 item 尝试 6 种旋转方向。
5. 不可装入时进入 `unpackedItems`，提示人工处理。
6. 输出利用率、体积重、实重、渠道限制命中情况。

后续再引入 OR-Tools 或独立 packing solver。

### 4.4 与现有系统关系

- 可以和 `stowage` 模块对接，作为配载/装箱前的推荐。
- 可以和 IoT pickup 数据对接，实际重量尺寸回写后重新计算；当前电子秤 `scale_records` 已能采集重量/尺寸，后续可同步回 `cartons`。
- 可以和 RateEngine 对接，比较不同装箱方案下的体积重费用。
- 计划确认后应通过现有 `StowageStateMachine` 推进装箱、转运、派送状态，避免绕过 ACC 配载状态机。
- 若生成新的配载/转运记录，应同步维护 `shipment_order_links`、`tracking_events` 和 `acc_transit_items` 关联，保证轨迹聚合可见。

## 5. 仓库 IoT 单一推送接口

### 5.1 接口定位

**当前代码状态（2026-05-29）：该接口尚未实现。** 业务之前已决定仓库 IoT push 先暂缓，因此它不阻塞 ACC 主业务上线。已有的电子秤 `Scale.php` 能力已通过 `scale_devices` / `scale_records` 实现，仓库 IoT 后续应在此基础上扩展，不要重复实现电子秤逻辑。

仓库 IoT、DWS、流水线、分拣机只对接一个地址：

```http
POST /api/iot/warehouse/parcel
Content-Type: application/json
```

通过 `action` 区分动作：

- `check`: 检查货箱是否存在。
- `pickup`: 首次拣货/收货。
- `update`: 更新拣货数据。

注意：这是外部设备协议接口，响应格式按设备要求返回 `status/info/options/print_labels`，不包装成系统内部 `ApiResponse<T>`。

### 5.2 鉴权

请求字段：

- `time`: 时间戳。
- `token`: 验证码。

算法：

```text
token = md5(secret + time)
```

要求：

1. `secret` 必须放在环境变量、配置中心或设备密钥表，不允许硬编码到代码或提交到 git。
2. 请求时间和服务器时间允许偏差建议为 5 分钟。
3. 同一个 `token + time + item_number + action` 在短时间内重复请求应识别为重放或幂等重试。
4. 生产环境必须使用 HTTPS。
5. 建议加 IP 白名单和设备编号 `device_code`。

配置建议：

```text
warehouse.iot.default-secret=${WAREHOUSE_IOT_SECRET}
warehouse.iot.allowed-skew-seconds=300
warehouse.iot.enable-ip-allowlist=true
```

### 5.3 action=check

#### 请求

```json
{
  "action": "check",
  "item_number": "10000001U001",
  "time": "1779940800",
  "token": "32位小写md5",
  "shipment_number": "10000001",
  "ext": {}
}
```

#### 处理逻辑

1. 验签。
2. 校验箱号 `item_number` 是否存在。
3. 如果传了 `shipment_number`，校验箱号是否属于该运单。
4. 查询总箱数、当前已收箱数、服务、国家、邮编。
5. 返回给分拣机展示的 `options`。

#### 成功返回

```json
{
  "status": 1,
  "info": "",
  "options": [
    {
      "label": "箱号",
      "value": "10000001U001"
    },
    {
      "label": "总箱数",
      "value": 10
    },
    {
      "label": "运单号",
      "value": "10000001"
    },
    {
      "label": "服务",
      "value": "专线速运"
    },
    {
      "label": "服务代码",
      "value": "KH003"
    },
    {
      "label": "国家",
      "value": "美国"
    },
    {
      "label": "邮编",
      "value": "KH001"
    }
  ]
}
```

#### 失败返回

```json
{
  "status": 0,
  "info": "箱号不存在",
  "options": []
}
```

### 5.4 action=pickup / update

#### 请求

```json
{
  "action": "pickup",
  "item_number": "10000001U001",
  "shipment_number": "10000001",
  "weight": 22.1,
  "length": 33,
  "width": 33,
  "height": 33,
  "time": "1779940800",
  "token": "32位小写md5",
  "pic_base64": "/9j/4AAQSkZJRgABA...",
  "pic_url": "https://oss.example.com/pickup/10000001U001.jpg",
  "ext": {
    "printlabels": {
      "shipment_parcel": {
        "qty": 1,
        "printer_name": "Printer-A"
      },
      "outer_shipment_parcel": {
        "qty": 1,
        "printer_name": "Printer-B"
      }
    }
  }
}
```

字段规则：

- `weight`: kg。
- `length` / `width` / `height`: cm。
- `pic_url` 和 `pic_base64` 至少传一个；生产建议优先 `pic_url`。
- `pickup` 表示首次拣货。
- `update` 表示更新重量、尺寸、图片或打印请求。

#### 处理逻辑

1. 验签。
2. 校验箱号和运单归属。
3. 对 `item_number + action + time` 做幂等检查。
4. 写入或更新包裹实际重量尺寸。
5. 保存图片 URL；如果传 `pic_base64`，后台异步上传到对象存储。
6. 更新包裹状态为 `PICKED` 或 `MEASURED`。
7. 重新计算运单已收箱数。
8. 如果 `ext.printlabels` 要求打印，则调用 `LabelService` 生成标签。
9. 返回流水线动作、播报内容、展示 options 和打印标签。

#### 成功返回

```json
{
  "status": 1,
  "info": "",
  "action": "continue",
  "voice_text": "拣货成功，当前 1 / 10",
  "options": [
    {
      "label": "件数",
      "value": "1/10"
    },
    {
      "label": "箱号",
      "value": "10000001U001"
    },
    {
      "label": "总箱数",
      "value": 10
    },
    {
      "label": "运单号",
      "value": "10000001"
    },
    {
      "label": "服务",
      "value": "专线速运"
    },
    {
      "label": "服务代码",
      "value": "KH003"
    },
    {
      "label": "国家",
      "value": "美国"
    },
    {
      "label": "邮编",
      "value": "KH001"
    }
  ],
  "print_labels": [
    {
      "printer_name": "Printer-A",
      "print_qty": 1,
      "type": "pdf",
      "label_url": "https://oss.example.com/labels/10000001U001.pdf"
    }
  ]
}
```

需要停止流水线时：

```json
{
  "status": 1,
  "info": "",
  "action": "stop",
  "voice_text": "重量超出渠道限制，请人工处理",
  "options": [
    {
      "label": "箱号",
      "value": "10000001U001"
    }
  ],
  "print_labels": []
}
```

失败返回：

```json
{
  "status": 0,
  "info": "签名错误",
  "action": "stop",
  "voice_text": "签名错误，请联系管理员",
  "options": [],
  "print_labels": []
}
```

### 5.5 建议错误码口径

外部设备只需要 `status/info`，内部日志记录详细错误码：

| 内部错误码 | 对外 info |
| --- | --- |
| `IOT_AUTH_INVALID` | 签名错误 |
| `IOT_TIMESTAMP_EXPIRED` | 请求已过期 |
| `IOT_ITEM_NOT_FOUND` | 箱号不存在 |
| `IOT_SHIPMENT_MISMATCH` | 箱号和运单不匹配 |
| `IOT_DIMENSION_INVALID` | 重量或尺寸不合法 |
| `IOT_LABEL_FAILED` | 标签生成失败 |
| `IOT_DUPLICATE_REQUEST` | 重复请求 |

### 5.6 落库建议

当前建议：**第一阶段可以不新建 IoT 专表**，先按仓库要求的响应格式返回，并把重量、尺寸、图片、状态尽量映射到现有 `shipments` / `cartons` / `scan_events` / `scale_records`。当仓库正式接入、有真实设备重放和审计需求后，再新增 IoT 专表。

如果进入第二阶段，新增表：

#### `iot_devices`

- `id`
- `tenant_id`
- `device_code`
- `device_name`
- `secret_hash`
- `ip_allowlist`
- `enabled`
- `created_at`

#### `iot_parcel_events`

- `id`
- `tenant_id`
- `device_code`
- `action`
- `item_number`
- `shipment_number`
- `weight`
- `length`
- `width`
- `height`
- `pic_url`
- `raw_request_json`
- `response_json`
- `idempotency_key`
- `status`
- `error_code`
- `created_at`

#### 包裹表字段补充

现有箱表/称重表已经支持大部分智能排柜需要的重量和尺寸数据。若当前包裹/箱表仍缺字段，需要补：

- `actual_weight_kg`
- `actual_length_cm`
- `actual_width_cm`
- `actual_height_cm`
- `picked_at`
- `measured_at`
- `pickup_image_url`
- `iot_device_code`

字段命名应对齐当前 `cartons` 既有实际重量/尺寸字段，不要再新增一套含义重复的 `actual_weight` / `length` 字段。

### 5.7 后端文件建议

新增：

```text
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotController.java
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotService.java
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotRepository.java
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotRequests.java
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotResponses.java
apps/backend/src/main/java/com/xqt/saas/iot/warehouse/WarehouseIotSignatureVerifier.java
```

新增 migration：

```text
db/migrations/048_warehouse_iot_schema.sql
```

注意：`036` 已被 `036_acc_currency_fields.sql` 占用，当前最新 migration 已到 `047_branch_visibility_fallback.sql`，仓库 IoT 新 migration 必须从 `048` 或更高编号开始。

### 5.8 Claude 实施提示

```text
请阅读 docs/ai-iot-new-modules-design-2026-05-28.md，先实现仓库 IoT 单一入口接口。

要求：
1. 新增 /api/iot/warehouse/parcel，支持 action=check/pickup/update；
2. 响应不要包 ApiResponse，必须按设备协议返回 status/info/options/print_labels；
3. token 校验为 md5(secret + time)，secret 从配置读取，不要硬编码；
4. 实现时间戳过期、防重放、幂等；
5. pickup/update 写入包裹实际重量尺寸、图片 URL、事件日志；
6. ext.printlabels 存在时调用现有 LabelService 生成标签；LabelService 已支持 ZPL/PNG/JPG 自动转 PDF；
7. 补 controller/service/repository 测试；
8. 新 migration 从 048 或更高编号开始；
9. 不要先实现 AI 客服、发票识别、3D 装箱，只预留 migration 和接口设计即可。
```

## 6. 实施顺序

在 ACC 主业务和生产部署基础已经完成后，建议顺序调整为：

1. 生产账号/基础数据配置：真实渠道账号、价卡、客户余额、汇率、分公司权限。
2. 仓库 IoT 推送接口（业务正式启动时）。
3. 3D 智能装箱基础版。
4. 客户发票识别。
5. 智能客服。

原因：

- 生产业务要先保证 ACC 主路径可运行：报价、Submit、取号、面单、费用、账单、资金流水。
- IoT 是外部设备对接，有明确协议和最高确定性。
- 3D 装箱可以直接利用 IoT 回传的真实重量尺寸。
- 发票识别需要文件存储、OCR、人工确认流。
- 智能客服依赖订单、轨迹、费用、发票等数据越完整，回答越可靠。

## 7. 生产上线与账号配置要求

### 7.1 必须配置

| 配置项 | 说明 |
| --- | --- |
| `JWT_SECRET` | 生产 JWT 密钥，不能使用默认值 |
| `RATES_STRICT_QUOTE=true` | 禁止报价失败后走 dev/demo 估算价 |
| `APP_CARRIER_STRICT_GATEWAY=true` 或 `CARRIER_STRICT_GATEWAY=true` | 禁止 Noop/Demo 兜底取号 |
| `POSTGRES_PASSWORD` | 修改生产数据库默认密码 |
| `users.branch_id` | 后台用户分公司权限 |
| `customers.branch_id` | 客户分公司归属，新建 shipment 会继承 |
| `exchange_rates` | 汇率；缺失会产生 `MISSING_RATE` 快照 |
| 真实 `acc_channel_accounts` | 生产承运商账号、`provider_code`、API key/secret |

### 7.2 可先使用 Sandbox 的项目

| 项目 | 演示配置 | 生产要求 |
| --- | --- | --- |
| 承运商取号 | `provider_code=SANDBOX` | UPS/FedEx/DHL 等真实 adapter + 凭证 |
| 面单 | Sandbox/Noop label provider | 真实 label provider 或承运商 label API |
| ZPL 转 PDF | 本地最小渲染 | 复杂条码建议接 Labelary 或 provider 直接返回 PDF |
| 仓库 IoT | 暂缓 | 设备 secret、IP 白名单、HTTPS、幂等存储 |

### 7.3 部署与验收

生产部署以 `docs/acc-deployment-2026-05-29.md` 为准：

- 服务器部署路径：`/opt/xqt-saas`
- 服务：PostgreSQL、Redis、backend、web
- `deploy/docker-compose.prod.yml` 挂载全量 migrations 和 seeds
- 部署后验证：

```bash
curl http://<server>/health
curl -X POST http://<server>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"xqt","username":"admin","password":"Test@1234"}'
```

上线后必须修改默认 admin 密码、数据库密码，并轮换任何曾经在沟通中暴露的服务器密码。

