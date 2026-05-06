/**
 * 系统基础设施路由 - System Infrastructure Routes
 * 认证登录、用户管理、角色权限、菜单、日志、配置、客户账号、客服、硬件、短信、工具
 */
import type { FastifyInstance } from "fastify";
import { requireAuth } from "../auth.js";
import { SystemAdapter } from "../adapters/system-adapter.js";

export async function systemRoutes(app: FastifyInstance) {
  const sys = new SystemAdapter(() => app.pg.connect());

  // Init schema on startup
  try { await sys.initSchema(); } catch { /* table may already exist */ }

  app.addHook("preHandler", async (req, reply) => {
    const path = req.url.split("?")[0];
    if (path === "/api/sys/login" || path === "/api/sys/customer-login") return;
    return requireAuth(app, req, reply);
  });

  // ══════════ Authentication ══════════

  app.post("/api/sys/login", async (req) => {
    const { username, password } = req.body as { username: string; password: string };
    const crypto = await import('crypto');
    const hash = crypto.createHash('md5').update(password).digest('hex');
    return sys.login(username, hash, req.ip);
  });

  app.post("/api/sys/change-password", async (req) => {
    const { userId, oldPassword, newPassword } = req.body as { userId: number; oldPassword: string; newPassword: string };
    const crypto = await import('crypto');
    const oldHash = crypto.createHash('md5').update(oldPassword).digest('hex');
    const newHash = crypto.createHash('md5').update(newPassword).digest('hex');
    return sys.changePassword(userId, oldHash, newHash);
  });

  // ══════════ User Management ══════════

  app.get("/api/sys/users", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listUsers(Number(q.page) || 1, Number(q.pageSize) || 50, q.keyword);
  });

  app.post("/api/sys/users", async (req) => {
    const body = req.body as any;
    const crypto = await import('crypto');
    const hash = crypto.createHash('md5').update(body.password ?? '123456').digest('hex');
    return sys.createUser({ ...body, passwordHash: hash });
  });

  app.put("/api/sys/users/:id", async (req) => {
    const { id } = req.params as { id: string };
    const body = req.body as any;
    return sys.updateUser(Number(id), body);
  });

  app.delete("/api/sys/users/:id", async (req) => {
    const { id } = req.params as { id: string };
    return sys.deleteUser(Number(id));
  });

  app.post("/api/sys/users/:id/reset-password", async (req) => {
    const { id } = req.params as { id: string };
    const { password = '123456' } = req.body as { password?: string };
    const crypto = await import('crypto');
    const hash = crypto.createHash('md5').update(password).digest('hex');
    return sys.resetPassword(Number(id), hash);
  });

  // ══════════ Roles & Permissions ══════════

  app.get("/api/sys/roles", async () => {
    return sys.listRoles();
  });

  app.post("/api/sys/roles", async (req) => {
    const { name, description, permissions } = req.body as { name: string; description: string; permissions: string[] };
    return sys.createRole(name, description ?? '', permissions ?? []);
  });

  app.put("/api/sys/roles/:id", async (req) => {
    const { id } = req.params as { id: string };
    const body = req.body as any;
    return sys.updateRole(Number(id), body);
  });

  app.delete("/api/sys/roles/:id", async (req) => {
    const { id } = req.params as { id: string };
    return sys.deleteRole(Number(id));
  });

  // ══════════ Menu Management ══════════

  app.get("/api/sys/menus", async () => {
    return sys.listMenus();
  });

  app.post("/api/sys/menus", async (req) => {
    return sys.saveMenu(req.body as any);
  });

  app.delete("/api/sys/menus/:id", async (req) => {
    const { id } = req.params as { id: string };
    return sys.deleteMenu(Number(id));
  });

  // ══════════ Operation Logs ══════════

  app.get("/api/sys/logs/operations", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listOperationLogs({
      page: Number(q.page) || 1, pageSize: Number(q.pageSize) || 50,
      userId: q.userId ? Number(q.userId) : undefined,
      module: q.module, dateFrom: q.dateFrom, dateTo: q.dateTo,
    });
  });

  // ══════════ Error Logs ══════════

  app.get("/api/sys/logs/errors", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listErrorLogs({
      page: Number(q.page) || 1, pageSize: Number(q.pageSize) || 50,
      level: q.level, dateFrom: q.dateFrom, dateTo: q.dateTo,
    });
  });

  app.post("/api/sys/logs/errors", async (req) => {
    await sys.addErrorLog(req.body as any);
    return { ok: true };
  });

  // ══════════ System Configuration ══════════

  app.get("/api/sys/configs", async (req) => {
    const q = req.query as Record<string, string>;
    if (q.group) return sys.getConfigsByGroup(q.group);
    return sys.getAllConfigs();
  });

  app.get("/api/sys/configs/:key", async (req) => {
    const { key } = req.params as { key: string };
    const value = await sys.getConfig(key);
    return { key, value };
  });

  app.post("/api/sys/configs", async (req) => {
    const { key, value, description, group } = req.body as { key: string; value: string; description?: string; group?: string };
    return sys.setConfig(key, value, description, group);
  });

  app.delete("/api/sys/configs/:key", async (req) => {
    const { key } = req.params as { key: string };
    return sys.deleteConfig(key);
  });

  // ══════════ System Info ══════════

  app.get("/api/sys/info", async () => {
    return sys.getSystemInfo();
  });

  // ══════════ Customer Accounts ══════════

  app.get("/api/sys/customer-accounts", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listCustomerAccounts(Number(q.page) || 1, Number(q.pageSize) || 50, q.keyword);
  });

  app.post("/api/sys/customer-accounts", async (req) => {
    const body = req.body as any;
    const crypto = await import('crypto');
    const hash = crypto.createHash('md5').update(body.password ?? '123456').digest('hex');
    return sys.createCustomerAccount({ ...body, passwordHash: hash });
  });

  app.put("/api/sys/customer-accounts/:id", async (req) => {
    const { id } = req.params as { id: string };
    const body = req.body as any;
    if (body.password) {
      const crypto = await import('crypto');
      body.passwordHash = crypto.createHash('md5').update(body.password).digest('hex');
    }
    return sys.updateCustomerAccount(Number(id), body);
  });

  app.post("/api/sys/customer-login", async (req) => {
    const { username, password } = req.body as { username: string; password: string };
    const crypto = await import('crypto');
    const hash = crypto.createHash('md5').update(password).digest('hex');
    return sys.customerLogin(username, hash);
  });

  // ══════════ Customer Service ══════════

  app.get("/api/sys/service/messages", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listServiceMessages(Number(q.customerId), Number(q.page) || 1, Number(q.pageSize) || 50);
  });

  app.post("/api/sys/service/messages", async (req) => {
    return sys.sendServiceMessage(req.body as any);
  });

  app.post("/api/sys/service/messages/read", async (req) => {
    const { customerId, direction } = req.body as { customerId: number; direction: number };
    return sys.markMessagesRead(customerId, direction);
  });

  app.get("/api/sys/service/unread", async () => {
    const count = await sys.getUnreadCount();
    return { count };
  });

  // ══════════ Hardware Interfaces ══════════

  app.get("/api/sys/hardware", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listHardwareConfigs(q.type);
  });

  app.post("/api/sys/hardware", async (req) => {
    return sys.saveHardwareConfig(req.body as any);
  });

  app.post("/api/sys/hardware/:id/heartbeat", async (req) => {
    const { id } = req.params as { id: string };
    return sys.hardwareHeartbeat(Number(id));
  });

  // ══════════ SMS Service ══════════

  app.post("/api/sys/sms/send", async (req) => {
    const { phone, content, template } = req.body as { phone: string; content: string; template?: string };
    return sys.sendSms(phone, content, template);
  });

  app.get("/api/sys/sms/logs", async (req) => {
    const q = req.query as Record<string, string>;
    return sys.listSmsLogs(Number(q.page) || 1, Number(q.pageSize) || 50, q.phone);
  });

  // ══════════ System Tools ══════════

  app.post("/api/sys/tools/clean-logs", async (req) => {
    const { days = 90 } = req.body as { days?: number };
    return sys.cleanOldLogs(days);
  });

  app.get("/api/sys/tools/db-stats", async () => {
    return sys.getDatabaseStats();
  });
}
