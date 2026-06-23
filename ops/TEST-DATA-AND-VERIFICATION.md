# xqt-saas 测试数据 & 实操验证流程

> 生产地址: **http://8.148.227.76/** · 后端: 18103 · 测试时间约 30 分钟跑完全流程

---

## 1. 登录账号

| 用途 | 用户名 | 密码 |
|---|---|---|
| 系统管理员 | `admin` | `admin123` |

> 上线前**必须改强密码**

---

## 2. 测试数据 (已就绪, 直接用)

### 2.1 客户 (5 个测试客户已建好)

| 客户代码 | 客户名 | 模式 | 信用额度 | 适用场景 |
|---|---|---|---|---|
| `DOC-DEMO` | 制单流程样例客户 | PREPAID 预付 | ¥50,000 | 测试**余额拦截** (Step 7) |
| `SELLER-DEMO` | 卖货流程样例客户 | MONTHLY 月结 | ¥200,000 | **主测试客户**, 走完整流程 |
| `KA-DEMO` | 大客户样例 | MONTHLY | 无限额 | 测试**大客户专价** |
| `TEST-CN-001` | 测试客户-国内出口代理 | MONTHLY | ¥10,000 | 测试**额度不足拒提交** |
| `TEST-VIP-001` | 测试客户-大客户VIP | MONTHLY | ¥1,000,000 | 测试**VIP 优先级** |
| `TEST-NEW-001` | 测试客户-新客户预付 | MONTHLY | ¥0 | 测试**信用为 0** |

### 2.2 渠道 / 制单账号

| 渠道代码 (product) | 渠道名 | 制单账号 (channelAccount) | 最大重量 | 用途 |
|---|---|---|---|---|
| `UPS-GROUND-US` | UPS Ground (US 国内) | `J602B0` | 70kg | **主测试渠道** |
| `EU-AIR-UPS` | 欧洲空派 UPS | `ACC-EU-AIR-UPS` | 70kg | 欧洲单测试 |
| `EU-TRUCK-LTL` | 欧洲卡航卡派 | `ACC-EU-TRUCK-LTL` | 无限制 | 大件测试 |
| `US-GROUND-FEDEX` | 美国 FedEx Ground | `ACC-US-GROUND-FEDEX` | — | 备选承运商 |

### 2.3 HS 编码 (制单可选)

| HS 码 | 品名 |
|---|---|
| `6109100020` | 棉制 T 恤衫 |
| `6204620010` | 棉制女裤 |
| `8517120000` | 蜂窝网络手机 |
| `8471300000` | 便携式自动数据处理设备 |
| `4202920000` | 塑料/纺织背包 |

> 制单 modal 申报明细行 HS 编码字段 → 点 ▼ 下拉可选

### 2.4 收件人样例数据

```
公司: E2E Test Inc.
姓名: John Smith
电话: +1-555-123-4567
国家: US
省/州: CA
城市: Los Angeles
邮编: 90001
地址: 123 Main Street, Suite 100
```

---

## 3. 完整制单验证流程 (跑完 ≈ 30 分钟)

### 路径: 浏览器打开 http://8.148.227.76/ → 用 admin/admin123 登录

---

### Stage 0: 准备 (3 分钟)

**目标**: 强刷一下避免缓存

1. 按 **Ctrl + Shift + R** (Mac: **Cmd + Shift + R**) 强刷
2. 登录: `admin` / `admin123`
3. 看左侧能看到 **9 个一级菜单**: 制单中心 / 配载中心 / 核算中心 / 财务中心 / 客服中心 / 主数据 / 报表统计 / HR 中心 / 系统管理

> ✅ **预期**: 9 个菜单全显示, 无红色错误提示

---

### Stage 1: 新增订单 (5 分钟)

**目标**: 验证制单 modal 9 段表单 + 8 阻止点

1. 左侧 → **制单中心** → 点 **快件订单** tab
2. 右上角点蓝色 **「+ 新增订单」** 按钮
3. modal 弹出 9 段表单, 依次填:

   **基本信息**:
   - 客户单号: 留空 (系统自动生成 `ORDER-YYYYMMDD-XXX`) 或填 `TEST-FLOW-001`
   - 客户: 下拉选 `SELLER-DEMO` (主测试客户)

   **发货渠道**:
   - 发货产品: `UPS-GROUND-US`
   - 制单账号: 下拉选 `J602B0 - RUSHING HUB UPS Ground`
   - 包裹类型: `PARCEL`
   - 电池: `无电池`

   **货物信息**:
   - 英文品名: `T-shirt`
   - 中文品名: `T 恤衫`
   - 件数: `1`
   - 重量 (kg): `1.5`
   - 货物金额: 自动汇总 (申报明细填完后自动算)

   **收件人** (点顶部「📦 收件人」锚点跳过去):
   - 国家: `US`
   - 邮编: `90001` → 焦点离开后自动反查国家+判偏远
   - 公司: `E2E Test Inc.`
   - 收件人: `John Smith`
   - 电话: `+1-555-123-4567`
   - 省/洲: `CA`
   - 城市: `Los Angeles`
   - 地址: `123 Main Street`

   **申报明细** (点「⚠ 申报明细」锚点):
   - 英文品名: `T-shirt`
   - 中文品名: `T 恤`
   - 数量: `1`
   - 单价: `18.88`
   - 货币: `USD`
   - HS 编码: **点 ▼ 下拉选** `6109.10 — 棉制T恤衫`

   **装箱单** (点「📦 装箱单」锚点):
   - 装箱单号: `PKG-001`
   - 英文品名: `T-shirt`
   - 中文品名: `T 恤`
   - 重量: `1.5`
   - 数量: `1`
   - 单价: `18.88`
   - HS 编码: `6109100020`

4. 滚到最底 → 点 **「确认添加」**

> ✅ **预期**: 弹"保存成功", modal 关闭, 列表多一行新订单 (status=`DRAFT`)

---

### Stage 2: **测 8 个阻止下单点** (5 分钟)

逐个验证 ACC_2xx 错误码触发:

#### Test A: 重号阻止 (ACC_216)

1. 再开「+ 新增订单」, 客户单号填上一步建的 `TEST-FLOW-001`
2. 客户单号输入框 **焦点离开**

> ✅ **预期**: 输入框旁红字 "客户单号 TEST-FLOW-001 已存在"; 点确认按钮直接拒绝

#### Test B: HS 编码 8-10 位校验 (ACC_219)

1. 申报明细行 HS 编码填 `6109` (4 位)
2. 点 **确认添加**

> ✅ **预期**: 红字 "申报明细第 1 行: HS 编码必须 6-10 位数字 (现 \"6109\")"

#### Test C: 装箱总重 vs 货物重量

1. 货物重量填 `1.5`, 装箱单第 1 行重量填 `2.5`
2. 点 **确认添加**

> ✅ **预期**: 红字 "装箱单总重 2.50kg 与货物重量 1.50kg 不一致"

#### Test D: 件数 vs 装箱单行数

1. 件数填 `2`, 装箱单只填 1 行
2. 点 **确认添加**

> ✅ **预期**: 红字 "装箱单行数 1 跟件数 2 不一致"

#### Test E: 必填字段缺失

1. 英文/中文品名/重量/件数/客户单号 任一漏填
2. 点确认

> ✅ **预期**: 红字提示具体哪个字段缺

#### Test F: 超重 1000kg (ACC_210)

1. 重量填 `1000`
2. 点确认

> ✅ **预期**: 红字 "重量 1000kg 超过渠道上限 70.00kg (ACC_210)"

#### Test G: 装箱单号重复

1. 装箱单加 2 行, 单号都填 `PKG-001`
2. 点确认

> ✅ **预期**: 红字 "装箱单第 2 行: 装箱单号 PKG-001 跟前面行重复"

#### Test H: 客户产品权限 (P0-A1)

> ⚠️ 客户没专属产品授权 (customer_products 表无数据), 此项当前**放行**, 兼容老租户

---

### Stage 3: 提交订单 (3 分钟)

**目标**: 验证制单 → shipment + charges 自动生成

1. 列表点订单 `TEST-FLOW-001` 那行任意位置 → 弹订单详情 modal
2. 找 **「提交订单」** 按钮 (通常顶部) → 点

> ✅ **预期**: 弹 "提交成功", 返回:
> - `shipmentId` (UUID)
> - `shipmentNo` (`SHP-TEST-FLOW-001`)
> - `trackingNo` (`NOOP-TEST-FLOW-001-000001`)
> - `status: SUBMITTED`

3. 切到 **「未提交」** tab → 订单应该消失 (移到主列表)
4. 列表里看订单 status 变 `SUBMITTED`

---

### Stage 4: 核算中心 - 验证自动建 charges (3 分钟)

**目标**: 验证 P0-B2 联动 (charges 自动生成)

1. 左侧 → **核算中心** → 点 **「运费核算」** tab
2. 找到 `TEST-FLOW-001` 关联的 charges

> ✅ **预期**: 有 **3 笔 AR charges** (status=`ESTIMATED`, audit_status=`PENDING`):
> - 运费 (Freight): ~6.26 USD
> - 燃油 (Fuel): ~1.10 USD
> - 偏远附加费 (Remote): ~2.50 USD (邮编 90001 是否触发偏远视 remote_zones 配置)

---

### Stage 5: 审核 charges → 验证 balance 联动 (5 分钟)

**目标**: 验证 P0-B2 余额联动 (审核一笔 charge → 客户欠款 +amount)

1. 切到 **「待核费用」** tab
2. 找 `TEST-FLOW-001` 的第一笔 charge → 行尾点绿色 **✓ 审核**
3. 弹"审核成功"

> ✅ **预期 (DB 验证)**:
> ```
> charges.audit_status = 'AUDITED'
> balance_ledger 新增一行:
>   direction = DEBIT
>   amount = 6.26 USD
>   biz_type = ADJUST
>   source_type = charges
>   remark = "应收审核: 客户欠款 +6.26"
> ```

4. 切到 **「财务中心」 → 「应收款项」** tab
5. 找 `SELLER-DEMO` 客户 → 未结金额应该 **+6.26 USD**

---

### Stage 6: 出账单 + 收款全流程 (5 分钟)

**目标**: 验证账单生成 + 收款审核 + 余额回退

1. **财务中心 → 客户账单** tab → 右上 **「+ 生成账单」**
2. 客户选 `SELLER-DEMO`, 区间 `今天 ~ 今天`, 保存
3. 找新账单, 点 **「确认」** → status = `CONFIRMED`

> ✅ **预期**: 账单金额 = 客户名下所有已审 charges 累计

4. **财务中心 → 收款记录** → 右上 **「+ 新增」**
5. 填:
   - 收款单号: `RCV-001`
   - 客户: `SELLER-DEMO`
   - 账户: USD 主结算户
   - 金额: 6.26 USD
   - 日期: 今天
6. 保存 → status = `UNAUDITED`
7. 切 **「待审收款」** → 找 RCV-001 → 点 **审核**

> ✅ **预期**:
> ```
> payments.audit_status = 'AUDITED'
> balance_ledger 新增 CREDIT 6.26 USD (客户欠款 -6.26)
> financial_accounts (USD 主结算户) balance +6.26
> ```

8. 切 **「应收款项」** → `SELLER-DEMO` 未结金额回到 **0**

---

### Stage 7: 测**额度不足拒提交** (3 分钟)

**目标**: 验证 P0-B7 客户余额拦截

1. 用 `TEST-NEW-001` (信用额度 0) 建订单
2. 点提交

> ✅ **预期**: 报错 "客户余额不足 + 授信额度不够" 拒绝提交

---

### Stage 8: 利润查询 (2 分钟)

**目标**: 验证 P0-B6 汇率快照 + 实时算

1. **财务中心 → 利润查询** tab
2. 找 `TEST-FLOW-001` 那行

> ✅ **预期**: 显示 revenue / cost / reparation / profit 4 列, 汇率折算 CNY 走 exchange_rate_snapshots

---

### Stage 9: 异常流测试 (5 分钟)

#### Test 1: 申请作废

1. 列表选 `TEST-FLOW-001` → 操作菜单 → **「申请作废」**
2. 填理由保存 → audit_status = `PENDING`
3. 切「作废待审」tab → 点审核
4. 验证 charges 全部 status=VOID, balance_ledger 反向 CREDIT 回退

#### Test 2: 退件流程

1. 新建一单, 提交后, 在配载 tab 模拟到 IN_TRANSIT
2. **客服中心 → 退件** → 「+ 新增」, 走 confirm + verify + audit 4 步
3. 验证 shipment.status → `RETURNING` → `RETURNED`

#### Test 3: 赔偿流程

1. 建一笔 reparation 申请
2. POST `/api/acc/reparations/{id}/verify` (二级审核)
3. POST `/api/acc/{tab}/{id}/audit-biz` (终审入账)
4. 验证 ReturnReparationFinanceSideEffect 写 ADJUSTED 负数 charge

---

### Stage 10: 报表 KPI 验证 (3 分钟)

**目标**: 验证 9 个新报表 endpoint

1. **报表统计 → 运营日报** → 看 4 KPI (今日提取/本周提取/今日签收/异常单)
2. **报表统计 → 客户分析** → 看 piece/weight/profitRate/mainCountry 列
3. **报表统计 → 业务员业绩** → 选月份, 看销售额+提成
4. **财务中心 → 账期已到** → 看 SELLER-DEMO 是否过期
5. **财务中心 → 应付账龄 by 供应商** → 看 P-FEDEX-DEMO 571.20

---

## 4. 清理测试数据 (跑完后)

```sql
PGPASSWORD=Xqt_prod_DB_2026_change_me psql -h localhost -p 15432 -U xqt -d xqt_saas <<EOSQL
-- 清测试订单+关联数据
DELETE FROM balance_ledger WHERE source_id IN (
  SELECT id FROM charges WHERE order_id IN (
    SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%'));
DELETE FROM acc_gl_vouchers WHERE source_id::text IN (
  SELECT id::text FROM charges WHERE order_id IN (
    SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%'));
DELETE FROM payments WHERE reference_no LIKE 'RCV-%';
DELETE FROM customer_invoice_lines WHERE charge_id IN (
  SELECT id FROM charges WHERE order_id IN (
    SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%'));
DELETE FROM customer_invoices WHERE customer_id = '9c41071a-99a2-427e-a1b3-8c6a8e33276f'
  AND created_at::date = current_date;
DELETE FROM charges WHERE order_id IN (
  SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%');
DELETE FROM shipment_order_links WHERE order_id IN (
  SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%');
DELETE FROM shipments WHERE id IN (
  SELECT shipment_id FROM shipment_order_links WHERE order_id IN (
    SELECT id FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%')
) OR shipment_no LIKE 'SHP-TEST-%';
DELETE FROM orders WHERE order_no LIKE 'TEST-FLOW-%' OR order_no LIKE 'E2E-%';
EOSQL
```

---

## 5. 失败时怎么报告 issue

如果某步对不上预期, 截图发给我 + 包含:
1. 浏览器 URL 路径 (e.g. `/制单中心/快件订单`)
2. 操作 (点了哪个按钮)
3. F12 → Network tab 红色的请求 (右键 Copy → Copy as cURL)
4. F12 → Console 红色错误

我直接定位 + 修。

---

## 6. 重要 endpoint 对照 (生产 8.148.227.76)

| 功能 | endpoint |
|---|---|
| 新增订单 | `POST /api/acc/orders/full` |
| 提交订单 | `POST /api/acc/orders/{id}/submit` |
| 审核 charge | `POST /api/acc/charges/{id}/audit-biz` |
| 反审 charge | `POST /api/acc/charges/{id}/undo-audit-biz` |
| 收款审核 | `POST /api/acc/receiveds/{id}/audit-biz` |
| 作废订单 | `POST /api/acc/orders/{id}/request-void` |
| 运营日报 | `GET /api/acc/profits/operations-daily` |
| 异常率 | `GET /api/acc/profits/abnormal-rate` |
| 业务员业绩 | `GET /api/acc/profits/salesman-performance?month=2026-06` |
| 客户分析 | `GET /api/acc/profits/customer-analysis` |
| 账期已到 | `GET /api/acc/profits/expired-bills` |
| 应付账龄 | `GET /api/acc/profits/payable-aging-by-partner` |

---

## 7. 8.148.227.76 上其它有用入口

| URL | 用途 |
|---|---|
| `http://8.148.227.76/actuator/health` | 后端健康检查 |
| `http://8.148.227.76/swagger-ui/index.html` | API 文档 (如开启) |
| `http://8.148.227.76/api/acc/orders?pageSize=10` | 查订单列表 (需 token) |
