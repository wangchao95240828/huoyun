import type { FastifyInstance } from "fastify";
import { bearerToken, createAuthToken, requireAuth, sessionHash, verifyAuthToken, verifyPassword } from "../auth.js";

interface LoginBody {
  tenantCode?: string;
  username: string;
  password: string;
}

export async function authRoutes(app: FastifyInstance) {
  app.post("/api/auth/login", async (request, reply) => {
    const body = request.body as LoginBody;
    const tenantCode = body.tenantCode || process.env.TENANT_DEFAULT_CODE || "xqt";
    const username = body.username?.trim();
    const password = body.password ?? "";

    if (!username || !password) {
      return reply.code(400).send({ ok: false, error: "用户名和密码不能为空" });
    }

    const client = await app.pg.connect();
    try {
      await client.query("SELECT set_config('app.service_role', 'true', false)");
      const { rows } = await client.query(
        `SELECT
           t.id AS tenant_id,
           t.code AS tenant_code,
           u.id AS user_id,
           u.username,
           u.email,
           u.display_name,
           u.password_hash,
           u.status,
           u.locked_until,
           COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::text[]) AS roles,
           COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::text[]) AS permissions
         FROM tenants t
         JOIN users u ON u.tenant_id = t.id
         LEFT JOIN user_roles ur ON ur.tenant_id = t.id AND ur.user_id = u.id
         LEFT JOIN roles r ON r.tenant_id = t.id AND r.id = ur.role_id
         LEFT JOIN role_permissions rp ON rp.tenant_id = t.id AND rp.role_id = r.id
         LEFT JOIN permissions p ON p.tenant_id = t.id AND p.id = rp.permission_id
         WHERE t.code = $1
           AND (u.username = $2 OR u.email = $2)
         GROUP BY t.id, t.code, u.id, u.username, u.email, u.display_name, u.password_hash, u.status, u.locked_until
         LIMIT 1`,
        [tenantCode, username]
      );

      const row = rows[0];
      const failure = async (reason: string) => {
        await client.query(
          `INSERT INTO auth_login_events (tenant_id, user_id, username, success, failure_reason, ip, user_agent)
           VALUES ($1, $2, $3, false, $4, $5, $6)`,
          [row?.tenant_id ?? null, row?.user_id ?? null, username, reason, request.ip, request.headers["user-agent"] ?? ""]
        );
        return reply.code(401).send({ ok: false, error: "用户名或密码错误" });
      };

      if (!row) return failure("USER_NOT_FOUND");
      if (row.status !== "ACTIVE") return failure("USER_NOT_ACTIVE");
      if (row.locked_until && new Date(row.locked_until).getTime() > Date.now()) return failure("USER_LOCKED");
      if (!verifyPassword(password, row.password_hash)) {
        await client.query(
          `UPDATE users
           SET failed_login_count = failed_login_count + 1,
               locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN now() + interval '15 minutes' ELSE locked_until END
           WHERE id = $1`,
          [row.user_id]
        );
        return failure("BAD_PASSWORD");
      }

      const { token, payload, expiresIn } = createAuthToken({
        userId: row.user_id,
        tenantId: row.tenant_id,
        tenantCode: row.tenant_code,
        username: row.username || row.email,
        displayName: row.display_name,
        roles: row.roles ?? [],
        permissions: row.permissions ?? [],
      });

      await client.query(
        `INSERT INTO user_sessions (tenant_id, user_id, session_hash, ip, user_agent, expires_at)
         VALUES ($1, $2, $3, $4, $5, to_timestamp($6))`,
        [row.tenant_id, row.user_id, sessionHash(payload.jti), request.ip, request.headers["user-agent"] ?? "", payload.exp]
      );
      await client.query(
        `UPDATE users
         SET last_login_at = now(), failed_login_count = 0, locked_until = NULL
         WHERE id = $1`,
        [row.user_id]
      );
      await client.query(
        `INSERT INTO auth_login_events (tenant_id, user_id, username, success, ip, user_agent)
         VALUES ($1, $2, $3, true, $4, $5)`,
        [row.tenant_id, row.user_id, username, request.ip, request.headers["user-agent"] ?? ""]
      );

      return {
        ok: true,
        token,
        expiresIn,
        user: {
          id: row.user_id,
          tenantId: row.tenant_id,
          tenantCode: row.tenant_code,
          username: row.username || row.email,
          displayName: row.display_name,
          roles: row.roles ?? [],
          permissions: row.permissions ?? [],
        },
      };
    } finally {
      await client.query("SELECT set_config('app.service_role', '', false)").catch(() => {});
      client.release();
    }
  });

  app.get("/api/auth/me", { preHandler: (request, reply) => requireAuth(app, request, reply) }, async (request) => {
    const auth = (request as any).auth;
    return { ok: true, user: auth };
  });

  app.post("/api/auth/logout", { preHandler: (request, reply) => requireAuth(app, request, reply) }, async (request) => {
    const token = bearerToken(request);
    const payload = verifyAuthToken(token);
    const client = await app.pg.connect();
    try {
      await client.query("SELECT set_config('app.service_role', 'true', false)");
      await client.query(
        `UPDATE user_sessions
         SET revoked_at = now()
         WHERE tenant_id = $1 AND user_id = $2 AND session_hash = $3`,
        [payload.tenantId, payload.userId, sessionHash(payload.jti)]
      );
      return { ok: true };
    } finally {
      await client.query("SELECT set_config('app.service_role', '', false)").catch(() => {});
      client.release();
    }
  });
}
