# xqt-saas 制单全流程测试 SOP

**文件位置**: `/Users/chaowang/新航线/xqt-saas/ops/SOP-制单测试.md`  
**测试地址**: http://8.148.227.76/  
**账号**: `admin` / `admin123`  
**测试时长**: 全流程 60 分钟跑通; 分阶段每段 5-10 分钟  
**版本**: 17/25 P0 完成 (commit 之后的最新)

---

## ⚠️ 测试前必看 — 浏览器布局说明

登录后页面布局:
- **左侧**: 一列侧边栏 (10 个二级菜单组, 手风琴展开)
- **顶部**: 蓝色面包屑 + 当前 tab 名
- **正中**: 列表/表单/详情主区
- **右下角**: 🤖 蓝紫色圆形 AI 客服按钮 (跟制单测试无关, 忽略)

⚠️ 浏览器要按 Ctrl + Shift + R **强刷**避开缓存

---

## 阶段 0: 准备数据 (5 分钟, 第一次跑必做)

### 0.1 检查客户在不在
1. 左侧手风琴, **点 「销售中心」组** → 展开
2. 点 **「客户管理 customers」** tab
3. 列表里应该看到至少 1 个客户. 如果没有, 点右上 **「+ 新增」**, 至少填:
   - 编号: `TEST-US-001`
   - 名称: `测试客户-美国电商A`
   - 信用额度: `5000`
   - 结算方式: `MONTHLY`
4. 列表里能看到刚建的就过

### 0.2 检查制单账号 (渠道账号)
1. 仍在 「销售中心」 组里
2. 点 **「渠道账号 channel-accounts」** tab
3. 列表里至少要有 1 个 `auditStatus=AUDITED` 的, 推荐 `J602B0 - RUSHING HUB UPS Ground`
4. **必看**: 列表「账号编号」列要显示真实账号代码 `J602B0`, 不能是 UUID. 看到 UUID 就是 bug, 截图发我

### 0.3 (可选) 配客户专价
1. 仍在 「销售中心」 组
2. 点 **「客户专价绑定 customer-rate-cards」** tab (这是上轮新加的)
3. 点右上 **「+ 新增」**:
   - 客户 *: 下拉选 `TEST-US-001`
   - 费率表 *: 填一个 rate_card UUID (没有就跳过这步, 走默认渠道费率)
   - 渠道 (留空=全渠道): 留空
   - 产品代码 (留空=全产品): 留空
   - 优先级: `100`
   - 生效日: 今天
4. 保存. 现在该客户名下新建订单会优先取这个费率

---

## 阶段 1: 制单 (10 分钟)

### 1.1 进入制单入口
1. 左侧 **点 「制单中心」组** → 展开
2. 点 **「快件订单 orders」** tab (这是默认 tab, 也可能已经在了)
3. 右上方点 **「+ 新增」** 蓝色按钮

### 1.2 弹出制单 modal — 9 段全填

modal 顶部有 9 个段落锚点条, 可点击直接跳转:  
**基本信息 / 发货渠道 / 货物信息 / 发票信息 / 收件人 / 发件人 / 进口商 / ⚠ 申报明细 / 装箱单**

#### 1.2.1 基本信息 (sec-basic)
- **客户单号 *** : `TEST-{今日YYYYMMDD}-001` (例 TEST-20260623-001)
- **下单日期** : 默认今天, 不改
- **客户 *** : 下拉选 `TEST-US-001 - 测试客户-美国电商A`
  - ✅ **必看**: 下拉显示客户名而不是 UUID. UUID 是 bug

#### 1.2.2 发货渠道 (sec-channel)
- **发货产品 *** : 填 `UPS-GROUND-US`
- **制单账号 *** : 下拉选 `J602B0 - RUSHING HUB UPS Ground`
  - ✅ **必看**: 下拉显示账号代码+名称, 不是 UUID
- **包裹类型** : `PARCEL`
- **电池代码** : 选 `无电池` (简化, 真测电池单独跑一份 SOP)
- **特殊货物** : `普通` (0)
- **标签类型** : `PDF`

#### 1.2.3 货物信息 (sec-cargo)
- **英文品名 *** : `T-shirt`
- **中文品名** : `T恤`
- **件数** : `1`
- **重量 (kg)** : `1.5`
- **体积 (m³)** : `0.005`

⚠️ 填完这一段, 自动联动 "申报明细" 第 1 行 — 不需手填重复 (是上轮做的优化)

#### 1.2.4 发票信息 (sec-invoice)
- **币种** : `USD`
- **货物金额** : `18.88`
- **运费** : `0` (系统会按渠道算)
- **保险** : `0`
- **备注** : `SOP 制单测试`

#### 1.2.5 收件人 (sec-receiver)
点 modal 顶部的 **「收件人」** 锚点直接跳到这段
- **国家 *** : 下拉选 `US 美国` (输入 US 应该自动定位)
- **邮编 *** : `90001`
- **收件人 *** : `John Smith`
- **公司** : `Acme Corp`
- **电话** : `1-555-0123`
- **省/州** : `CA`
- **城市** : `Los Angeles`
- **地址1** : `123 Main St`
- **税号 / 报关代码** : 留空 (US 不强制)

#### 1.2.6 发件人 (sec-shipper)
通常用默认模板, 直接跳过

#### 1.2.7 进口商 (sec-importer)
非必填, 跳过

#### 1.2.8 ⚠ 申报明细 (sec-declare)
点 modal 顶部 **「⚠ 申报明细 (HS 编码/数量/货值)」** 锚点 — 橙色高亮就是它
- 第 1 行已经自动联动了 (T-shirt / T恤 / 1)
- **海关编码** : 点输入框右侧下拉箭头 → **必看**: 看到 `6109.10 — 棉制T恤衫` 排在第一位
  - 也可手输 `6109.10` 或品名 `t-shirt` 触发自动补全
- **原产地** : `CN`
- **货值** : `18.88` (跟货物金额一致)

✅ 这是上轮做的功能, 没看到推荐就是 bug

#### 1.2.9 装箱单明细 (sec-package)
- **装箱单号** : `PKG-001`
- **货箱重量** : `1.5`
- **商品毛重** : `1.5`
- **长 / 宽 / 高 (cm)** : `20 / 15 / 5`
- **数量** : `1`
- **单价** : `18.88`

### 1.3 保存
- 滚到 modal 最底点 **「确认添加」** 蓝色按钮 (右下)
- 弹「保存成功」后 modal 自动关闭
- 列表回到 「快件订单」, 但**默认 tab 不一定看到刚建的**
- 切到 **「制单中心 → 未提交 orders-draft」** tab 应该能看到 ✅

---

## 阶段 2: 提交订单 (5 分钟)

### 2.1 进未提交列表
1. 「制单中心」 → 点 **「未提交 orders-draft」** tab
2. 找到刚建的 `TEST-20260623-001`
3. 点 **客户单号** 蓝色下划线 (这是上轮加的可点击) **或者** 行尾 **眼睛图标** → 弹订单详情 9 段

### 2.2 提交
方式 A — 在详情 modal 内:
- 滚到顶部, 点 **「提交」** 按钮 (如果有)

方式 B — 列表内联:
- 行尾 **action 按钮组** → 找 **「提交」** 操作

### 2.3 预期 + ⚠ 余额拦截测试 (P0-B7)
- ✅ 提交成功: status 变 `SUBMITTED`, 跳出「未提交」tab 到「快件订单」主 tab
- ⚠ 如果弹 **「客户欠款 X 已超信用额度 Y, 请先收款再提交订单」**: 这就是 **P0-B7 修复生效**
  - 截图发我
  - 改用别的客户, 或者跳到 阶段 6 先做客户收款再回来

### 2.4 验证 charges 自动建出来
1. 左侧 **点 「核算中心」** 组
2. 点 **「运费核算 charges」** tab
3. 找带 `TEST-20260623-001` 的行, 应该有 1-2 条 (AR 应收 + AP 应付如果有成本)
4. 列表「状态」列应该是 `ESTIMATED` (估算, 还没核算)

---

## 阶段 3: 配载排柜 (8 分钟)

### 3.1 选未配载快件
1. 左侧 **点 「配载中心」** 组
2. 点 **「快件查询 shipments-query」** tab
3. 状态过滤选 `IN_WAREHOUSE` 或 `BOOKED`, 找到刚提交的 shipment

### 3.2 建配载方案 (3D 装柜)
1. 仍在 「配载中心」 组
2. 点 **「配载方案 stowage-plans」** tab (这是 xqt-saas 比 ACC 多的 3D 功能)
3. 右上点 **「+ 新增方案」** 或 **「智能分柜」**
4. 选客户 / 目的地 / 柜型 (20'/40')
5. 系统跑 py3dbp 算法, 出 3D 立体图
6. 点 **「保存方案」**

### 3.3 配载列表
- 切到 **「配载列表 stowages」** tab 看新增的配载单
- 默认状态 `DRAFT`

### 3.4 配载审核 (触发 P0-C3 多表事务)
1. 在 stowages 列表行尾 **审核** 按钮 (✓ 图标)
2. 弹审核确认 → 通过

**预期 (P0-C3+C4 修复生效)**:
- ✅ shipments 全部推到 `IN_TRANSIT` 状态
- ✅ 该 shipment 名下的 acc_asks 退件类型问题件自动 `CLOSED`
- ✅ acc_stowage_steps 表写 `AUDIT_OK` 一行
- ⚠ 反审 (撤销审核): 行尾 **反审核** 按钮 → 全部反向回退

---

## 阶段 4: 追踪 (5 分钟)

### 4.1 自动追踪
1. **「制单中心」** → 点 **「追踪快递 orders-batch-track」** tab
2. 勾选刚才那单 (左侧 checkbox)
3. 点右上 **「批量追踪」** 蓝色按钮
4. 系统调 UPS API 拉轨迹回写 tracking_events 表

### 4.2 看轨迹
1. 切到 **「快件订单 orders」** tab
2. 点单号详情 → **「轨迹时间线」** 段
3. 看到 tracking_events 列表

### 4.3 测异常自动建 Ask (P0-C10 修复)
真实生产数据可能不会触发, 这条 SOP 后期跑:
- ⚠ 如果 UPS 回的 raw status 含 `Held in customs` → 自动建 acc_asks (ask_type=CUSTOMS, source=SYSTEM)
- 去 「客服中心 → 问题件」 看是否有 `[自动] FEDEX 轨迹检测异常: Held in customs` 这样的工单

---

## 阶段 5: 签收 → 完结 (3 分钟)

### 5.1 签收
- shipment.status `IN_TRANSIT` → `DELIVERED` (UPS API 返回 delivered=true 自动改, 不需手动)

### 5.2 完结
- 7 天后自动 status=`CLOSED`, 或手动在订单详情点 **「完结订单」**

---

## 阶段 6: 出账单 + 收款 + 利润 (15 分钟)

### 6.1 核算费用 (核算中心)
1. 左侧 **点 「核算中心」** 组
2. 点 **「待核费用 charges-pending」** tab
3. 找到该 shipment 名下的 charges
4. 行尾 **审核** 按钮 → 通过

**预期 (P0-B2 修复生效)**:
- ✅ charges audit_status=AUDITED
- ✅ 客户预扣账户 balance_ledger 加一条 DEBIT (客户欠款 +amount)
- ✅ 总账中心写一张 V-AUTO-xxx 凭证 (借 1121 / 贷 5001)

### 6.2 出账单
1. **「财务中心」** 组 → 点 **「客户账单 bills」** tab
2. 右上 **「+ 生成账单」** 按钮 (如果有) 或 **「+ 新增」**
3. 选客户 → 选区间 → 系统按 customer_invoice_lines 汇总
4. 账单 status=`DRAFT`, 行尾点 **「确认」** → status=`CONFIRMED`

### 6.3 收款
1. **「财务中心」** 组 → 点 **「收款记录 receiveds」** tab
2. 右上 **「+ 新增」**
3. 填:
   - 收款单号: `RCV-{TS}-001`
   - 客户: 下拉选 `TEST-US-001`
   - 账户: 下拉选 `USD 主结算户`
   - 金额: `18.88`
   - 日期: 今天
4. 保存 → status=`UNAUDITED`
5. 切 **「待审收款 receiveds-pending」** tab
6. 找到该单, 行尾 **审核** 按钮

**预期 (P0-B2 修复生效)**:
- ✅ payments (实际是 receiveds 表) audit_status=AUDITED
- ✅ 客户预扣账户 balance_ledger 加一条 CREDIT (客户欠款 -amount)
- ✅ USD 主结算户 balance_ledger 加一条 CREDIT (银行 +amount)
- ✅ 总账写凭证 (借 1002 银行 / 贷 1121 应收账款)

### 6.4 利润查询
1. **「财务中心」** 组 → 点 **「利润查询 profits」** tab
2. 找到该 shipment 行
3. 看 `revenue / cost / reparation / profit` 4 列
4. profit = revenue - cost - reparation

**预期 (P0-B6 修复生效)**:
- ✅ revenue/cost 用审核时刻 freeze 的汇率快照折算 CNY
- ✅ 汇率波动后回看, 历史 profit 不漂移

---

## 阶段 7: 测异常路径 (10 分钟)

### 7.1 测作废订单 (P0-B8)
1. 「制单中心」 → 「快件订单」 → 选一单 → 详情 → **「申请作废」**
2. 填理由 → 保存
3. 切 **「作废订单 orders-void」** tab
4. 勾该单 → 右上 **「批量审核」**

**预期**:
- ✅ orders.status=CANCELLED
- ✅ 该单未审 ESTIMATED charges 标 VOID
- ✅ 客户预扣账户反向 CREDIT 回退余额
- ✅ 没动已开账单的 charges

### 7.2 测退件 4 步 (P0-C2)
1. **「配载中心」** 组 → 点 **「退件处理 returns」** tab
2. 右上 **「+ 新增退件」**:
   - shipment: 选已发货的某单
   - reason: `客户要求退货`
3. 保存 → status=DRAFT

接下来 3 步通过 API 测 (UI 还在做):
```bash
curl -X POST http://8.148.227.76/api/acc/returns/{id}/confirm -H "Authorization: Bearer $TOKEN"
# 预期: shipment.status=RETURNING, 关联 acc_asks/acc_detains 自动 CLOSED/RESOLVED

curl -X POST http://8.148.227.76/api/acc/returns/{id}/verify -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"refundAmount":18.88,"supplierFee":3,"reason":"客户拒收"}'
# 预期: 退款金额 + 物流商费用录入

curl -X POST http://8.148.227.76/api/acc/returns/{id}/audit-biz -H "Authorization: Bearer $TOKEN"
# 预期: ReturnReparationFinanceSideEffect 触发, 退款入客户预扣 CREDIT
```

### 7.3 测问题件 doReply 7 Handle (P0-C7)
1. **「客服中心」** 组 → **「问题件 asks」** tab → 选一条
2. 右下角浮动 AI 客服窗口可问 (跟此项无关), 或 API:
```bash
# 申请赔偿 (handle=4)
curl -X POST http://8.148.227.76/api/acc/asks/{id}/reply -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"content":"申请赔偿 500 CNY","handle":4,"applyAmount":500,"currency":"CNY"}'
```
**预期**:
- ✅ acc_ask_replies 加 1 条 handle=4 的记录
- ✅ acc_reparations 自动建 1 条 status=DRAFT
- ✅ shipment.status=CLAIMING

### 7.4 测提成计算 (P0-B3)
```bash
curl -X POST "http://8.148.227.76/api/acc/commissions/calculate?month=2026-06" \
  -H "Authorization: Bearer $TOKEN"
```
**预期**: 返回 `{month, totalAmount, employeesProcessed}` (Demo 数据 employeesProcessed=0 正常)

### 7.5 测客户预报差异 (P0-D6)
```bash
curl "http://8.148.227.76/api/acc/forecasts/diff" -H "Authorization: Bearer $TOKEN"
```
**预期**: 返回 `{data, total, summary:{totalForecastPieces, totalActualPieces, undeliveredCount, overDeliveredCount}}`

---

## 整体验证 checklist

| 阶段 | 验证项 | 关联 P0 修复 |
|---|---|---|
| 0.2 | 渠道账号列表显示真实代码非 UUID | A1 + 早期 fix |
| 1.2.1 | 客户下拉显示客户名非 UUID | 早期 fix |
| 1.2.2 | 制单账号下拉显示账号代码 | 早期 fix |
| 1.2.8 | HS 编码下拉推荐 6109.10 排首 | 早期 fix |
| 2.3 | 提交时余额拦截 (PREPAY 客户超额) | B7 |
| 2.4 | 提交后 charges 自动建 (ESTIMATED) | (xqt-saas 架构) |
| 3.4 | 配载审核 → shipments=IN_TRANSIT + acc_asks 关闭 + steps 写 | C3 + C4 |
| 6.1 | charges 审核 → 客户预扣 balance_ledger DEBIT + 总账凭证 | B2 |
| 6.3 | 收款审核 → 客户预扣 CREDIT + 银行账户 CREDIT | B2 |
| 6.4 | 利润 SQL 用汇率快照 (target_amount) | B6 |
| 7.1 | 作废审核 → 反向 balance_ledger CREDIT + ESTIMATED charges 标 VOID | B8 |
| 7.2 | 退件 4 步 + 反算 charges | C2 + C9 |
| 7.3 | doReply 7 Handle 自动转单 (扣件/退件/赔偿) | C7 + C8 |
| 7.4 | 提成 calculate endpoint 200 OK | B3 |
| 7.5 | forecasts/diff endpoint 200 OK | D6 |

---

## 报问题模板

填到 GitHub Issue 或 Slack:

```
SOP 步骤号: 1.2.8 (海关编码下拉)
现象: 输入 "T-shirt" 没看到推荐
期望: 第一项是 6109.10 — 棉制T恤衫
浏览器: Chrome 134, 屏幕 1920x1080
截图: <附>
Network HAR: <附>
错误日志 (Console): <附>
```

---

## 已知限制 (不要报)

- 87 单都是 DEMO 数据, 提成 calculate 跑 0 员工正常
- HS 编码库只有 38 条内置, 自定义可在 数据管理 → 海关编码 增加
- 客户专价绑定后, 旧订单 charges 不会重算, 只对新订单生效
- AI 客服浮动窗口跟制单 SOP 无关, 别误以为是制单功能
- 退件 4 步的 ④ 审核入财务还要靠通用 audit-biz 端点
- 还有 8 个 P0 未修 (B4 借款利息 / C5 配载同步 / D2-D5), 不影响主流程

---

## 文件位置 (重要)

| 文件 | 路径 |
|---|---|
| **本 SOP** | `/Users/chaowang/新航线/xqt-saas/ops/SOP-制单测试.md` |
| API 对接指南 | `/Users/chaowang/新航线/xqt-saas/ops/API-客户对接指南.md` |
| 用户实操 runbook | `/Users/chaowang/新航线/xqt-saas/ops/USER-TESTING-RUNBOOK.md` |
| 后端代码 | `/Users/chaowang/新航线/xqt-saas/apps/backend/src/main/java/com/xqt/saas/acc/` |
| 前端代码 | `/Users/chaowang/新航线/xqt-saas/apps/web/src/App.vue` |
| DB migrations | `/Users/chaowang/新航线/xqt-saas/db/migrations/` (089-092 是这一波) |
