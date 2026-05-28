# Comparison Case：多源轨迹聚合（任务 S1）

> 任务：docs/acc-logic-gap-claude-task-supplement-2026-05-29.md §3 任务 S1
> 代码：`apps/backend/src/main/java/com/xqt/saas/tracking/`

## 1. 目标

ACC 旧系统：`Express_Process` / `Transit_Process` / `Stowage_Process` /
`Online_TrackNo` / `Express_TrackNo` 五张表 union 成一条时间线给客户/内部看。

新系统对应来源：
| ACC 表 | 新系统对应 |
|---|---|
| Express_Process | tracking_events（含子单号事件，CARRIER_API 来源）|
| Online_TrackNo / Express_TrackNo | tracking_events（合并）|
| Stowage_Process | acc_stowage_steps（join stowages → cartons → shipment）|
| 上门揽收 | acc_dispatches（按 customer_id + 日期窗）|
| Transit_Process | acc_transits（关联键预留，暂未启用）|

## 2. 实现

### 文件
- `tracking/TrackingEvent.java`（DTO + Visibility 枚举 + toPublic）
- `tracking/TrackingAggregator.java`（service，4 个 from* 方法 + sort）

### 端点
| 端点 | 视角 | 鉴权 |
|---|---|---|
| `GET /api/customer-api/tracking/{trackingNo}/timeline` | **客户**（剥 operator/internalRemark） | customer-api 签名 |
| `GET /api/acc/shipments/{id}/timeline` | **内部**（全字段含 operator）| JWT |

## 3. 测试

### 单测（TrackingAggregatorTest，5 项全过）
1. 3 源混合按时间排序：t1(stowage) → t2(tracking) → t3(dispatch) ✅
2. INTERNAL 含 operator + internalRemark ✅
3. toPublic 剥 operator + internalRemark，message/location 保留 ✅
4. byTrackingNo → cartons resolve shipmentId → 扩到其它源 ✅
5. tracking_no 找不到 shipment 时只返 tracking_events 部分 ✅

### 端到端实测
```bash
GET /api/acc/shipments/7462da1b.../timeline
```

返回：
```json
{
  "shipmentId": "7462da1b-c73e-4e76-bde5-5c500d304173",
  "events": [{
    "source": "tracking_events",
    "sourceId": "a3301e72...",
    "eventTime": "2026-05-08T02:20:00Z",
    "statusCode": "CREATED",
    "location": "Shenzhen",
    "message": "Label created",
    "operator": null,
    "internalRemark": null,
    "visibility": "INTERNAL"
  }]
}
```

✅ 真实数据库返回 1 条 tracking_events 事件，含全 INTERNAL 字段。

## 4. 关键设计

- **可见性分层**：visibility 枚举 INTERNAL/PUBLIC；客户端调 toPublic() 剥敏感字段，从根上避免泄漏
- **关联链**：tracking_events（直接 shipment_id）+ stowage_steps（join cartons.stowage_id）+ dispatches（同客户+时间窗弱关联±3 天，避免误关联其它运单）
- **transits 暂留接口**：当前 acc_transits 表无 shipment 关联键，预留 fromTransits 方法返回空 list，等业务层补关联键后启用
- **故障隔离**：每个 from* 方法独立 try/catch DataAccessException，单表查询失败不影响其它源

## 5. 未来扩展点

1. transits ↔ shipment 关联键落地后启用 fromTransits
2. 物化视图（若 5 源 union 性能不行）
3. 客户端 metadata 过滤（仅返回某段时间）
4. 状态码标准化（不同源用不同 enum）
