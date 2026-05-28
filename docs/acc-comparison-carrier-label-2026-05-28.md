# Comparison Case：渠道取号 / 面单 provider 数据驱动 + Sandbox

> 任务：docs/acc-logic-gap-claude-task-2026-05-28.md §3.5 + 任务5
> 旧系统：`acc/api/APIClass.php` Submit/Label，`inc/online/<Code>.php` 插件
> 新系统：`CarrierGatewayRegistry` + `LabelGatewayRegistry` 数据驱动 + `SANDBOX` adapter

## 1. 迁移映射

| ACC | 新系统 |
|---|---|
| `Channel_Account` 表（渠道账号 + 插件 Code） | `acc_channel_accounts.provider_code` |
| 取号：`getPlugin($Code)->addOrder($Data)` | `CarrierGatewayRegistry.forChannel(tenant, code).submit(ctx)` |
| 面单：`getPlugin($Code)->doPrint('Label')` | `LabelGatewayRegistry.forChannel(tenant, code).print(ctx)` |
| 动态 PHP 文件加载 | Spring bean + `gatewayKey()` 索引 |
| Plugin->TrackNo / TrackNoList | `Issuance.carrierTrackingNo` / `LabelArtifact.mainTrackingNo`/`subTrackingNos` |
| 插件保存 provider 响应到 Online | `Issuance.raw` / `LabelArtifact.raw` → charges/label_files evidence |

## 2. 路由优先级（取号 + 面单 同一份配置源）

```
1. acc_channel_accounts.provider_code (active=true, 最新 created_at)  ← 数据驱动
2. strict 模式 → 抛错（生产禁 Demo/Noop 兜底）
3. 非 strict + 取号：last_mile_method 推断 *_DEMO
4. 非 strict → Noop 兜底
```

配置：
```yaml
app.carrier.strict-gateway: ${CARRIER_STRICT_GATEWAY:false}
```

## 3. Sandbox Adapter（接近真实 provider）

**SandboxCarrierGateway**（取号）：模拟向 `https://sandbox.carrier.example.com/v1/shipments` POST，
生成 `SBX` 单号；`Issuance.raw` 含 `{provider, request, response}`，落到 `charges.evidence`。

**SandboxLabelGateway**（面单）：模拟向 `.../v1/labels` POST，生成 `SBX-LBL-` 主单号 +
`SBX-SUB-` 子单号；`LabelArtifact.raw` 同样含 request + response，落到 `label_files`。
支持 PDF/ZPL（按 PrintContext.requestedType）。

真实 UPS/FedEx adapter 按此模板换真实 HTTP 调用即可，业务层 (`CustomerApiService.submitOrder` /
`LabelService.generate`) 无需改动。

## 4. 数据驱动种子（migration 039）

`acc_channel_accounts` 加 `provider_code`，并给演示渠道 EU-AIR-UPS 配 SANDBOX provider：
```sql
INSERT INTO acc_channel_accounts (
  tenant_id, channel_id, provider_code, account_no, account_name, endpoint_url, is_active)
VALUES (..., 'SANDBOX', 'SBX-001', 'Sandbox 演示取号账号',
        'https://sandbox.carrier.example.com/v1/shipments', true);
```

## 5. 启动注册验证（实测）

```
[CarrierGatewayRegistry] registered gateways: [FEDEX_DEMO, UPS_DEMO, NOOP, SANDBOX]
[LabelGatewayRegistry]   registered providers: [NOOP, SANDBOX]
```

## 6. 测试场景

| # | 场景 | Carrier | Label |
|---|---|---|---|
| 1 | provider_code=SANDBOX → 路由 sandbox | ✅ | ✅ |
| 2 | 无配置 + 非 strict → Noop | ✅ | ✅ |
| 3 | 无配置 + strict → 400 "未配置可用接口" | ✅ | ✅ |
| 4 | strict + 有配置 → 正常 | ✅ | (与上同) |
| 5 | sandbox.submit/print 含 request+response evidence | ✅ | ✅ |
| 6 | sandbox label PDF/ZPL 按请求类型 | — | ✅ |

## 7. 刻意偏离 ACC

1. **不动态加载代码**：ACC 用 `include 'inc/online/'.$Code.'.php'` 加载插件类，新系统所有 adapter
   是 Spring bean，启动时静态注册到 registry；`provider_code` 仅作路由标识。
2. **Sandbox 不实现真实 PDF 生成**：仅产 placeholder 字节，生产 adapter 应从 provider 拉真实 PDF；
   Noop 仍生成完整最小 PDF 兼容现有 Label 契约测试。
3. **evidence 字段名归一**：ACC 在多张表分散保存；新系统统一 `Issuance.raw` /
   `LabelArtifact.raw` → 业务层落到 charges/label_files。

## 8. 待补（下一轮）

- 真实 UPS/FedEx/EDI adapter：用 `acc_channel_accounts` 的 api_key/api_secret/endpoint_url 取号。
- 失败重试/幂等：取号失败的 evidence 落库 + 不污染 shipment 状态。
- 真实 PDF 拉取 + ACC 的非 PDF 图片→PDF 转换（FPDI/TCPDF 等价）。
- evidence 在前端"运单详情"页可见（reviewers 排障必需）。
