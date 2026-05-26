"""剩余 28 ACC tab 三人分工计划 — 生成 docx."""
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


def code_block(doc, text):
    p = doc.add_paragraph(text)
    for run in p.runs:
        run.font.name = 'Consolas'
        run.font.size = Pt(9)


# ─────────────────────────────────────────────────────────────────────────────
doc = Document()

style = doc.styles['Normal']
style.font.name = 'Microsoft YaHei'
style.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
style.font.size = Pt(10)

# ── 标题 ──
title = doc.add_heading('ACC 剩余 28 tab — 3 人 2 天 分工计划', level=0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('生成日期：2026-05-26  ·  完成截止：2026-05-28 EOD')
r.italic = True
r.font.size = Pt(9)
r.font.color.rgb = RGBColor.from_string('666666')

doc.add_paragraph()

# ── 总览 ──
doc.add_heading('一、分工概览', level=1)
add_table(doc,
    ['工程师', '主战域', 'tab 数', '难度评估', '估时'],
    [
        ['工程师 A', 'HR 人事 + 提成', '9', '1 个大型 + 7 个中小', '14h (2 × 7h)'],
        ['工程师 B', '物流扩展 + 价格分区', '9', '1 个中大 + 8 个中小', '13h (2 × 6.5h)'],
        ['工程师 C', '客户产品 + 系统杂项', '10', '2 个中大 + 8 个中小', '14h (2 × 7h)'],
        ['合计', '', '28', '', '~41 人时'],
    ],
    col_widths=[Cm(2.5), Cm(5), Cm(2), Cm(4), Cm(2.5)]
)
doc.add_paragraph()

# ── 范式 ──
doc.add_heading('二、统一开发范式（所有人遵守）', level=1)
para(doc, '基础设施已就绪，每个 tab 走相同的 10 步：', bold=True)
bullet(doc, '建 DB 表（如需）：写 db/migrations/0XX_xxx.sql + RLS + audit 列；docker compose 挂载')
bullet(doc, '应用迁移：docker exec -i xqt-postgres psql -U xqt -d xqt_saas < db/migrations/0XX_xxx.sql')
bullet(doc, '建 Controller：复制 AccCustomersController.java 改路径/SQL/project()')
bullet(doc, 'list 加 audit_status/audited_at/audit_name 三列；project() 暴露这三个字段')
bullet(doc, 'update 用 FieldGate；delete 加审核锁 + CascadeChecker.checkBeforeDelete()')
bullet(doc, '汇率类（金额 + currency 字段）调 MoneySnapshotService.snapshot()')
bullet(doc, '注册：AuditService.AUDITABLE_ENTITIES、FieldGate.init()、CascadeChecker.init()、AccAuditController.TAB_TO_TABLE')
bullet(doc, '编译：./mvnw -DskipTests compile')
bullet(doc, '重启后端，curl 跑 list/create/audit/undo 四步联通测试')
bullet(doc, '把完成的 tab 在 docs/acc-tab-migration-plan.md §0 加进已完成清单')
doc.add_paragraph()
para(doc, '参考模板：apps/backend/src/main/java/com/xqt/saas/acc/AccCustomersController.java',
     italic=True, color='666666', size=9)
para(doc, '前端无需改动（路径自动匹配 accTabs[].api，审核按钮共享）。',
     italic=True, color='666666', size=9)
doc.add_paragraph()

# ── 工程师 A ──
doc.add_heading('三、工程师 A — HR 人事 + 提成（9 tab）', level=1)
para(doc, '主战域：员工生命周期、薪酬、考勤、社保公积金、提成规则。'
          'employees 是最复杂的一个（ACC Employee.php 62KB）。', italic=True, color='555555', size=9)
doc.add_paragraph()

add_table(doc,
    ['Tab', '建议表名', '复用？', '估时', '执行天'],
    [
        ['employees', 'acc_employees', '新表', '3.0h', 'Day 1'],
        ['attendances', 'acc_attendances', '新表', '1.5h', 'Day 1'],
        ['wages', 'acc_wages', '新表（含 audit 流双签）', '2.0h', 'Day 1'],
        ['commission-rules', 'acc_commission_rules', '新表', '1.5h', 'Day 2'],
        ['commissions', 'acc_commissions', '新表（含汇率快照）', '2.0h', 'Day 2'],
        ['socials', 'acc_socials', '新表', '1.0h', 'Day 2'],
        ['social-persons', 'acc_social_persons', '新表（关联 socials）', '1.0h', 'Day 2'],
        ['funds', 'acc_funds', '新表（同 socials 结构）', '1.0h', 'Day 2'],
        ['fund-persons', 'acc_fund_persons', '新表（关联 funds）', '1.0h', 'Day 2'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(3.5), Cm(2), Cm(2.5)]
)
doc.add_paragraph()

para(doc, '关键 join / 级联：', bold=True)
bullet(doc, 'employees ↔ wages / attendances / commissions / social-persons / fund-persons —— 删员工前级联检查这 5 类')
bullet(doc, 'wages 涉及金额，需要 MoneySnapshotService.snapshot() 锁汇率')
bullet(doc, 'social-persons / fund-persons 是关联表，没有 FK 业务约束，简单 CRUD')
bullet(doc, 'commission-rules / commissions 见前端 accColumns: percent / amount / sales / profit')
doc.add_paragraph()

para(doc, '建表 SQL 草稿（acc_employees + acc_wages 关键字段）：', bold=True)
code_block(doc,
    '-- acc_employees: 关键字段\n'
    'create table acc_employees (\n'
    '  id uuid primary key default gen_random_uuid(),\n'
    '  tenant_id uuid not null references tenants(id),\n'
    '  emp_no text not null,\n'
    '  name text not null,\n'
    '  gender text check (gender in (\'M\',\'F\')),\n'
    '  mobile text,\n'
    '  branch_id uuid references organizations(id),\n'
    '  department_id uuid references organizations(id),\n'
    '  position text,\n'
    '  status text default \'ACTIVE\' check (status in (\'ACTIVE\',\'LEFT\',\'PROBATION\')),\n'
    '  entry_date date,\n'
    '  -- + audit 四件套 + RLS\n'
    '  unique (tenant_id, emp_no)\n'
    ');\n\n'
    '-- acc_wages\n'
    'create table acc_wages (\n'
    '  id uuid primary key default gen_random_uuid(),\n'
    '  tenant_id uuid not null references tenants(id),\n'
    '  employee_id uuid not null references acc_employees(id),\n'
    '  the_month char(7) not null,  -- yyyy-MM\n'
    '  basic numeric(14,2), bonus numeric(14,2), commission numeric(14,2),\n'
    '  deduction numeric(14,2), total numeric(14,2),\n'
    '  currency char(3) default \'CNY\',\n'
    '  -- + audit 四件套\n'
    '  unique (tenant_id, employee_id, the_month)\n'
    ');'
)
doc.add_paragraph()

# ── 工程师 B ──
doc.add_heading('四、工程师 B — 物流扩展 + 价格分区（9 tab）', level=1)
para(doc, '主战域：配载 / 转运 / 港口 / 上门揽收 / 预报 / 轨迹 / 价格分区。'
          '其中 stowages 因为已经有 customer-api signed 版本（020 表已建），仅需新建 /api/acc/stowages 视图层。',
     italic=True, color='555555', size=9)
doc.add_paragraph()

add_table(doc,
    ['Tab', '建议表名', '复用？', '估时', '执行天'],
    [
        ['stowages', 'stowages（已有，020）', '✅ 复用', '1.5h', 'Day 1'],
        ['stowage-categories', 'stowage_categories（已有）', '✅ 复用', '0.8h', 'Day 1'],
        ['ports', 'stowage_ports（已有，020）', '✅ 复用', '0.8h', 'Day 1'],
        ['stowage-steps', 'acc_stowage_steps', '新表', '1.0h', 'Day 1'],
        ['transits', 'acc_transits', '新表（含金额+汇率快照）', '2.0h', 'Day 1'],
        ['dispatches', 'acc_dispatches', '新表（上门揽收）', '2.0h', 'Day 2'],
        ['forecasts', 'acc_forecasts', '新表（预报包裹）', '1.5h', 'Day 2'],
        ['tracks', 'acc_track_items', '新表（字典 + tracking_events join）', '1.5h', 'Day 2'],
        ['zones', '聚合 rate_card_lines 视图', '✅ 复用', '1.5h', 'Day 2'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(3.5), Cm(2), Cm(2.5)]
)
doc.add_paragraph()

para(doc, '注意：', bold=True)
bullet(doc, 'stowages 已经有 /api/customer-api/stowages/sync 完整业务，新 /api/acc/stowages 是后台维护视图，复用 stowages 表的 status / category 等字段')
bullet(doc, 'transits 是物流转运段，需要 MoneySnapshotService（关税 + 成本）')
bullet(doc, 'zones 是 rate_card_lines.zone_code 的聚合，建议建 SQL VIEW 或直接在 controller 里聚合')
bullet(doc, 'tracks（轨迹项目）是 ACC 旧 Track_Item 表，作为字典维护"待收取/已签入/转仓中"等标签，运行时 tracking_events.raw_status 引用')
doc.add_paragraph()

# ── 工程师 C ──
doc.add_heading('五、工程师 C — 客户产品 + 系统杂项（10 tab）', level=1)
para(doc, '主战域：客户细分（潜在客户、收件地址）、产品/品名、客户通知、第三方接口配置、定时任务、消息模板。'
          'products 是中大型（关联 rate_cards + services）。',
     italic=True, color='555555', size=9)
doc.add_paragraph()

add_table(doc,
    ['Tab', '建议表名', '复用？', '估时', '执行天'],
    [
        ['channel-accounts', 'acc_channel_accounts', '新表（FK channels + partners）', '1.5h', 'Day 1'],
        ['products', '复用 rate_cards + services', '✅ 复用', '2.5h', 'Day 1'],
        ['product-items', 'acc_product_items', '新表', '1.0h', 'Day 1'],
        ['sold-tos', '复用 addresses 或 acc_sold_tos', '混合', '1.5h', 'Day 1'],
        ['potentials', 'acc_potentials', '新表', '1.0h', 'Day 1'],
        ['notices', 'acc_notices', '新表（客户通知 + 已读/未读）', '1.5h', 'Day 2'],
        ['quick-orders', 'orders 视图', '✅ 视图', '1.0h', 'Day 2'],
        ['logistics-interfaces', 'acc_logistics_interfaces', '新表（第三方对接配置）', '1.5h', 'Day 2'],
        ['tasks', '复用 background_jobs 或 acc_scheduled_tasks', '混合', '1.5h', 'Day 2'],
        ['templates', '复用 invoice_templates 或 acc_message_templates', '混合', '1.0h', 'Day 2'],
    ],
    col_widths=[Cm(3.5), Cm(5), Cm(3.5), Cm(2), Cm(2.5)]
)
doc.add_paragraph()

para(doc, '注意：', bold=True)
bullet(doc, 'products 关键 join：rate_cards + service_channel_links + services；前端列 name/code/channelName/supplierName/isOpen')
bullet(doc, 'quick-orders 是 orders 的简化视图（order_entry_type=\'QUICK_ORDER\' 或全部 orders 简化展示），只读，无 audit')
bullet(doc, 'logistics-interfaces 是 ACC 旧版本里第三方物流插件的配置表（API Key / 启用状态），需要新表')
bullet(doc, 'tasks / templates 可优先复用现有 background_jobs / invoice_templates；不够再加新表')
doc.add_paragraph()

# ── 验收标准 ──
doc.add_heading('六、统一验收标准（每个 tab 必过）', level=1)
para(doc, '工程师自测脚本（每个 tab 完成后跑）：', bold=True)
code_block(doc,
    'TOKEN=$(curl -s -X POST http://localhost:18103/api/auth/login -H \'Content-Type: application/json\' \\\n'
    '  -d \'{"tenantCode":"xqt","username":"admin","password":"Admin@123456"}\' \\\n'
    '  | python3 -c "import json,sys;print(json.load(sys.stdin)[\'data\'][\'token\'])")\n\n'
    'TAB=<your-tab-name>\n\n'
    '# 1) list\ncurl -s -H "Authorization: Bearer $TOKEN" \\\n'
    '  "http://localhost:18103/api/acc/$TAB?page=1&pageSize=2" | python3 -m json.tool\n\n'
    '# 2) create\nNEW=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \\\n'
    '  -H \'Content-Type: application/json\' -d \'{<your json>}\' \\\n'
    '  "http://localhost:18103/api/acc/$TAB")\nID=$(echo "$NEW" | python3 -c "import json,sys;print(json.load(sys.stdin)[\'id\'])")\n\n'
    '# 3) audit\ncurl -s -X POST -H "Authorization: Bearer $TOKEN" \\\n'
    '  "http://localhost:18103/api/acc/$TAB/$ID/audit-biz"\n\n'
    '# 4) try update locked field — 应返回 BAD_REQUEST + 中文报错\n'
    'curl -s -X PUT -H "Authorization: Bearer $TOKEN" \\\n'
    '  -H \'Content-Type: application/json\' -d \'{<locked field>}\' \\\n'
    '  "http://localhost:18103/api/acc/$TAB/$ID"\n\n'
    '# 5) undo + delete\ncurl -s -X POST -H "Authorization: Bearer $TOKEN" \\\n'
    '  "http://localhost:18103/api/acc/$TAB/$ID/undo-biz"\ncurl -s -X DELETE -H "Authorization: Bearer $TOKEN" \\\n'
    '  "http://localhost:18103/api/acc/$TAB/$ID"'
)
doc.add_paragraph()

para(doc, '验收 checklist（每个 tab）：', bold=True)
bullet(doc, 'list 返回带 auditStatus / auditedAt / auditName 三个字段')
bullet(doc, 'POST 创建成功并返回 id')
bullet(doc, 'POST {id}/audit-biz 把 audit_status 从 PENDING → AUDITED')
bullet(doc, '审核后 PUT 改"锁定字段"，返回 BAD_REQUEST 含中文报错')
bullet(doc, '审核后 DELETE，返回 BAD_REQUEST "请先反审"')
bullet(doc, 'POST {id}/undo-biz 把 audit_status 反回 UNAUDITED')
bullet(doc, '反审后 DELETE 成功（或被级联拒，根据业务）')
bullet(doc, 'audit_events 表能查到 AUDIT + UNDO_AUDIT 两条事件')
bullet(doc, '前端 /api/acc/{tab} 路径在 5173 proxy 下能正常访问（带 Bearer）')
doc.add_paragraph()

# ── 集成 ──
doc.add_heading('七、协作守则', level=1)
bullet(doc, '每人在分支 feat/acc-{name}-{date} 上工作，每日 EOD 合并到 main')
bullet(doc, '迁移文件号严格按序递增（026 / 027 / ...）：先在群里同步抢号，避免冲突')
bullet(doc, '改框架文件（AuditService / FieldGate / CascadeChecker / AccAuditController）合并前互相 review，避免互相覆盖')
bullet(doc, '每天结束前在 docs/acc-tab-migration-plan.md §0 把自己完成的 tab 加到清单')
bullet(doc, '遇到模板覆盖不了的场景（多张表 / 复杂状态机）—— 找 lead 而不是自己发明')
bullet(doc, '所有 SQL 错误信息保留 ACC 原中文（"客户已审核，字段不可修改: code；请先反审"）')
doc.add_paragraph()

# ── 时间表 ──
doc.add_heading('八、详细时间表', level=1)
add_table(doc,
    ['时间', 'A (HR)', 'B (物流)', 'C (客户产品)'],
    [
        ['Day 1 上午', 'employees', 'stowages + stowage-categories', 'channel-accounts + products'],
        ['Day 1 下午', 'attendances + wages', 'ports + stowage-steps + transits', 'product-items + sold-tos + potentials'],
        ['Day 1 EOD', '3 tab 完成', '5 tab 完成', '5 tab 完成'],
        ['Day 2 上午', 'commission-rules + commissions', 'dispatches + forecasts', 'notices + quick-orders + logistics-interfaces'],
        ['Day 2 下午', 'socials + social-persons + funds + fund-persons', 'tracks + zones', 'tasks + templates'],
        ['Day 2 EOD', '9 tab 完成', '9 tab 完成', '10 tab 完成'],
    ],
    col_widths=[Cm(3), Cm(4.5), Cm(4.5), Cm(5)]
)
doc.add_paragraph()

# ── 启动命令 ──
doc.add_heading('九、本地环境启动命令', level=1)
code_block(doc,
    'cd /Users/chaowang/新航线/xqt-saas\n\n'
    '# Docker\nopen -a Docker\ndocker compose -f infra/docker-compose.yml up -d\n\n'
    '# 后端\ncd apps/backend && env -u DEBUG \\\n'
    '  API_PORT=18103 \\\n'
    '  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/xqt_saas \\\n'
    '  SPRING_DATASOURCE_USERNAME=xqt SPRING_DATASOURCE_PASSWORD=xqt_dev_password \\\n'
    '  REDIS_HOST=localhost REDIS_PORT=6379 \\\n'
    '  JWT_SECRET=local-dev-secret-not-for-production-do-not-use \\\n'
    '  TENANT_DEFAULT_CODE=xqt \\\n'
    '  nohup ./mvnw -q spring-boot:run > /tmp/xqt-backend.log 2>&1 &\n\n'
    '# 前端\ncd ../.. && WEB_PORT=5173 SPRING_API_PORT=18103 \\\n'
    '  nohup npm run dev > /tmp/xqt-web.log 2>&1 &\n\n'
    '# 验证\ncurl -s http://localhost:18103/api/health\n'
    '# 浏览器：http://localhost:5173    账号：xqt / admin / Admin@123456'
)

# ── 提交 ──
output = '/Users/chaowang/新航线/xqt-saas/docs/ACC-剩余28tab-3人2天分工.docx'
doc.save(output)
print(f"OK saved to {output}")
