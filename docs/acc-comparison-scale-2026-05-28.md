# Comparison Case：Scale.php 电子秤设备接口迁移

> 任务：docs/acc-logic-gap-claude-task-2026-05-28.md §3.7 + 任务6
> 旧系统：`acc/api/Scale.php`（33 行路由）+ `acc/config/scale/Goodscan.php`（插件 doReceive）
> 新系统：`/api/device/scale/{pluginCode}`

## 1. 迁移映射

| ACC | 新系统 |
|---|---|
| `Scale.php?plugin={code}`（按编号加载插件） | `POST /api/device/scale/{pluginCode}` |
| `Scale` 表（接口配置） | `scale_devices`（plugin_code + hid + tenant） |
| `Scale_Item` 表（称重记录） | `scale_records` |
| `Goodscan.doReceive`（接收称重） | `ScaleService.receive` |
| `Goodscan.readData`（列未签入） | `GET .../{pluginCode}/records` |
| 动态加载 PHP 插件文件 | 统一 ScaleService（plugin_code 仅作设备路由标识，不再动态加载代码） |

## 2. 请求/响应（保持 Goodscan 格式）

请求（设备 POST，无 JWT）：
```json
{ "hid": "HID-DEMO-001", "code": "PKG001",
  "L": 300, "W": 200, "H": 150, "weight": 2.5,
  "picture": "<base64,可空>", "time": "2026-05-28 10:00:00" }
```
响应：`{ "code": 0, "error": "upload success" }`（错误时 code 非 0，HTTP 始终 200）

## 3. 鉴权与幂等

- **鉴权**：设备无 JWT。`/api/device/scale/**` 在 SecurityConfig + BearerAuthFilter 双重放行；
  身份由 body.hid 与 scale_devices.hid 匹配校验（复刻 Goodscan HID 校验）。
- **幂等**：`hash = md5(原始报文)`，`scale_records UNIQUE(tenant_id, hash)` +
  `INSERT ON CONFLICT DO NOTHING`。重复报文返回成功但不重复落库（实测重复 POST 后 DB 仅 1 条）。

## 4. 错误码对照（1:1 复刻 Goodscan）

| code | 含义 | ACC | 新系统 |
|---|---|---|---|
| 0 | upload success | ✅ | ✅ |
| 1 | 数据为空 | ✅ | ✅ |
| 2 | JSON 解析失败 | ✅ | ✅ |
| 3 | 找不到 hid | ✅ | ✅ |
| 4 | hid 匹配失败 | ✅ | ✅ |
| 5 | 找不到 code（装箱单号） | ✅ | ✅ |
| 6/7 | L 缺失/非正 | ✅ | ✅ |
| 8/9 | W 缺失/非正 | ✅ | ✅ |
| 10/11 | H 缺失/非正 | ✅ | ✅ |
| 12/13 | weight 缺失/非正 | ✅ | ✅ |
| 99 | 找不到该电子秤接口 | Scale.php alert | ✅ |

## 5. 数据落地

`scale_records`：tenant_id / device_id / no（装箱单号）/ weight / length_mm / width_mm /
height_mm / has_picture / hash(唯一) / shipment_id（签入运单，NULL=未签入）/ received_at。
长宽高存 mm；readData 查询时 mm→cm 向上取整（对齐 Goodscan `ceil(/10)`）。

## 6. 端到端验证（实测）

| 场景 | 结果 |
|---|---|
| 设备 POST 正常报文 | `{code:0, error:"upload success"}` + 落库 |
| 重复同报文（幂等） | code 0，DB 仍 1 条 |
| hid 错误 | `{code:4}` 不落库 |
| 查未签入记录 | 返回 `[{No,weight,length:30,width:20,height:15}]`（mm→cm） |

## 7. 刻意偏离 ACC

1. 不再动态加载 PHP 插件文件；plugin_code 仅作设备配置路由标识，称重逻辑统一在 ScaleService。
2. 图片：当前只记 has_picture 标志，未落 base64 图片文件（ACC 存磁盘）。下一轮可接对象存储。
3. 签入（Express 关联）：scale_records.shipment_id 字段已预留，制单签入逻辑下一轮接入。

## 8. 待补（下一轮）

- 真实设备报文样本回放（3 条）逐字段 diff。
- 称重记录签入运单（制单时按 no 匹配 scale_records → 写 shipment_id + 回填 cartons 重量/体积）。
- 图片落对象存储。
