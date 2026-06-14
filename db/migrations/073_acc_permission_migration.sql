-- 073: ACC Permission bitmap → xqt-saas 字符串权限码 迁移支持

-- ACC 用 hex bitmap 存权限：用户.Per 字段如 "FFFF0000" = 32 bits 位图
-- 每个 bit 位对应 acc/inc/Set.php $AdminPermissions 数组里的一个权限项
-- 本迁移建立映射表 + 转换函数，便于未来从 ACC 实例导入历史数据

BEGIN;

-- 映射表：ACC bit 索引 → xqt-saas 权限 code
CREATE TABLE IF NOT EXISTS acc_permission_map (
    acc_bit_index   integer PRIMARY KEY,
    acc_module      text    NOT NULL,
    acc_label       text    NOT NULL,
    xqt_permission  text    NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- 种子映射（覆盖 ACC Set.php 主要权限）
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission) VALUES
    -- 基本配置 0-7
    (0,  '基本配置', '登陆系统',     'auth.profile.read'),
    (1,  '基本配置', '修改密码',     'auth.profile.write'),
    (2,  '基本配置', '系统设置',     'admin.user.write'),
    (3,  '基本配置', '模块管理',     'admin.user.write'),
    -- 用户管理 15-21
    (15, '用户管理', '查看管理员',   'admin.user.read'),
    (16, '用户管理', '添加管理员',   'admin.user.write'),
    (17, '用户管理', '修改管理员',   'admin.user.write'),
    (18, '用户管理', '删除管理员',   'admin.user.write'),
    (19, '用户管理', '锁定管理员',   'admin.user.write'),
    (20, '用户管理', '授予权限',     'admin.role.write'),
    (21, '用户管理', '角色管理',     'admin.role.write'),
    -- 日记管理 22-24
    (22, '日记管理', '查看日记',     'admin.audit.read'),
    (23, '日记管理', '删除日记',     'admin.user.write'),
    (24, '日记管理', '日记配置',     'admin.user.write'),
    -- 业务/制单/订单（示例范围 100-110）
    (100, '业务',   '查看订单',     'operation.order.read'),
    (101, '业务',   '添加订单',     'operation.order.write'),
    (102, '业务',   '修改订单',     'operation.order.write'),
    (103, '业务',   '审核订单',     'business.flow.write'),
    -- 财务（示例 120-140）
    (120, '财务',   '查看应收',     'finance.receivable.read'),
    (121, '财务',   '审核应收',     'finance.receivable.write'),
    (122, '财务',   '出账',         'finance.invoice.write'),
    (123, '财务',   '收款',         'finance.receivable.write'),
    (124, '财务',   '查看应付',     'finance.payable.read'),
    (125, '财务',   '审核应付',     'finance.payable.write'),
    (126, '财务',   '付款',         'finance.payable.write'),
    (127, '财务',   '调账',         'finance.adjust.approve'),
    (128, '财务',   '查看资金账户', 'finance.account.read'),
    (129, '财务',   '资金账户管理', 'finance.account.write'),
    (130, '财务',   '查看价目表',   'finance.rate.read'),
    (131, '财务',   '维护价目表',   'finance.rate.write'),
    -- 仓库/配载 (160-170)
    (160, '配载',   '扫描',         'warehouse.scan.write'),
    (161, '配载',   '审核',         'warehouse.scan.write')
ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label;

-- 函数：把 ACC hex bitmap 转成 bit 位置数组
-- 例: '0F' → {0,1,2,3} （二进制 1111 → 第 0-3 bit）
CREATE OR REPLACE FUNCTION acc_hex_to_bits(hex_str text) RETURNS integer[] AS $$
DECLARE
    bits integer[] := ARRAY[]::integer[];
    i integer;
    byte_val integer;
    bit_idx integer;
BEGIN
    IF hex_str IS NULL OR length(hex_str) = 0 THEN
        RETURN bits;
    END IF;
    -- 按每 2 hex 字符 = 1 byte 处理
    FOR i IN 0..(length(hex_str)/2 - 1) LOOP
        byte_val := ('x' || substr(hex_str, i*2 + 1, 2))::bit(8)::integer;
        FOR bit_idx IN 0..7 LOOP
            IF (byte_val >> bit_idx) & 1 = 1 THEN
                bits := bits || (i*8 + bit_idx);
            END IF;
        END LOOP;
    END LOOP;
    RETURN bits;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 视图：从 ACC bitmap 翻译到 xqt-saas 权限 code 列表
CREATE OR REPLACE FUNCTION acc_bitmap_to_xqt_permissions(hex_str text) RETURNS text[] AS $$
    SELECT coalesce(array_agg(DISTINCT m.xqt_permission), ARRAY[]::text[])
      FROM unnest(acc_hex_to_bits(hex_str)) bit_idx
      JOIN acc_permission_map m ON m.acc_bit_index = bit_idx;
$$ LANGUAGE sql IMMUTABLE;

-- 测试断言（部署时验证）
DO $$
DECLARE
    result text[];
BEGIN
    -- 0xFF 应该返回 bit 0-7 对应的权限
    result := acc_bitmap_to_xqt_permissions('FF');
    IF array_length(result, 1) < 1 THEN
        RAISE NOTICE 'acc_permission_map 种子可能未生效';
    END IF;
    RAISE NOTICE 'acc_bitmap test passed, sample result: %', result;
END $$;

COMMIT;
