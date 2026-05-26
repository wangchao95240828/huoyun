"""Generate ACC API 完成状态报告 docx."""
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement


def set_table_header(table):
    for cell in table.rows[0].cells:
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.bold = True
        tc_pr = cell._tc.get_or_add_tcPr()
        shd = OxmlElement('w:shd')
        shd.set(qn('w:fill'), 'D9D9D9')
        tc_pr.append(shd)


def add_table(doc, headers, rows, col_widths=None):
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = 'Light Grid Accent 1'
    for i, h in enumerate(headers):
        t.rows[0].cells[i].text = h
    for r, row in enumerate(rows, start=1):
        for c, val in enumerate(row):
            t.rows[r].cells[c].text = '' if val is None else str(val)
    if col_widths:
        for col, w in zip(t.columns, col_widths):
            for cell in col.cells:
                cell.width = w
    set_table_header(t)
    return t


def para(doc, text, bold=False, italic=False, color=None, size=None):
    p = doc.add_paragraph()
    run = p.add_run(text)
    if bold:
        run.bold = True
    if italic:
        run.italic = True
    if color:
        run.font.color.rgb = RGBColor.from_string(color)
    if size:
        run.font.size = Pt(size)
    return p


def bullet(doc, text):
    p = doc.add_paragraph(style='List Bullet')
    p.add_run(text)
    return p


def code_block(doc, text):
    p = doc.add_paragraph(text)
    for run in p.runs:
        run.font.name = 'Consolas'
        run.font.size = Pt(9)
    return p


doc = Document()

style = doc.styles['Normal']
style.font.name = 'Microsoft YaHei'
style.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
style.font.size = Pt(10)

# ── 标题 ──
title = doc.add_heading('ACC 系统迁移 — API 完成状态报告', level=0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('生成日期：2026-05-25  ·  代码仓库：xqt-saas  ·  ACC 来源：/Users/chaowang/新航线/acc')
r.italic = True
r.font.size = Pt(9)
r.font.color.rgb = RGBColor.from_string('666666')

doc.add_paragraph()

# ── 一、总览 ──
doc.add_heading('一、总览', level=1)
doc.add_paragraph(
    '本系统对 ACC 旧 PHP 后台进行迁移，分两个层面：'
)
bullet(doc, '客户外部 API（signed，14 端点）：从 acc/api/APIClass.php、acc/api/Track.php、'
            'acc/api/getNewLabel.php 逐项 1:1 业务行为复刻，含契约测试锁定。')
bullet(doc, 'ACC 后台业务 tab（78 个）：前端 App.vue 的 accTabs 数组中所有 tab，'
            '后端按 /api/acc/{tab} 形式提供 CRUD + 审核流，已完成 50 个全部接入 5 框架（'
            '审核流 / 状态机 / 级联 / 字段闸 / 汇率快照）。')
doc.add_paragraph()

add_table(doc,
    ['类别', '总数', '已完成', '完成率'],
    [
        ['客户外部 API（signed）', '14', '14', '100%'],
        ['ACC 后台业务 tab', '78', '50', '64%'],
        ['基础设施 / 5 框架', '5', '5', '100%'],
        ['DB 迁移文件', '25', '25', '100%'],
        ['前端 UI 集成（审核 badge + 按钮 + 级联提示）', '主页面', '已接入', '100%'],
    ],
    col_widths=[Cm(7), Cm(2.5), Cm(2.5), Cm(2.5)]
)
doc.add_paragraph()

# ── 二、客户外部 API ──
doc.add_heading('二、客户外部 API（signed）— 14 个完整 1:1 复刻 ACC', level=1)
p = doc.add_paragraph()
p.add_run('全部对照 ACC 原 PHP 代码逐项验证，鉴权走自实现的 ACC 兼容签名（')
p.add_run('X-API-User / X-API-Time / X-API-Version / X-API-Sign').font.name = 'Consolas'
p.add_run('），错误码沿用 ACC_NNN 格式。配契约测试锁住响应字段。')
doc.add_paragraph()

add_table(doc,
    ['新 API', 'ACC 旧入口', '业务', '复刻特点'],
    [
        ['GET /api/customer-api/balance', 'act=Balance', '余额查询', '已 retrofit + 对照样本'],
        ['POST /api/customer-api/orders', 'act=PreOrder', '预报下单', 'DRAFT 落 orders.metadata.acc_compat'],
        ['POST /api/customer-api/orders/{no}/submit', 'act=Submit', '提交订单', 'DRAFT→SUBMITTED + 落 shipments/cartons/declarations + CarrierGateway'],
        ['PUT /api/customer-api/orders/{no}', 'act=Modify', '修改订单', 'DRAFT 允许，合并到 metadata.acc_compat'],
        ['POST /api/customer-api/orders/{no}/cancel', 'act=Cancel', '取消订单', '状态机校验 + shipments 同步标 EXCEPTION'],
        ['POST /api/customer-api/orders/status', 'act=Status', '批量查状态', 'AccStatusMapping 数字码映射，找不到返回 -1'],
        ['POST /api/customer-api/orders/query', 'act=Query', '订单详情', 'Draft 阶段从 metadata.acc_compat 还原 receiver/declare'],
        ['GET /api/customer-api/channels', 'act=Product/Channel', '渠道字典', '租户已启用 channels'],
        ['POST /api/customer-api/tracking/query', 'act=Track', '轨迹查询', 'tracking_events 多源单表合并'],
        ['POST /api/customer-api/rates/quote', 'act=Price', '运费试算', 'RateEngine MVP（7 个契约测试场景）'],
        ['POST /api/customer-api/labels/generate', 'act=Label / doLabel', '面单生成', 'method=0 base64 / method=1 URL；批量；子单号回写 cartons；Express_Status → tracking_events'],
        ['POST /api/customer-api/labels/relabel', 'api/getNewLabel.php', '换标面单', 'queryNo 三段查找 + hasSingle 多页 PDF 真分页提取（PDFBox）'],
        ['POST /api/customer-api/stowages/sync', 'act=Sync / doSync', '配载同步', '11 项校验 + 跨客户检查 + TrackNo 冲突 + attach/detach'],
        ['POST /api/public/tracking/query', 'api/Track.php', '公开轨迹', '匿名查询，service_role 跨租户'],
    ],
    col_widths=[Cm(5.5), Cm(3.5), Cm(2.2), Cm(5.8)]
)

para(doc,
    '说明：客户外部 API 的复刻深度是 95%（业务逻辑等价）；'
    'NoopCarrierGateway 为占位渠道（生成 NOOP-XXX 子单号），'
    '"客户专属价 / 电池过滤 / 邮编优先级 / 佣金 / 货代分润"等 ACC 边缘分支按文档故意未做。',
    italic=True, color='666666', size=9)
doc.add_paragraph()

# ── 三、ACC 后台 50 个完成 ──
doc.add_heading('三、ACC 后台 tab — 50 个已完成（已接前端）', level=1)
p = doc.add_paragraph()
p.add_run('前端 App.vue accTabs 数组对应的 tab，路径前缀 ')
p.add_run('/api/acc/{tab}').font.name = 'Consolas'
p.add_run('。每个 tab 提供 GET 列表 / GET {id}/raw / POST / PUT {id} / DELETE {id}，'
          '并通过通用 ')
p.add_run('/api/acc/{tab}/{id}/audit-biz').font.name = 'Consolas'
p.add_run(' 等审核端点接入审核流。')

doc.add_heading('3.1 早期字典（3 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '状态'],
    [
        ['customers', '/api/acc/customers', 'customers', '已 retrofit'],
        ['channels', '/api/acc/channels', 'channels', '已 retrofit'],
        ['currencies', '/api/acc/currencies', 'finance_currency (BIGINT id)', '已 retrofit'],
    ],
    col_widths=[Cm(3), Cm(5), Cm(5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.2 P1 业务核心（9 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '特色'],
    [
        ['orders', '/api/acc/orders', 'orders', '客户+件数+计费重子查询'],
        ['shipments', '/api/acc/shipments', 'shipments + cartons 聚合', '额外 {id}/items 装箱单 + 申报'],
        ['bills', '/api/acc/bills', 'customer_invoices + payments 聚合', '额外 {id}/items 账单明细'],
        ['payments', '/api/acc/payments', 'partner_payments', 'AP 付款 + 汇率快照'],
        ['receiveds', '/api/acc/receiveds', 'payments', 'AR 收款 + 汇率快照 + /quick 快速收款'],
        ['charges', '/api/acc/charges', 'charges WHERE side=AR', '应收 + paid 聚合 + 汇率快照'],
        ['costs', '/api/acc/costs', 'charges WHERE side=AP', '应付 + paid 聚合 + 汇率快照'],
        ['profits', '/api/acc/profits', 'shipments + 双向 charges 聚合', '只读视图 + /summary?groupBy='],
        ['packages', '/api/acc/packages', 'shipments + cartons + JSONB acc_compat', '收件人/邮编从 metadata 还原'],
    ],
    col_widths=[Cm(3), Cm(4.5), Cm(5.5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.3 P2 主数据（6 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '状态'],
    [
        ['suppliers', '/api/acc/suppliers', 'partners (SUPPLIER)', '已 retrofit'],
        ['remotes', '/api/acc/remotes', 'remote_zones', '已 retrofit'],
        ['fuels', '/api/acc/fuels', 'fuel_surcharge_rates', '已 retrofit'],
        ['fee-types', '/api/acc/fee-types', 'charge_items', '已 retrofit'],
        ['acc-branches', '/api/acc/branches', "organizations (branch+hq)", '已 retrofit'],
        ['departments', '/api/acc/departments', "organizations (department)", '已 retrofit'],
    ],
    col_widths=[Cm(3), Cm(5), Cm(5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.4 P2 主数据字典 — 022 批次（8 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '说明'],
    [
        ['countries', '/api/acc/countries', 'countries（新）', '国家 + ISO 二字/三字码'],
        ['postcodes', '/api/acc/postcodes', 'postcodes（新）', '邮编 + 国家 + 省/市'],
        ['hscodes', '/api/acc/hscodes', 'hs_codes（新）', 'HS 编码字典'],
        ['bank-names', '/api/acc/bank-names', 'bank_names（新）', '银行字典'],
        ['districts', '/api/acc/districts', 'districts（新，自引用）', '行政区域 + 多级 parent_id'],
        ['customer-groups', '/api/acc/customer-groups', 'customer_groups（新）', '客户分组'],
        ['warehouses', '/api/acc/warehouses', 'warehouses（复用）', '加 audit 列'],
        ['returns', '/api/acc/returns', 'return_orders（复用）', '退件，加 audit 列'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.5 P2 异常流 + 财务扩展 — 023 批次（8 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '说明'],
    [
        ['collects', '/api/acc/collects', 'acc_collects', '总单/留仓'],
        ['detains', '/api/acc/detains', 'acc_detains', '扣件'],
        ['asks', '/api/acc/asks', 'acc_asks', '问题件'],
        ['reparations', '/api/acc/reparations', 'acc_reparations', '赔偿 + 汇率快照'],
        ['void-orders', '/api/acc/void-orders', 'orders WHERE status=CANCELLED', '只读视图，前端列大写驼峰'],
        ['fees', '/api/acc/fees', 'acc_fees', '杂费套餐'],
        ['customer-fines', '/api/acc/customer-fines', 'acc_fines (CUSTOMER)', '客户罚款 + 汇率快照'],
        ['supplier-fines', '/api/acc/supplier-fines', 'acc_fines (SUPPLIER)', '物流商罚款 + 汇率快照'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.6 P2 财务流水 + 字典 — 024 批次（8 个）', level=2)
para(doc, '设计亮点：6 个 customer/supplier × adjust/refund/rebate 共享 acc_finance_txns 单表，'
          'side + txn_type 区分。',
     italic=True, color='666666', size=9)
add_table(doc,
    ['Tab', '路由', 'side', 'txn_type', '共享表'],
    [
        ['customer-adjusts', '/api/acc/customer-adjusts', 'CUSTOMER', 'ADJUST', 'acc_finance_txns'],
        ['supplier-adjusts', '/api/acc/supplier-adjusts', 'SUPPLIER', 'ADJUST', '同上'],
        ['customer-refunds', '/api/acc/customer-refunds', 'CUSTOMER', 'REFUND', '同上'],
        ['supplier-refunds', '/api/acc/supplier-refunds', 'SUPPLIER', 'REFUND', '同上'],
        ['customer-rebates', '/api/acc/customer-rebates', 'CUSTOMER', 'REBATE', '同上'],
        ['supplier-rebates', '/api/acc/supplier-rebates', 'SUPPLIER', 'REBATE', '同上'],
        ['expense-categories', '/api/acc/expense-categories', '—', '—', 'acc_expense_categories'],
        ['fee-item-types', '/api/acc/fee-item-types', '—', '—', 'acc_fee_item_types'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(2.5), Cm(2.5), Cm(4)]
)
doc.add_paragraph()

doc.add_heading('3.7 P2 资金管理 — 025 批次（8 个）', level=2)
add_table(doc,
    ['Tab', '路由', 'DB 表', '关键特性'],
    [
        ['expenses', '/api/acc/expenses', 'acc_expenses', '费用收支 + 银行账户 join + 汇率快照'],
        ['banks', '/api/acc/banks', 'financial_accounts (BANK)', '复用财务账户表 + 跨表级联保护'],
        ['transfers', '/api/acc/transfers', 'acc_transfers', '转出/转入双账户 + 汇率快照'],
        ['dividends', '/api/acc/dividends', 'acc_dividends', '分红 + 资金账户 + 汇率快照'],
        ['borrowings', '/api/acc/borrowings', 'acc_borrowings', '借贷 + 利率字段'],
        ['cycles', '/api/acc/cycles', 'acc_cycles', '周期费用 + 开始/结束日期'],
        ['assets', '/api/acc/assets', 'acc_assets', '固定资产 + 折旧 + 残值 + 折旧月数'],
        ['received-sms', '/api/acc/received-sms', 'acc_received_sms', '收款短信 + 银行账户 join'],
    ],
    col_widths=[Cm(3.5), Cm(4.5), Cm(5), Cm(5)]
)
doc.add_paragraph()

# ── 四、剩余 28 个 ──
doc.add_heading('四、剩余 28 个 ACC tab — 尚未做', level=1)
doc.add_paragraph(
    '前端 accTabs 里仍有 28 个 tab 未实现后端，调用会返回 404 或前端兜底空列表。'
)
doc.add_paragraph()

doc.add_heading('4.1 HR 人事（8 个）', level=2)
add_table(doc,
    ['前端 tab.api', '业务', 'ACC 源', '建议方案'],
    [
        ['employees', '员工管理', 'Employee.php (62KB)', '新表 acc_employees，复用模板'],
        ['wages', '工资发放', '（无独立类）', '新表 acc_wages，按月汇总员工薪酬'],
        ['attendances', '考勤管理', 'Attence.php', '新表 acc_attendances'],
        ['socials', '社保缴纳', '（无）', '新表 acc_socials'],
        ['social-persons', '社保人员', '（无）', '新表 acc_social_persons'],
        ['funds', '公积金缴纳', '（无）', '新表 acc_funds'],
        ['fund-persons', '公积金人员', '（无）', '新表 acc_fund_persons'],
        ['commission-rules', '提成规则', 'Commission.php', '新表 acc_commission_rules'],
    ],
    col_widths=[Cm(3.5), Cm(3), Cm(4), Cm(5.5)]
)
doc.add_paragraph()

doc.add_heading('4.2 物流扩展（8 个）', level=2)
add_table(doc,
    ['前端 tab.api', '业务', '复用现有？', '说明'],
    [
        ['stowages', '配载管理', '复用 stowages 表（020 已建）', '需建 /api/acc/stowages 视图（已有 /api/customer-api/stowages/sync 签名版）'],
        ['transits', '转运管理', '新表 acc_transits', ''],
        ['ports', '港口管理', '复用 stowage_ports（020 已建）', ''],
        ['dispatches', '上门揽收', 'Dispatch.php → 新表 acc_dispatches', ''],
        ['forecasts', '预报包裹', '新表 acc_forecasts', ''],
        ['tracks', '轨迹项目', 'tracking_events 已有，加字典表 acc_track_items', ''],
        ['stowage-categories', '配载分类', '复用 stowage_categories（020 已建）', ''],
        ['stowage-steps', '配载步骤', '新表 acc_stowage_steps', ''],
    ],
    col_widths=[Cm(3.5), Cm(2.5), Cm(5), Cm(5)]
)
doc.add_paragraph()

doc.add_heading('4.3 客户/产品（7 个）', level=2)
add_table(doc,
    ['前端 tab.api', '业务', '说明'],
    [
        ['channel-accounts', '渠道账号', '新表 acc_channel_accounts，关联 channels'],
        ['products', '价格表', '复用 rate_cards / services'],
        ['product-items', '品名管理', '新表 acc_product_items'],
        ['potentials', '潜在客户', '新表 acc_potentials'],
        ['sold-tos', '收件地址库', '复用 addresses 或新表 acc_sold_tos'],
        ['notices', '客户通知', 'ClientNotice.php → 新表 acc_notices'],
        ['quick-orders', '快速下单', 'orders 视图 + 简化创建端点'],
    ],
    col_widths=[Cm(3.5), Cm(3), Cm(9.5)]
)
doc.add_paragraph()

doc.add_heading('4.4 杂项（5 个）', level=2)
add_table(doc,
    ['前端 tab.api', '业务', '说明'],
    [
        ['zones', '价格分区', '聚合 rate_card_lines.zone_code 视图'],
        ['logistics-interfaces', '物流接口', '新表 acc_logistics_interfaces，记录第三方对接配置'],
        ['tasks', '定时任务', '新表 acc_scheduled_tasks 或复用 background_jobs'],
        ['templates', '消息模板', '复用 invoice_templates 或新表 acc_message_templates'],
        ['commissions', '员工提成', 'Commission.php → 新表 acc_commissions，关联 commission-rules'],
    ],
    col_widths=[Cm(3.5), Cm(3), Cm(9.5)]
)
doc.add_paragraph()

# ── 五、5 框架 ──
doc.add_heading('五、基础设施 / 5 框架（全部就绪）', level=1)
add_table(doc,
    ['框架', 'Java 类', '能力'],
    [
        ['审核流', 'framework.audit.AuditService',
            'audit / undoAudit / batchAudit / history；写不可变 audit_events；覆盖 33 张业务表'],
        ['状态机', 'framework.statemachine.StateMachineRegistry',
            '6 套实体规则（audit/orders/shipments/charges/customer_invoices）；非法转移抛 ApiException'],
        ['级联校验', 'framework.cascade.CascadeChecker',
            '20+ 类实体的删除阻塞规则；删 banks/customers/orders 等前自动扫描'],
        ['字段权限', 'framework.fieldgate.FieldGate',
            '33 个实体的"审核后白名单"；rejected 字段返回前端'],
        ['汇率快照', 'framework.money.MoneySnapshotService',
            '落库 freeze rate 到 exchange_rate_snapshots，避免日后金额漂移'],
    ],
    col_widths=[Cm(2.5), Cm(5.5), Cm(8)]
)
doc.add_paragraph()

# ── 六、DB 迁移 ──
doc.add_heading('六、DB 迁移文件 — 25 个', level=1)
add_table(doc,
    ['迁移文件', '内容', '应用状态'],
    [
        ['001-018 + 016/017 seed', '主基础架构（多租户/RLS/orders/shipments/charges 等）+ 客户外部 API 种子', '已应用'],
        ['019_acc_labels_schema.sql', 'label_files + relabel_no_map', '已应用'],
        ['020_acc_stowage_schema.sql', 'stowages + stowage_categories + stowage_ports + cartons.stowage_id', '已应用'],
        ['021_acc_audit_framework.sql', 'audit_events + 给 14 张表加 audit 列 + exchange_rate_snapshots', '已应用'],
        ['022_acc_master_data_dicts.sql', '6 字典表 + warehouses/return_orders audit 列', '已应用'],
        ['023_acc_exceptions_and_finance.sql', 'acc_collects / detains / asks / reparations / fees / fines', '已应用'],
        ['024_acc_finance_txns_and_dicts.sql', 'acc_finance_txns（共享表）+ 2 字典', '已应用'],
        ['025_acc_finance_mgmt.sql', '7 资金管理表 + financial_accounts audit 列', '已应用'],
    ],
    col_widths=[Cm(5), Cm(8), Cm(3)]
)
doc.add_paragraph()

# ── 七、前端 UI ──
doc.add_heading('七、前端 UI 集成', level=1)
para(doc, '前端代码（apps/web/src/App.vue, 3500+ 行）已经做的改造：')
bullet(doc, '路径基础对齐：/health → /api/health；19 处 ACC fetch → apiFetch（注入 Bearer）')
bullet(doc, '审核状态列：每个 ACC tab 列表多了"审核状态"列，三色 badge（红已审核 / 橙已反审 / 灰待审核）')
bullet(doc, '行视觉：审核行底色变浅红，hover 显示审核人 + 时间')
bullet(doc, '按钮智能切换：AUDITED 行隐藏编辑/删除，只显示反审按钮；PENDING/UNAUDITED 行可改可删')
bullet(doc, '批量审核：顶部勾选 + 批量按钮，调 /batch-audit')
bullet(doc, '后端拒绝时（字段闸 / 审核锁 / 级联阻塞），顶部消息显示 ACC 风格中文提示')
bullet(doc, 'System tab：12 个 sys 模块中能对上 admin 的 4 个正常显示，其余标"未上线"')
bullet(doc, 'Dashboard：/api/finance/dashboard、/branches、/dashboard/health 三端点已建')
doc.add_paragraph()

# ── 八、启动 / 关闭命令 ──
doc.add_heading('八、启动 / 关闭命令', level=1)
para(doc, '启动顺序：', bold=True)
code_block(doc,
    'cd /Users/chaowang/新航线/xqt-saas\n\n'
    '# 1) Docker Desktop\nopen -a Docker\n\n'
    '# 2) Postgres + Redis\ndocker compose -f infra/docker-compose.yml up -d\n\n'
    '# 3) 后端\n'
    'cd apps/backend && env -u DEBUG \\\n'
    '  API_PORT=18103 \\\n'
    '  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xqt_saas \\\n'
    '  SPRING_DATASOURCE_USERNAME=xqt SPRING_DATASOURCE_PASSWORD=xqt_dev_password \\\n'
    '  REDIS_HOST=localhost REDIS_PORT=6379 \\\n'
    '  JWT_SECRET=local-dev-secret-not-for-production-do-not-use \\\n'
    '  TENANT_DEFAULT_CODE=xqt \\\n'
    '  nohup ./mvnw -q spring-boot:run > /tmp/xqt-backend.log 2>&1 &\n\n'
    '# 4) 前端\n'
    'WEB_PORT=5173 SPRING_API_PORT=18103 \\\n'
    '  nohup npm run dev > /tmp/xqt-web.log 2>&1 &'
)
para(doc, '浏览器：http://localhost:5173    账号：xqt / admin / Admin@123456')
doc.add_paragraph()
para(doc, '关闭：', bold=True)
code_block(doc,
    'kill $(lsof -ti :5173) 2>/dev/null\n'
    'kill $(lsof -ti :18103) 2>/dev/null\n'
    'docker compose -f infra/docker-compose.yml down       # 保留数据\n'
    '# docker compose -f infra/docker-compose.yml down -v  # 清空数据卷'
)
doc.add_paragraph()

# ── 九、测试 ──
doc.add_heading('九、测试覆盖', level=1)
para(doc, '后端：./mvnw test —— 71/71 全绿', bold=True)
bullet(doc, 'CustomerApiContractTests — 13 个：客户外部 API 行为锁定')
bullet(doc, 'AccStatusMappingTests — 3 个：状态码映射')
bullet(doc, 'SignatureValidatorTests — 6 个：签名算法')
bullet(doc, 'LabelContractTests — 7 个：面单生成 / 换标 / hasSingle 分页')
bullet(doc, 'PdfPageExtractorTests — 6 个：PDFBox 页面提取与缩放')
bullet(doc, 'PublicTrackingServiceTests — 4 个：公开 Track.php')
bullet(doc, 'StowageServiceTests — 11 个：配载同步 11 项校验')
bullet(doc, 'StateMachineRegistryTests / FieldGateTests — 8 个：框架行为')
bullet(doc, '其它（ApiContractTests / XqtBackendApplicationTests）— 13 个')

output = '/Users/chaowang/新航线/xqt-saas/docs/ACC-API-完成状态报告.docx'
doc.save(output)
print(f"OK saved to {output}")
