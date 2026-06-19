# xqt-saas 用户实操 runbook

替代 ACC PHP 之前必须找真实用户跑通的核心场景。每个角色一份 checklist，跑完后把"卡住的地方"填在 Issues 区。

## 使用方法

1. 复制本文件到一个新 PR 或 issue
2. 让财务/制单/仓库各 1 名同事按下面 checklist 跑
3. 每个 ✓ 项遇到任何不顺手都填到末尾的 **Issues 区**，包含: 步骤号 / 现象 / 期望 / 截图
4. 跑完后告诉我，按反馈打补丁

环境: http://8.148.227.76/  admin/admin123 (demo, 真上线前改)

---

## 角色 A: 财务专员（核心）

目标：把客户欠款收回来 + 出账单。

### A1. 看应收欠款（每日开工第 1 件事）
- [ ] 财务中心 → 应收款项 (customer-receivables) 能看到 4 条欠款（数额 35.44 / 294.4 / 764.57 / 943.5）
- [ ] 点客户名能看到 ta 名下未结订单明细
- [ ] 排序按 unpaid_amount 倒序

### A2. 出账单
- [ ] 客户账单 (bills) 看到 3 张 (DRAFT / PENDING / CONFIRMED 各 1)
- [ ] DRAFT 状态账单能编辑金额
- [ ] CONFIRMED 状态账单**不能**编辑（已审核字段锁定）
- [ ] 「客户单号」列能点击进订单详情

### A3. 收款
- [ ] 财务中心 → 收款记录 (receiveds) → 「+ 新增」
- [ ] 客户下拉显示**客户名**（不是 UUID）
- [ ] 账户下拉显示**账户名**（USD 主结算户 / 新航线测试结算户）
- [ ] 金额必填，币种默认 CNY
- [ ] 单号生成器 / 手填都可
- [ ] 保存 → 出现在「待审收款」(receiveds-pending) tab
- [ ] 在 receiveds-pending 列表行内能看到「审核」按钮（这是上一轮修的）
- [ ] 点审核 → 状态变 AUDITED → 钱真的入账 → 关联订单 charges 状态变化

### A4. 退款（异常流）
- [ ] 客户退款 (customer-refunds) → 「+ 新增」
- [ ] 「退款金额必填且 > 0」校验生效
- [ ] 必须先有 AUDITED 的应收才能退（业务规则）

### A5. 利润查询（月初对账）
- [ ] 利润查询 (profits) 列表展示
- [ ] 每行有 revenue / cost / reparation / profit 4 列
- [ ] 列宽自适应，钱按 ¥ 符号显示
- [ ] 低利快件 (profits-lowprofit) 能筛出 profit < 50 的

### A6. 批量审核（高频操作）
- [ ] 在 charges-pending 勾 5 条 → 工具栏「批量审核(5)」
- [ ] 提交后状态全变 AUDITED → 总账副作用触发

### A7. 财务工作台导入实际账单
- [ ] 核算中心 → 导入费用 (charges-import) → 「上传费用 CSV」按钮可见（这是上轮修的）
- [ ] 上传 UPS/FedEx 实际账单 CSV (tracking_no, actual_amount, currency)
- [ ] 显示「匹配 X 条 / 跳过 Y 条」
- [ ] 跳过的能下载错误清单

---

## 角色 B: 制单员（高频）

目标：把今日要发的快件录入系统并打面单。

### B1. 新建快件
- [ ] 制单中心 → 快件订单 → 「+ 新增」打开完整制单表单
- [ ] 顶部锚点栏 9 个 section 可见（这是上轮修的）
- [ ] 制单账号下拉显示账号名（如 `J602B0 - RUSHING HUB UPS Ground`，不是 UUID）（这是上轮修的）
- [ ] 客户下拉显示客户名
- [ ] 货物信息「英文品名」填 "T-shirt" → 申报明细第 1 行自动同步（这是上轮修的）
- [ ] 申报明细的 hsCode 列点开下拉 → 看到 `6109.10 — 棉制T恤衫` 推荐（这是上轮修的）
- [ ] 收件人国家填 US，邮编符合 5 位数字格式
- [ ] 件数 / 重量 / 体积都填
- [ ] 保存 → 跳到「未提交」(orders-draft) tab，能看到刚建的单

### B2. 提交订单
- [ ] orders-draft 点详情 → 「提交」按钮可见
- [ ] 提交后 status 变 SUBMITTED
- [ ] 自动调外部 API 取转单号
- [ ] 自动产生 ESTIMATED charges (side=AR/AP 两条)

### B3. 批量改转单号（ExpressBatch）
- [ ] orders-update-tracking tab 看到「批量改转单号」按钮（这是本轮做的）
- [ ] 点开 dialog → 填 textarea 每行 "订单ID,转单号"
- [ ] 校验：批内重复转单号会被拒
- [ ] 校验：转单号已被其他订单占用会被拒
- [ ] 提交后弹「成功 X / 跳过 Y」

### B4. 批量改计费重
- [ ] orders-update-weight tab 「批量改计费重」按钮（这是本轮做的）
- [ ] textarea 填 "订单ID,1.5kg" 每行
- [ ] 提交后 cartons.chargeable_weight_kg 等比例缩放
- [ ] 多件订单：N 件 carton 都按比例改

### B5. 变更客户
- [ ] orders-change-customer 勾 3 个订单 → 「变更选中(3)到新客户」（这是本轮做的）
- [ ] 选目标客户 → 提交
- [ ] 校验：已有 AUDITED charges 的订单被拒
- [ ] 校验：所有订单都已经是目标客户时被拒（无意义操作）

### B6. 批量计费重算
- [ ] orders-batch-charge 勾 5 个订单 → 「重算选中(5)费用」
- [ ] 提交后 ESTIMATED charges 标 VOID
- [ ] 下次审核触发定价引擎

### B7. 批量打印面单
- [ ] orders-batch-print 勾选 → 「批量打印面单(N)」（这是上轮修的）
- [ ] 返回文档列表（暂时是 JSON，PDF 渲染待补）

---

## 角色 C: 仓库录入员 / 操作员

目标：到货扫码 → 配载 → 出货。

### C1. 收货入仓 (DWS)
- [ ] 客服中心 → DWS 实物分拣 (dws-scans) → 扫一个箱码
- [ ] 自动跑重量差异检查 → 实测 vs 客户预报
- [ ] 差异 > 阈值落 dws-discrepancies

### C2. 配载排柜
- [ ] 配载中心 → 配载方案 (stowage-plans) → 选客户/目的地 → 「智能分柜」
- [ ] 看到 3D 立体图 (上轮做的)
- [ ] PDF 装柜清单能下载（中文不乱码）
- [ ] 重心 + 4 象限重量分布显示

### C3. 异常处理
- [ ] 配载中心 → 异常提单 (stowages-exception) → 看到 EXCEPTION 状态
- [ ] 扣件处理 (detains) → 录入扣件单
- [ ] 退件处理 (returns) → 录入退件单
- [ ] 各自能走完审核流

### C4. 渠道统计
- [ ] 配载中心 → 渠道统计 (channel-stats) → 显示各渠道近 30 天数据
- [ ] piece / weight / charge / cost 4 列都有数（不是 0）

### C5. 实时追踪
- [ ] orders-batch-track 勾选 → 「批量追踪(N)」
- [ ] UPS/FedEx API 真正打到 → 更新 tracking_events 表

---

## Issues 区（用户填这里）

填写格式:

```
### Issue #1: <一句话标题>
- 步骤: A3.4 (在收款表单填客户)
- 现象: 客户下拉空白，搜不出客户
- 期望: 看到 8 个客户列表
- 截图: <粘贴 / 链接>
- 浏览器: Chrome 120 / 屏幕 1920x1080
```

---

## 完成度跟踪

每个角色跑完后填这里:

| 角色 | 跑测人 | 跑测日期 | 通过项 | 失败项 | 总 issues |
|---|---|---|---|---|---|
| A 财务 | | | / 28 | | |
| B 制单 | | | / 27 | | |
| C 仓库 | | | / 13 | | |

---

## 边界条件 / 已知限制（不算 issue）

- HS code 数据库目前为空，全走前端内置 38 条（已记录）
- 批量打印只返回文档清单 JSON，PDF 渲染待后端补
- 4 个 ExpressBatch 批量操作单次上限 200 行
- 历史 ACC 数据未迁移（要做迁移脚本）
- admin/admin123 是 demo 密码，真切换前必须改
- 云备份 (OSS) 占位未启用，需要 OSS bucket + AK
