/**
 * 系统基础设施适配器 - System Infrastructure Adapter
 * 覆盖：认证登录、用户管理、角色权限、菜单配置、操作日志、系统配置、
 *       硬件接口、在线客服、客户登录、系统工具
 */
type PoolClient = { query: (sql: string, params?: any[]) => Promise<{ rows: any[]; rowCount: number | null }>; release: () => void };

// ═══════════════════════════════════════════
//  Types
// ═══════════════════════════════════════════

export interface SystemUser {
  id: number;
  username: string;
  realName: string;
  email?: string;
  phone?: string;
  roleId: number;
  roleName?: string;
  branchId?: number;
  status: number; // 0=disabled, 1=active
  lastLogin?: string;
  createdAt: string;
}

export interface SystemRole {
  id: number;
  name: string;
  description?: string;
  permissions: string[]; // permission codes
  createdAt: string;
}

export interface MenuItem {
  id: number;
  parentId: number;
  name: string;
  path: string;
  icon?: string;
  sort: number;
  permission?: string;
  visible: boolean;
}

export interface OperationLog {
  id: number;
  userId: number;
  username: string;
  module: string;
  action: string;
  target?: string;
  detail?: string;
  ip?: string;
  createdAt: string;
}

export interface SystemConfig {
  key: string;
  value: string;
  description?: string;
  group: string;
}

export interface CustomerAccount {
  id: number;
  customerId: number;
  username: string;
  status: number;
  lastLogin?: string;
}

export interface ServiceMessage {
  id: number;
  customerId: number;
  direction: number; // 0=customer->staff, 1=staff->customer
  content: string;
  staffId?: number;
  readAt?: string;
  createdAt: string;
}

// ═══════════════════════════════════════════
//  SQL Schema (PostgreSQL)
// ═══════════════════════════════════════════

const SCHEMA_SQL = `
-- 用户管理
CREATE TABLE IF NOT EXISTS sys_users (
  id SERIAL PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  real_name VARCHAR(50) NOT NULL DEFAULT '',
  email VARCHAR(100) DEFAULT '',
  phone VARCHAR(20) DEFAULT '',
  role_id INTEGER NOT NULL DEFAULT 0,
  branch_id INTEGER DEFAULT NULL,
  status SMALLINT NOT NULL DEFAULT 1,
  last_login TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 角色权限
CREATE TABLE IF NOT EXISTS sys_roles (
  id SERIAL PRIMARY KEY,
  name VARCHAR(50) UNIQUE NOT NULL,
  description VARCHAR(200) DEFAULT '',
  permissions JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 菜单管理
CREATE TABLE IF NOT EXISTS sys_menus (
  id SERIAL PRIMARY KEY,
  parent_id INTEGER NOT NULL DEFAULT 0,
  name VARCHAR(50) NOT NULL,
  path VARCHAR(200) NOT NULL DEFAULT '',
  icon VARCHAR(50) DEFAULT '',
  sort INTEGER NOT NULL DEFAULT 0,
  permission VARCHAR(100) DEFAULT '',
  visible BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 操作日志
CREATE TABLE IF NOT EXISTS sys_operation_logs (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL DEFAULT 0,
  username VARCHAR(50) NOT NULL DEFAULT '',
  module VARCHAR(50) NOT NULL DEFAULT '',
  action VARCHAR(50) NOT NULL DEFAULT '',
  target VARCHAR(200) DEFAULT '',
  detail TEXT DEFAULT '',
  ip VARCHAR(45) DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 错误日志
CREATE TABLE IF NOT EXISTS sys_error_logs (
  id SERIAL PRIMARY KEY,
  level VARCHAR(10) NOT NULL DEFAULT 'error',
  module VARCHAR(50) DEFAULT '',
  message TEXT NOT NULL,
  stack TEXT DEFAULT '',
  context JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 系统配置
CREATE TABLE IF NOT EXISTS sys_configs (
  key VARCHAR(100) PRIMARY KEY,
  value TEXT NOT NULL DEFAULT '',
  description VARCHAR(200) DEFAULT '',
  group_name VARCHAR(50) NOT NULL DEFAULT 'general',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 客户登录账号
CREATE TABLE IF NOT EXISTS sys_customer_accounts (
  id SERIAL PRIMARY KEY,
  customer_id INTEGER NOT NULL,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  api_key VARCHAR(64) DEFAULT '',
  status SMALLINT NOT NULL DEFAULT 1,
  last_login TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 在线客服消息
CREATE TABLE IF NOT EXISTS sys_service_messages (
  id SERIAL PRIMARY KEY,
  customer_id INTEGER NOT NULL,
  direction SMALLINT NOT NULL DEFAULT 0,
  content TEXT NOT NULL,
  staff_id INTEGER DEFAULT NULL,
  read_at TIMESTAMPTZ DEFAULT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 硬件接口配置
CREATE TABLE IF NOT EXISTS sys_hardware_configs (
  id SERIAL PRIMARY KEY,
  type VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  config JSONB NOT NULL DEFAULT '{}',
  status SMALLINT NOT NULL DEFAULT 1,
  last_heartbeat TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SMS发送记录
CREATE TABLE IF NOT EXISTS sys_sms_logs (
  id SERIAL PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  template VARCHAR(50) DEFAULT '',
  status SMALLINT NOT NULL DEFAULT 0,
  result TEXT DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 初始数据
INSERT INTO sys_roles (name, description, permissions) VALUES
  ('超级管理��', '系统最高权限', '["*"]'),
  ('财务主管', '财务审核权限', '["finance.*","report.*"]'),
  ('操作员', '日常操作权限', '["order.*","shipment.*","warehouse.*"]'),
  ('客服', '客户服务权限', '["customer.view","order.view","service.*"]'),
  ('业务员', '销售业务权限', '["customer.*","order.create","commission.view"]')
ON CONFLICT DO NOTHING;

INSERT INTO sys_users (username, password_hash, real_name, role_id) VALUES
  ('admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 1)
ON CONFLICT DO NOTHING;

INSERT INTO sys_configs (key, value, description, group_name) VALUES
  ('company.name', '新航线国际物流', '公司名称', 'company'),
  ('company.phone', '', '联系电话', 'company'),
  ('company.address', '', '公司地址', 'company'),
  ('system.version', '1.0.0', '系统版本', 'system'),
  ('system.maintenance', 'false', '维护模式', 'system'),
  ('sms.provider', '', '短信服务商', 'sms'),
  ('sms.api_key', '', '短信API密钥', 'sms'),
  ('scale.port', '', '电子秤串口', 'hardware'),
  ('scale.baudrate', '9600', '电子秤波特率', 'hardware'),
  ('attendance.ip', '', '考勤机IP', 'hardware'),
  ('attendance.port', '4370', '考勤机端口', 'hardware')
ON CONFLICT DO NOTHING;
`;

// ═══════════════════════════════════════════
//  Adapter Class
// ═══════════════════════════════════════════

export class SystemAdapter {
  private getClient: () => Promise<PoolClient>;

  constructor(getClient: () => Promise<PoolClient>) {
    this.getClient = getClient;
  }

  async initSchema(): Promise<void> {
    const client = await this.getClient();
    try {
      await client.query(SCHEMA_SQL);
    } finally {
      client.release();
    }
  }

  // ═══════════════════════════════════════════
  //  认证登录 - Authentication
  // ═══════════════════════════════════════════

  async login(username: string, passwordHash: string, ip?: string): Promise<{ ok: boolean; user?: SystemUser; token?: string; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `SELECT u.id, u.username, u.real_name, u.email, u.phone, u.role_id, u.branch_id, u.status, r.name as role_name, r.permissions
         FROM sys_users u LEFT JOIN sys_roles r ON r.id=u.role_id
         WHERE u.username=$1 AND u.password_hash=$2`, [username, passwordHash]
      );
      if (rows.length === 0) return { ok: false, error: '用户名或密码错误' };
      const u = rows[0];
      if (u.status !== 1) return { ok: false, error: '账号已禁用' };

      // Update last login
      await client.query(`UPDATE sys_users SET last_login=NOW() WHERE id=$1`, [u.id]);

      // Log operation
      await client.query(
        `INSERT INTO sys_operation_logs (user_id,username,module,action,ip,created_at) VALUES ($1,$2,'auth','login',$3,NOW())`,
        [u.id, u.username, ip ?? '']
      );

      // Simple token (in production use JWT)
      const crypto = await import('crypto');
      const token = crypto.randomBytes(32).toString('hex');

      return {
        ok: true,
        user: {
          id: u.id, username: u.username, realName: u.real_name,
          email: u.email, phone: u.phone, roleId: u.role_id,
          roleName: u.role_name, branchId: u.branch_id,
          status: u.status, lastLogin: new Date().toISOString(), createdAt: '',
        },
        token,
      };
    } finally { client.release(); }
  }

  async changePassword(userId: number, oldHash: string, newHash: string): Promise<{ ok: boolean; error?: string }> {
    const client = await this.getClient();
    try {
      const { rowCount } = await client.query(
        `UPDATE sys_users SET password_hash=$1, updated_at=NOW() WHERE id=$2 AND password_hash=$3`,
        [newHash, userId, oldHash]
      );
      if (!rowCount) return { ok: false, error: '原密码错误' };
      return { ok: true };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  用户管理 - User Management
  // ═══════════════════════════════════════════

  async listUsers(page = 1, pageSize = 50, keyword?: string): Promise<{ data: SystemUser[]; total: number }> {
    const client = await this.getClient();
    try {
      const conditions: string[] = [];
      const values: any[] = [];
      if (keyword) { conditions.push(`(u.username ILIKE $${values.length + 1} OR u.real_name ILIKE $${values.length + 1})`); values.push(`%${keyword}%`); }
      const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';

      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_users u ${where}`, values);
      const total = Number(countRes.rows[0].cnt);

      values.push(pageSize, (page - 1) * pageSize);
      const { rows } = await client.query(
        `SELECT u.id, u.username, u.real_name, u.email, u.phone, u.role_id, u.branch_id, u.status, u.last_login, u.created_at, r.name as role_name
         FROM sys_users u LEFT JOIN sys_roles r ON r.id=u.role_id ${where}
         ORDER BY u.id LIMIT $${values.length - 1} OFFSET $${values.length}`, values
      );
      return { data: rows.map((r: any) => ({ id: r.id, username: r.username, realName: r.real_name, email: r.email, phone: r.phone, roleId: r.role_id, roleName: r.role_name, branchId: r.branch_id, status: r.status, lastLogin: r.last_login, createdAt: r.created_at })), total };
    } finally { client.release(); }
  }

  async createUser(data: { username: string; passwordHash: string; realName: string; email?: string; phone?: string; roleId: number; branchId?: number }): Promise<{ ok: boolean; id?: number; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `INSERT INTO sys_users (username, password_hash, real_name, email, phone, role_id, branch_id)
         VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
        [data.username, data.passwordHash, data.realName, data.email ?? '', data.phone ?? '', data.roleId, data.branchId ?? null]
      );
      return { ok: true, id: rows[0].id };
    } catch (e: any) {
      if (e.code === '23505') return { ok: false, error: '用户名已存在' };
      return { ok: false, error: e.message };
    } finally { client.release(); }
  }

  async updateUser(id: number, data: Partial<{ realName: string; email: string; phone: string; roleId: number; branchId: number; status: number }>): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      const sets: string[] = [];
      const vals: any[] = [];
      if (data.realName !== undefined) { vals.push(data.realName); sets.push(`real_name=$${vals.length}`); }
      if (data.email !== undefined) { vals.push(data.email); sets.push(`email=$${vals.length}`); }
      if (data.phone !== undefined) { vals.push(data.phone); sets.push(`phone=$${vals.length}`); }
      if (data.roleId !== undefined) { vals.push(data.roleId); sets.push(`role_id=$${vals.length}`); }
      if (data.branchId !== undefined) { vals.push(data.branchId); sets.push(`branch_id=$${vals.length}`); }
      if (data.status !== undefined) { vals.push(data.status); sets.push(`status=$${vals.length}`); }
      if (sets.length === 0) return { ok: true };
      sets.push('updated_at=NOW()');
      vals.push(id);
      await client.query(`UPDATE sys_users SET ${sets.join(',')} WHERE id=$${vals.length}`, vals);
      return { ok: true };
    } finally { client.release(); }
  }

  async deleteUser(id: number): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(`DELETE FROM sys_users WHERE id=$1 AND id!=1`, [id]);
      return { ok: true };
    } finally { client.release(); }
  }

  async resetPassword(id: number, newHash: string): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(`UPDATE sys_users SET password_hash=$1, updated_at=NOW() WHERE id=$2`, [newHash, id]);
      return { ok: true };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  角色权限 - Roles & Permissions
  // ═══════════════════════════════════════════

  async listRoles(): Promise<SystemRole[]> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT id, name, description, permissions, created_at FROM sys_roles ORDER BY id`);
      return rows.map((r: any) => ({ id: r.id, name: r.name, description: r.description, permissions: r.permissions ?? [], createdAt: r.created_at }));
    } finally { client.release(); }
  }

  async createRole(name: string, description: string, permissions: string[]): Promise<{ ok: boolean; id?: number; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `INSERT INTO sys_roles (name, description, permissions) VALUES ($1,$2,$3) RETURNING id`,
        [name, description, JSON.stringify(permissions)]
      );
      return { ok: true, id: rows[0].id };
    } catch (e: any) { return { ok: false, error: e.message }; }
    finally { client.release(); }
  }

  async updateRole(id: number, data: { name?: string; description?: string; permissions?: string[] }): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      const sets: string[] = [];
      const vals: any[] = [];
      if (data.name) { vals.push(data.name); sets.push(`name=$${vals.length}`); }
      if (data.description !== undefined) { vals.push(data.description); sets.push(`description=$${vals.length}`); }
      if (data.permissions) { vals.push(JSON.stringify(data.permissions)); sets.push(`permissions=$${vals.length}`); }
      if (sets.length === 0) return { ok: true };
      sets.push('updated_at=NOW()');
      vals.push(id);
      await client.query(`UPDATE sys_roles SET ${sets.join(',')} WHERE id=$${vals.length}`, vals);
      return { ok: true };
    } finally { client.release(); }
  }

  async deleteRole(id: number): Promise<{ ok: boolean; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT COUNT(*) as cnt FROM sys_users WHERE role_id=$1`, [id]);
      if (Number(rows[0].cnt) > 0) return { ok: false, error: '该角色下存在用户，无法删除' };
      await client.query(`DELETE FROM sys_roles WHERE id=$1 AND id!=1`, [id]);
      return { ok: true };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  菜单管理 - Menu Management
  // ═══════════════════════════════════════════

  async listMenus(): Promise<MenuItem[]> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT * FROM sys_menus ORDER BY sort, id`);
      return rows.map((r: any) => ({ id: r.id, parentId: r.parent_id, name: r.name, path: r.path, icon: r.icon, sort: r.sort, permission: r.permission, visible: r.visible }));
    } finally { client.release(); }
  }

  async saveMenu(data: { id?: number; parentId: number; name: string; path: string; icon?: string; sort?: number; permission?: string; visible?: boolean }): Promise<{ ok: boolean; id?: number }> {
    const client = await this.getClient();
    try {
      if (data.id) {
        await client.query(
          `UPDATE sys_menus SET parent_id=$1, name=$2, path=$3, icon=$4, sort=$5, permission=$6, visible=$7 WHERE id=$8`,
          [data.parentId, data.name, data.path, data.icon ?? '', data.sort ?? 0, data.permission ?? '', data.visible ?? true, data.id]
        );
        return { ok: true, id: data.id };
      }
      const { rows } = await client.query(
        `INSERT INTO sys_menus (parent_id, name, path, icon, sort, permission, visible) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
        [data.parentId, data.name, data.path, data.icon ?? '', data.sort ?? 0, data.permission ?? '', data.visible ?? true]
      );
      return { ok: true, id: rows[0].id };
    } finally { client.release(); }
  }

  async deleteMenu(id: number): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(`DELETE FROM sys_menus WHERE id=$1 OR parent_id=$1`, [id]);
      return { ok: true };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  操作日志 - Operation Logs
  // ═══════════════════════════════════════════

  async addOperationLog(data: { userId: number; username: string; module: string; action: string; target?: string; detail?: string; ip?: string }): Promise<void> {
    const client = await this.getClient();
    try {
      await client.query(
        `INSERT INTO sys_operation_logs (user_id,username,module,action,target,detail,ip) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [data.userId, data.username, data.module, data.action, data.target ?? '', data.detail ?? '', data.ip ?? '']
      );
    } finally { client.release(); }
  }

  async listOperationLogs(params: { page?: number; pageSize?: number; userId?: number; module?: string; dateFrom?: string; dateTo?: string }): Promise<{ data: OperationLog[]; total: number }> {
    const client = await this.getClient();
    try {
      const conds: string[] = [];
      const vals: any[] = [];
      if (params.userId) { vals.push(params.userId); conds.push(`user_id=$${vals.length}`); }
      if (params.module) { vals.push(params.module); conds.push(`module=$${vals.length}`); }
      if (params.dateFrom) { vals.push(params.dateFrom); conds.push(`created_at>=$${vals.length}`); }
      if (params.dateTo) { vals.push(params.dateTo + ' 23:59:59'); conds.push(`created_at<=$${vals.length}`); }
      const where = conds.length ? `WHERE ${conds.join(' AND ')}` : '';

      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_operation_logs ${where}`, vals);
      const total = Number(countRes.rows[0].cnt);

      const pageSize = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * pageSize;
      vals.push(pageSize, offset);
      const { rows } = await client.query(
        `SELECT * FROM sys_operation_logs ${where} ORDER BY id DESC LIMIT $${vals.length - 1} OFFSET $${vals.length}`, vals
      );
      return {
        data: rows.map((r: any) => ({ id: r.id, userId: r.user_id, username: r.username, module: r.module, action: r.action, target: r.target, detail: r.detail, ip: r.ip, createdAt: r.created_at })),
        total
      };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  错误日志 - Error Logs
  // ═══════════════════════════════════════════

  async addErrorLog(data: { level?: string; module?: string; message: string; stack?: string; context?: any }): Promise<void> {
    const client = await this.getClient();
    try {
      await client.query(
        `INSERT INTO sys_error_logs (level,module,message,stack,context) VALUES ($1,$2,$3,$4,$5)`,
        [data.level ?? 'error', data.module ?? '', data.message, data.stack ?? '', JSON.stringify(data.context ?? {})]
      );
    } finally { client.release(); }
  }

  async listErrorLogs(params: { page?: number; pageSize?: number; level?: string; dateFrom?: string; dateTo?: string }): Promise<{ data: any[]; total: number }> {
    const client = await this.getClient();
    try {
      const conds: string[] = [];
      const vals: any[] = [];
      if (params.level) { vals.push(params.level); conds.push(`level=$${vals.length}`); }
      if (params.dateFrom) { vals.push(params.dateFrom); conds.push(`created_at>=$${vals.length}`); }
      if (params.dateTo) { vals.push(params.dateTo + ' 23:59:59'); conds.push(`created_at<=$${vals.length}`); }
      const where = conds.length ? `WHERE ${conds.join(' AND ')}` : '';

      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_error_logs ${where}`, vals);
      const total = Number(countRes.rows[0].cnt);

      const pageSize = params.pageSize ?? 50;
      vals.push(pageSize, ((params.page ?? 1) - 1) * pageSize);
      const { rows } = await client.query(
        `SELECT * FROM sys_error_logs ${where} ORDER BY id DESC LIMIT $${vals.length - 1} OFFSET $${vals.length}`, vals
      );
      return { data: rows, total };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  系统配置 - System Configuration
  // ═══════════════════════════════════════════

  async getConfig(key: string): Promise<string | null> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT value FROM sys_configs WHERE key=$1`, [key]);
      return rows[0]?.value ?? null;
    } finally { client.release(); }
  }

  async getConfigsByGroup(group: string): Promise<SystemConfig[]> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT key, value, description, group_name FROM sys_configs WHERE group_name=$1 ORDER BY key`, [group]);
      return rows.map((r: any) => ({ key: r.key, value: r.value, description: r.description, group: r.group_name }));
    } finally { client.release(); }
  }

  async getAllConfigs(): Promise<SystemConfig[]> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`SELECT key, value, description, group_name FROM sys_configs ORDER BY group_name, key`);
      return rows.map((r: any) => ({ key: r.key, value: r.value, description: r.description, group: r.group_name }));
    } finally { client.release(); }
  }

  async setConfig(key: string, value: string, description?: string, group?: string): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(
        `INSERT INTO sys_configs (key, value, description, group_name, updated_at) VALUES ($1,$2,$3,$4,NOW())
         ON CONFLICT (key) DO UPDATE SET value=$2, description=COALESCE($3, sys_configs.description), updated_at=NOW()`,
        [key, value, description ?? '', group ?? 'general']
      );
      return { ok: true };
    } finally { client.release(); }
  }

  async deleteConfig(key: string): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(`DELETE FROM sys_configs WHERE key=$1`, [key]);
      return { ok: true };
    } finally { client.release(); }
  }

  async getSystemInfo(): Promise<any> {
    const client = await this.getClient();
    try {
      const userCount = await client.query(`SELECT COUNT(*) as cnt FROM sys_users WHERE status=1`);
      const roleCount = await client.query(`SELECT COUNT(*) as cnt FROM sys_roles`);
      const logCount = await client.query(`SELECT COUNT(*) as cnt FROM sys_operation_logs WHERE created_at > NOW() - INTERVAL '24 hours'`);
      const errorCount = await client.query(`SELECT COUNT(*) as cnt FROM sys_error_logs WHERE created_at > NOW() - INTERVAL '24 hours'`);
      const version = await client.query(`SELECT value FROM sys_configs WHERE key='system.version'`);

      return {
        version: version.rows[0]?.value ?? '1.0.0',
        users: Number(userCount.rows[0].cnt),
        roles: Number(roleCount.rows[0].cnt),
        todayLogs: Number(logCount.rows[0].cnt),
        todayErrors: Number(errorCount.rows[0].cnt),
        serverTime: new Date().toISOString(),
        nodeVersion: process.version,
        platform: process.platform,
        uptime: process.uptime(),
      };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  客户登录 - Customer Login Management
  // ═══════════════════════════════════════════

  async listCustomerAccounts(page = 1, pageSize = 50, keyword?: string): Promise<{ data: CustomerAccount[]; total: number }> {
    const client = await this.getClient();
    try {
      const conds: string[] = [];
      const vals: any[] = [];
      if (keyword) { vals.push(`%${keyword}%`); conds.push(`username ILIKE $${vals.length}`); }
      const where = conds.length ? `WHERE ${conds.join(' AND ')}` : '';
      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_customer_accounts ${where}`, vals);
      vals.push(pageSize, (page - 1) * pageSize);
      const { rows } = await client.query(
        `SELECT id, customer_id, username, status, last_login, created_at FROM sys_customer_accounts ${where} ORDER BY id DESC LIMIT $${vals.length - 1} OFFSET $${vals.length}`, vals
      );
      return {
        data: rows.map((r: any) => ({ id: r.id, customerId: r.customer_id, username: r.username, status: r.status, lastLogin: r.last_login })),
        total: Number(countRes.rows[0].cnt)
      };
    } finally { client.release(); }
  }

  async createCustomerAccount(data: { customerId: number; username: string; passwordHash: string; apiKey?: string }): Promise<{ ok: boolean; id?: number; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `INSERT INTO sys_customer_accounts (customer_id, username, password_hash, api_key) VALUES ($1,$2,$3,$4) RETURNING id`,
        [data.customerId, data.username, data.passwordHash, data.apiKey ?? '']
      );
      return { ok: true, id: rows[0].id };
    } catch (e: any) {
      if (e.code === '23505') return { ok: false, error: '用户名已存在' };
      return { ok: false, error: e.message };
    } finally { client.release(); }
  }

  async updateCustomerAccount(id: number, data: { status?: number; passwordHash?: string; apiKey?: string }): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      const sets: string[] = [];
      const vals: any[] = [];
      if (data.status !== undefined) { vals.push(data.status); sets.push(`status=$${vals.length}`); }
      if (data.passwordHash) { vals.push(data.passwordHash); sets.push(`password_hash=$${vals.length}`); }
      if (data.apiKey !== undefined) { vals.push(data.apiKey); sets.push(`api_key=$${vals.length}`); }
      if (sets.length === 0) return { ok: true };
      vals.push(id);
      await client.query(`UPDATE sys_customer_accounts SET ${sets.join(',')} WHERE id=$${vals.length}`, vals);
      return { ok: true };
    } finally { client.release(); }
  }

  async customerLogin(username: string, passwordHash: string): Promise<{ ok: boolean; customerId?: number; error?: string }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `SELECT id, customer_id, status FROM sys_customer_accounts WHERE username=$1 AND password_hash=$2`, [username, passwordHash]
      );
      if (rows.length === 0) return { ok: false, error: '用户名或密码错误' };
      if (rows[0].status !== 1) return { ok: false, error: '账号已禁用' };
      await client.query(`UPDATE sys_customer_accounts SET last_login=NOW() WHERE id=$1`, [rows[0].id]);
      return { ok: true, customerId: rows[0].customer_id };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  在线客服 - Customer Service Messages
  // ═══════════════════════════════════════════

  async listServiceMessages(customerId: number, page = 1, pageSize = 50): Promise<{ data: ServiceMessage[]; total: number }> {
    const client = await this.getClient();
    try {
      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_service_messages WHERE customer_id=$1`, [customerId]);
      const offset = (page - 1) * pageSize;
      const { rows } = await client.query(
        `SELECT * FROM sys_service_messages WHERE customer_id=$1 ORDER BY id DESC LIMIT $2 OFFSET $3`,
        [customerId, pageSize, offset]
      );
      return {
        data: rows.map((r: any) => ({ id: r.id, customerId: r.customer_id, direction: r.direction, content: r.content, staffId: r.staff_id, readAt: r.read_at, createdAt: r.created_at })),
        total: Number(countRes.rows[0].cnt)
      };
    } finally { client.release(); }
  }

  async sendServiceMessage(data: { customerId: number; direction: number; content: string; staffId?: number }): Promise<{ ok: boolean; id?: number }> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `INSERT INTO sys_service_messages (customer_id, direction, content, staff_id) VALUES ($1,$2,$3,$4) RETURNING id`,
        [data.customerId, data.direction, data.content, data.staffId ?? null]
      );
      return { ok: true, id: rows[0].id };
    } finally { client.release(); }
  }

  async markMessagesRead(customerId: number, direction: number): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(
        `UPDATE sys_service_messages SET read_at=NOW() WHERE customer_id=$1 AND direction=$2 AND read_at IS NULL`,
        [customerId, direction]
      );
      return { ok: true };
    } finally { client.release(); }
  }

  async getUnreadCount(staffId?: number): Promise<number> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(
        `SELECT COUNT(DISTINCT customer_id) as cnt FROM sys_service_messages WHERE direction=0 AND read_at IS NULL`
      );
      return Number(rows[0].cnt);
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  硬件接口 - Hardware Interfaces
  // ═══════════════════════════════════════════

  async listHardwareConfigs(type?: string): Promise<any[]> {
    const client = await this.getClient();
    try {
      const cond = type ? `WHERE type=$1` : '';
      const { rows } = await client.query(`SELECT * FROM sys_hardware_configs ${cond} ORDER BY id`, type ? [type] : []);
      return rows;
    } finally { client.release(); }
  }

  async saveHardwareConfig(data: { id?: number; type: string; name: string; config: any; status?: number }): Promise<{ ok: boolean; id?: number }> {
    const client = await this.getClient();
    try {
      if (data.id) {
        await client.query(
          `UPDATE sys_hardware_configs SET name=$1, config=$2, status=$3 WHERE id=$4`,
          [data.name, JSON.stringify(data.config), data.status ?? 1, data.id]
        );
        return { ok: true, id: data.id };
      }
      const { rows } = await client.query(
        `INSERT INTO sys_hardware_configs (type, name, config, status) VALUES ($1,$2,$3,$4) RETURNING id`,
        [data.type, data.name, JSON.stringify(data.config), data.status ?? 1]
      );
      return { ok: true, id: rows[0].id };
    } finally { client.release(); }
  }

  async hardwareHeartbeat(id: number): Promise<{ ok: boolean }> {
    const client = await this.getClient();
    try {
      await client.query(`UPDATE sys_hardware_configs SET last_heartbeat=NOW() WHERE id=$1`, [id]);
      return { ok: true };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  SMS短信 - SMS Service
  // ═══════════════════════════════════════════

  async sendSms(phone: string, content: string, template?: string): Promise<{ ok: boolean; error?: string }> {
    const client = await this.getClient();
    try {
      await client.query(
        `INSERT INTO sys_sms_logs (phone, content, template, status) VALUES ($1,$2,$3,1)`,
        [phone, content, template ?? '']
      );
      return { ok: true };
    } finally { client.release(); }
  }

  async listSmsLogs(page = 1, pageSize = 50, phone?: string): Promise<{ data: any[]; total: number }> {
    const client = await this.getClient();
    try {
      const conds: string[] = [];
      const vals: any[] = [];
      if (phone) { vals.push(`%${phone}%`); conds.push(`phone ILIKE $${vals.length}`); }
      const where = conds.length ? `WHERE ${conds.join(' AND ')}` : '';
      const countRes = await client.query(`SELECT COUNT(*) as cnt FROM sys_sms_logs ${where}`, vals);
      vals.push(pageSize, (page - 1) * pageSize);
      const { rows } = await client.query(
        `SELECT * FROM sys_sms_logs ${where} ORDER BY id DESC LIMIT $${vals.length - 1} OFFSET $${vals.length}`, vals
      );
      return { data: rows, total: Number(countRes.rows[0].cnt) };
    } finally { client.release(); }
  }

  // ═══════════════════════════════════════════
  //  系统工具 - System Tools
  // ═══════════════════════════════════════════

  async cleanOldLogs(days = 90): Promise<{ deleted: number }> {
    const client = await this.getClient();
    try {
      const res = await client.query(`DELETE FROM sys_operation_logs WHERE created_at < NOW() - $1::interval`, [`${days} days`]);
      const res2 = await client.query(`DELETE FROM sys_error_logs WHERE created_at < NOW() - $1::interval`, [`${days} days`]);
      return { deleted: (res.rowCount ?? 0) + (res2.rowCount ?? 0) };
    } finally { client.release(); }
  }

  async getDatabaseStats(): Promise<any> {
    const client = await this.getClient();
    try {
      const { rows } = await client.query(`
        SELECT schemaname, relname as table_name, n_live_tup as row_count
        FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 20
      `);
      const sizeRes = await client.query(`SELECT pg_database_size(current_database()) as size`);
      return {
        tables: rows,
        databaseSize: Number(sizeRes.rows[0].size),
        databaseSizeMB: Math.round(Number(sizeRes.rows[0].size) / 1024 / 1024 * 100) / 100,
      };
    } finally { client.release(); }
  }
}
