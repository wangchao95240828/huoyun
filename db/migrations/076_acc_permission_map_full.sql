-- 076: ACC permission map full 386 entries (auto-generated from acc/inc/Set.php)

BEGIN;

INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (0, '基本配置', '登陆系统', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (1, '基本配置', '修改密码', 'auth.profile.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (2, '基本配置', '系统设置', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (3, '基本配置', '模块管理', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (4, '基本配置', '线上管理', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (5, '基本配置', '菜单管理', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (6, '基本配置', '编译模板', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (7, '基本配置', '服务器信息', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (8, '数据管理', '压缩数据库', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (9, '数据管理', '备份数据', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (10, '数据管理', '查看备份', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (11, '数据管理', '删除备份', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (12, '数据管理', '恢复备份', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (13, '数据管理', '文件校验', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (14, '数据管理', '执行SQL指令', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (15, '用户管理', '查看管理员', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (16, '用户管理', '添加管理员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (17, '用户管理', '修改管理员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (18, '用户管理', '删除管理员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (19, '用户管理', '锁定管理员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (20, '用户管理', '授予权限', 'admin.role.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (21, '用户管理', '角色管理', 'admin.role.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (22, '日记管理', '查看日记', 'admin.audit.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (23, '日记管理', '删除日记', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (24, '日记管理', '日记配置', 'admin.audit.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (25, '邮件模板', '查看邮件', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (26, '邮件模板', '添加邮件', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (27, '邮件模板', '修改邮件', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (28, '邮件模板', '删除邮件', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (29, '打印模板', '查看模板', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (30, '打印模板', '添加模板', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (31, '打印模板', '修改模板', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (32, '打印模板', '删除模板', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (33, '接口管理', '查看接口', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (34, '接口管理', '添加接口', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (35, '接口管理', '修改接口', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (36, '接口管理', '删除接口', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (37, '微信管理', '管理微信菜单', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (38, '微信管理', '管理微信用户', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (39, '微信管理', '查看微信消息', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (40, '微信管理', '群发微信消息', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (41, '分店管理', '查看分店', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (42, '分店管理', '添加分店', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (43, '分店管理', '修改分店', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (44, '分店管理', '删除分店', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (45, '地区管理', '查看地区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (46, '地区管理', '添加地区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (47, '地区管理', '修改地区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (48, '地区管理', '删除地区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (49, '邮编管理', '查看邮编', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (50, '邮编管理', '添加邮编', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (51, '邮编管理', '修改邮编', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (52, '邮编管理', '删除邮编', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (53, '轨迹管理', '查看轨迹', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (54, '轨迹管理', '添加轨迹', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (55, '轨迹管理', '修改轨迹', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (56, '轨迹管理', '删除轨迹', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (57, '产品管理', '查看销售产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (58, '产品管理', '添加销售产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (59, '产品管理', '修改销售产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (60, '产品管理', '删除销售产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (61, '产品管理', '查看成本产品', 'finance.payable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (62, '产品管理', '添加成本产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (63, '产品管理', '修改成本产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (64, '产品管理', '删除成本产品', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (65, '产品分区', '查看分区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (66, '产品分区', '添加分区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (67, '产品分区', '修改分区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (68, '产品分区', '删除分区', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (69, '渠道账号', '查看账号', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (70, '渠道账号', '添加账号', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (71, '渠道账号', '修改账号', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (72, '渠道账号', '删除账号', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (73, '杂费类型', '查看杂费类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (74, '杂费类型', '添加杂费类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (75, '杂费类型', '修改杂费类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (76, '杂费类型', '删除杂费类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (77, '杂费套餐', '查看杂费套餐', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (78, '杂费套餐', '添加杂费套餐', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (79, '杂费套餐', '修改杂费套餐', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (80, '杂费套餐', '删除杂费套餐', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (81, '燃油费用', '查看燃油', 'finance.rate.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (82, '燃油费用', '添加燃油', 'finance.rate.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (83, '燃油费用', '修改燃油', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (84, '燃油费用', '删除燃油', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (85, '港口管理', '查看港口', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (86, '港口管理', '添加港口', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (87, '港口管理', '修改港口', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (88, '港口管理', '删除港口', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (89, '品名管理', '查看品名', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (90, '品名管理', '添加品名', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (91, '品名管理', '修改品名', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (92, '品名管理', '删除品名', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (93, '收支类型', '查看收支类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (94, '收支类型', '添加收支类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (95, '收支类型', '修改收支类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (96, '收支类型', '删除收支类型', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (97, '配载分类', '查看配载分类', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (98, '配载分类', '添加配载分类', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (99, '配载分类', '修改配载分类', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (100, '配载分类', '删除配载分类', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (101, '仓库管理', '查看仓库', 'operation.order.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (102, '仓库管理', '添加仓库', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (103, '仓库管理', '修改仓库', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (104, '仓库管理', '删除仓库', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (105, '进 口 商', '查看进口商', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (106, '进 口 商', '添加进口商', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (107, '进 口 商', '修改进口商', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (108, '进 口 商', '删除进口商', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (109, '员工管理', '查看员工', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (110, '员工管理', '添加员工', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (111, '员工管理', '修改员工', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (112, '员工管理', '删除员工', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (113, '部门管理', '查看部门', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (114, '部门管理', '添加部门', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (115, '部门管理', '修改部门', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (116, '部门管理', '删除部门', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (117, '考勤管理', '查看考勤', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (118, '考勤管理', '添加考勤', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (119, '考勤管理', '修改考勤', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (120, '考勤管理', '删除考勤', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (121, '考勤管理', '审核考勤', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (122, '考勤管理', '撤消考勤', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (123, '社保人员', '查看社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (124, '社保人员', '添加社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (125, '社保人员', '修改社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (126, '社保人员', '删除社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (127, '社保人员', '停缴社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (128, '社保人员', '恢复社保人员', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (129, '银行账户', '查看账户', 'finance.account.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (130, '银行账户', '添加账户', 'finance.account.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (131, '银行账户', '修改账户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (132, '银行账户', '删除账户', 'finance.account.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (133, '往来账户', '查看往来账户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (134, '往来账户', '添加往来账户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (135, '往来账户', '修改往来账户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (136, '往来账户', '删除往来账户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (137, '币种管理', '查看币种', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (138, '币种管理', '添加币种', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (139, '币种管理', '修改币种', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (140, '币种管理', '删除币种', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (141, '资金转账', '查看转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (142, '资金转账', '添加转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (143, '资金转账', '修改转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (144, '资金转账', '删除转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (145, '资金转账', '审核转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (146, '资金转账', '撤消转账', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (147, '资金借贷', '查看借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (148, '资金借贷', '添加借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (149, '资金借贷', '修改借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (150, '资金借贷', '删除借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (151, '资金借贷', '审核借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (152, '资金借贷', '撤消借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (153, '资金借贷', '出纳借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (154, '资金借贷', '撤回借贷', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (155, '固定资产', '查看资产', 'finance.account.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (156, '固定资产', '添加资产', 'finance.account.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (157, '固定资产', '修改资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (158, '固定资产', '删除资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (159, '固定资产', '审核资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (160, '固定资产', '撤消资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (161, '固定资产', '出纳资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (162, '固定资产', '撤回资产', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (163, '固定资产', '查看报表', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (164, '分红入股', '查看分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (165, '分红入股', '添加分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (166, '分红入股', '修改分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (167, '分红入股', '删除分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (168, '分红入股', '审核分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (169, '分红入股', '撤消分红', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (170, '费用管理', '查看费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (171, '费用管理', '添加费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (172, '费用管理', '修改费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (173, '费用管理', '删除费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (174, '费用管理', '审核费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (175, '费用管理', '撤消费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (176, '费用管理', '出纳费用', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (177, '周期费用', '查看周期', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (178, '周期费用', '添加周期', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (179, '周期费用', '修改周期', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (180, '周期费用', '删除周期', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (181, '周期费用', '添加续费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (182, '周期费用', '撤消续费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (183, '工资发放', '查看工资', 'admin.user.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (184, '工资发放', '添加工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (185, '工资发放', '修改工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (186, '工资发放', '删除工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (187, '工资发放', '审核工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (188, '工资发放', '撤消工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (189, '工资发放', '出纳工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (190, '工资发放', '撤回工资', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (191, '员工提成', '查看提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (192, '员工提成', '添加提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (193, '员工提成', '修改提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (194, '员工提成', '删除提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (195, '员工提成', '审核提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (196, '员工提成', '撤消提成', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (197, '员工提成', '提成规则', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (198, '社保管理', '查看社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (199, '社保管理', '添加社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (200, '社保管理', '修改社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (201, '社保管理', '删除社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (202, '社保管理', '审核社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (203, '社保管理', '撤消社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (204, '社保管理', '出纳社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (205, '社保管理', '撤回社保', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (206, '客户管理', '查看客户', 'finance.receivable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (207, '客户管理', '添加客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (208, '客户管理', '修改客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (209, '客户管理', '删除客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (210, '客户管理', '获取价格', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (211, '客户管理', '调整账务', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (212, '客户管理', '佣金管理', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (213, '客户账单', '查看客户账单', 'finance.receivable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (214, '客户账单', '添加客户账单', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (215, '客户账单', '修改客户账单', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (216, '客户账单', '删除客户账单', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (217, '客户账单', '审核客户账单', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (218, '客户账单', '撤消客户账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (219, '客户调账', '查看客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (220, '客户调账', '添加客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (221, '客户调账', '修改客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (222, '客户调账', '删除客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (223, '客户调账', '审核客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (224, '客户调账', '撤消客户调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (225, '客户返利', '查看客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (226, '客户返利', '添加客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (227, '客户返利', '修改客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (228, '客户返利', '删除客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (229, '客户返利', '审核客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (230, '客户返利', '撤消客户返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (231, '客户罚款', '查看客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (232, '客户罚款', '添加客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (233, '客户罚款', '修改客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (234, '客户罚款', '删除客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (235, '客户罚款', '审核客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (236, '客户罚款', '撤消客户罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (237, '客户退款', '查看客户退款', 'finance.receivable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (238, '客户退款', '添加客户退款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (239, '客户退款', '修改客户退款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (240, '客户退款', '删除客户退款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (241, '客户退款', '审核客户退款', 'finance.payable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (242, '客户退款', '撤消客户退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (243, '潜在客户', '查看潜在客户', 'finance.receivable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (244, '潜在客户', '添加潜在客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (245, '潜在客户', '修改潜在客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (246, '潜在客户', '删除潜在客户', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (247, '潜在客户', '分配潜在客户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (248, '潜在客户', '撤消潜在客户', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (249, '物流商管理', '查看物流', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (250, '物流商管理', '添加物流', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (251, '物流商管理', '修改物流', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (252, '物流商管理', '删除物流', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (253, '物流商管理', '获取价格', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (254, '物流商账单', '查看物流账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (255, '物流商账单', '添加物流账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (256, '物流商账单', '修改物流账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (257, '物流商账单', '删除物流账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (258, '物流商账单', '审核物流账单', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (259, '物流商账单', '撤消物流账单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (260, '物流商调账', '查看物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (261, '物流商调账', '添加物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (262, '物流商调账', '修改物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (263, '物流商调账', '删除物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (264, '物流商调账', '审核物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (265, '物流商调账', '撤消物流调账', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (266, '物流商返利', '查看物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (267, '物流商返利', '添加物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (268, '物流商返利', '修改物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (269, '物流商返利', '删除物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (270, '物流商返利', '审核物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (271, '物流商返利', '撤消物流返利', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (272, '物流商罚款', '查看物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (273, '物流商罚款', '添加物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (274, '物流商罚款', '修改物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (275, '物流商罚款', '删除物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (276, '物流商罚款', '审核物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (277, '物流商罚款', '撤消物流罚款', 'finance.adjust.approve')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (278, '收款管理', '查看收款', 'finance.receivable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (279, '收款管理', '添加收款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (280, '收款管理', '修改收款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (281, '收款管理', '删除收款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (282, '收款管理', '审核收款', 'finance.receivable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (283, '收款管理', '撤消收款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (284, '付款管理', '查看付款', 'finance.payable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (285, '付款管理', '添加付款', 'finance.payable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (286, '付款管理', '修改付款', 'finance.payable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (287, '付款管理', '删除付款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (288, '付款管理', '审核付款', 'finance.payable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (289, '付款管理', '撤消付款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (290, '物流商退款', '查看物流退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (291, '物流商退款', '添加物流退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (292, '物流商退款', '修改物流退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (293, '物流商退款', '删除物流退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (294, '物流商退款', '审核物流退款', 'finance.payable.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (295, '物流商退款', '撤消物流退款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (296, '来款认领', '查看来款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (297, '来款认领', '添加来款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (298, '来款认领', '认领来款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (299, '来款认领', '删除来款', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (300, '快件管理', '查看快件', 'operation.order.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (301, '快件管理', '添加快件', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (302, '快件管理', '修改快件', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (303, '快件管理', '删除快件', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (304, '快件管理', '审核快件', 'business.flow.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (305, '快件管理', '撤消快件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (306, '快件管理', '批量操作', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (307, '在线制单', '查看制单', 'operation.order.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (308, '在线制单', '添加制单', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (309, '在线制单', '修改制单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (310, '在线制单', '删除制单', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (311, '在线制单', '提交制单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (312, '在线制单', '撤消制单', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (313, '在线制单', '制单详情', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (314, '调度管理', '查看调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (315, '调度管理', '添加调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (316, '调度管理', '修改调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (317, '调度管理', '删除调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (318, '调度管理', '安排调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (319, '调度管理', '完成调度', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (320, '装箱单管理', '查看装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (321, '装箱单管理', '添加装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (322, '装箱单管理', '修改装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (323, '装箱单管理', '删除装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (324, '装箱单管理', '审核装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (325, '装箱单管理', '撤消装箱单', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (326, '配载管理', '查看配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (327, '配载管理', '添加配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (328, '配载管理', '修改配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (329, '配载管理', '删除配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (330, '配载管理', '审核配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (331, '配载管理', '撤消配载', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (332, '出货管理', '查看出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (333, '出货管理', '添加出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (334, '出货管理', '修改出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (335, '出货管理', '删除出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (336, '出货管理', '审核出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (337, '出货管理', '撤消出货', 'warehouse.scan.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (338, '问题件', '查看问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (339, '问题件', '创建问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (340, '问题件', '修改问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (341, '问题件', '删除问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (342, '问题件', '回复问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (343, '问题件', '关闭问题', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (344, '扣件管理', '查看扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (345, '扣件管理', '添加扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (346, '扣件管理', '修改扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (347, '扣件管理', '删除扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (348, '扣件管理', '处理扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (349, '扣件管理', '解除扣件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (350, '退件管理', '查看退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (351, '退件管理', '添加退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (352, '退件管理', '修改退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (353, '退件管理', '删除退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (354, '退件管理', '处理退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (355, '退件管理', '撤消退件', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (356, '赔偿管理', '查看赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (357, '赔偿管理', '添加赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (358, '赔偿管理', '修改赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (359, '赔偿管理', '删除赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (360, '赔偿管理', '审核赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (361, '赔偿管理', '撤消赔偿', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (362, '运费核算', '查看运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (363, '运费核算', '添加运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (364, '运费核算', '修改运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (365, '运费核算', '删除运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (366, '运费核算', '审核运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (367, '运费核算', '撤消运费', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (368, '成本核算', '查看成本', 'finance.payable.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (369, '成本核算', '添加成本', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (370, '成本核算', '修改成本', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (371, '成本核算', '删除成本', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (372, '成本核算', '审核成本', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (373, '成本核算', '撤消成本', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (374, '在线客服', '查看客服', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (375, '在线客服', '添加客服', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (376, '在线客服', '修改客服', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (377, '在线客服', '删除客服', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (378, '通知公告', '查看通知', 'auth.profile.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (379, '通知公告', '添加通知', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (380, '通知公告', '修改通知', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (381, '通知公告', '删除通知', 'admin.user.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (382, '客户预报', '查看预报', 'operation.order.read')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (383, '客户预报', '添加预报', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (384, '客户预报', '修改预报', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;
INSERT INTO acc_permission_map (acc_bit_index, acc_module, acc_label, xqt_permission)
  VALUES (385, '客户预报', '删除预报', 'operation.order.write')
  ON CONFLICT (acc_bit_index) DO UPDATE SET
    xqt_permission = EXCLUDED.xqt_permission,
    acc_label = EXCLUDED.acc_label,
    acc_module = EXCLUDED.acc_module;

COMMIT;
