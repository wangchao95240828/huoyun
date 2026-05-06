import type { FastifyInstance } from "fastify";
import { hashPassword, requireAuth, requirePermission, type AuthContext } from "../auth.js";

async function withTenant<T>(app: FastifyInstance, auth: AuthContext, fn: (client: any) => Promise<T>): Promise<T> {
  const client = await app.pg.connect();
  try {
    await client.query("BEGIN");
    await client.query("SELECT set_config('app.current_tenant_id', $1, true)", [auth.tenantId]);
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    client.release();
  }
}

export async function adminRoutes(app: FastifyInstance) {
  app.addHook("preHandler", (request, reply) => requireAuth(app, request, reply));

  app.get(
    "/api/admin/users",
    { preHandler: requirePermission("admin.user.read") },
    async (request) => {
      const auth = (request as any).auth as AuthContext;
      return withTenant(app, auth, async (client) => {
        const { rows } = await client.query(
          `SELECT
             u.id,
             u.username,
             u.email,
             u.display_name,
             u.role_code,
             u.status,
             u.last_login_at,
             COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::text[]) AS roles
           FROM users u
           LEFT JOIN user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
           LEFT JOIN roles r ON r.tenant_id = u.tenant_id AND r.id = ur.role_id
           WHERE u.tenant_id = $1
           GROUP BY u.id
           ORDER BY u.created_at DESC`,
          [auth.tenantId]
        );
        return { ok: true, items: rows, data: rows };
      });
    }
  );

  app.post(
    "/api/admin/users",
    { preHandler: requirePermission("admin.user.write") },
    async (request, reply) => {
      const auth = (request as any).auth as AuthContext;
      const body = request.body as {
        username: string;
        email: string;
        displayName: string;
        password: string;
        roleCodes?: string[];
        status?: string;
      };

      if (!body.username || !body.email || !body.displayName || !body.password) {
        return reply.code(400).send({ ok: false, error: "username, email, displayName and password are required" });
      }

      return withTenant(app, auth, async (client) => {
        const { rows } = await client.query(
          `INSERT INTO users (tenant_id, username, email, display_name, role_code, password_hash, status)
           VALUES ($1, $2, $3, $4, $5, $6, $7)
           RETURNING id, username, email, display_name, status`,
          [
            auth.tenantId,
            body.username,
            body.email,
            body.displayName,
            body.roleCodes?.[0] ?? "OPERATOR",
            hashPassword(body.password),
            body.status ?? "ACTIVE",
          ]
        );

        if (body.roleCodes?.length) {
          await client.query(
            `INSERT INTO user_roles (tenant_id, user_id, role_id)
             SELECT $1, $2, r.id
             FROM roles r
             WHERE r.tenant_id = $1 AND r.code = ANY($3::text[])
             ON CONFLICT DO NOTHING`,
            [auth.tenantId, rows[0].id, body.roleCodes]
          );
        }

        await client.query(
          `INSERT INTO audit_logs (tenant_id, actor_id, entity_type, entity_id, action, after_data)
           VALUES ($1, $2, 'users', $3, 'CREATE', $4)`,
          [auth.tenantId, auth.userId, rows[0].id, rows[0]]
        );

        return { ok: true, user: rows[0] };
      });
    }
  );

  app.get(
    "/api/admin/roles",
    { preHandler: requirePermission("admin.role.read") },
    async (request) => {
      const auth = (request as any).auth as AuthContext;
      return withTenant(app, auth, async (client) => {
        const { rows } = await client.query(
          `SELECT
             r.id,
             r.code,
             r.name,
             r.description,
             r.system_role,
             COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::text[]) AS permissions
           FROM roles r
           LEFT JOIN role_permissions rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
           LEFT JOIN permissions p ON p.tenant_id = r.tenant_id AND p.id = rp.permission_id
           WHERE r.tenant_id = $1
           GROUP BY r.id
           ORDER BY r.system_role DESC, r.code`,
          [auth.tenantId]
        );
        return { ok: true, items: rows, data: rows };
      });
    }
  );

  app.post(
    "/api/admin/roles",
    { preHandler: requirePermission("admin.role.write") },
    async (request, reply) => {
      const auth = (request as any).auth as AuthContext;
      const body = request.body as { code: string; name: string; description?: string; permissionCodes?: string[] };
      if (!body.code || !body.name) return reply.code(400).send({ ok: false, error: "code and name are required" });

      return withTenant(app, auth, async (client) => {
        const { rows } = await client.query(
          `INSERT INTO roles (tenant_id, code, name, description)
           VALUES ($1, $2, $3, $4)
           RETURNING id, code, name, description`,
          [auth.tenantId, body.code, body.name, body.description ?? ""]
        );

        if (body.permissionCodes?.length) {
          await client.query(
            `INSERT INTO role_permissions (tenant_id, role_id, permission_id)
             SELECT $1, $2, p.id
             FROM permissions p
             WHERE p.tenant_id = $1 AND p.code = ANY($3::text[])
             ON CONFLICT DO NOTHING`,
            [auth.tenantId, rows[0].id, body.permissionCodes]
          );
        }

        await client.query(
          `INSERT INTO audit_logs (tenant_id, actor_id, entity_type, entity_id, action, after_data)
           VALUES ($1, $2, 'roles', $3, 'CREATE', $4)`,
          [auth.tenantId, auth.userId, rows[0].id, rows[0]]
        );

        return { ok: true, role: rows[0] };
      });
    }
  );

  app.get(
    "/api/admin/permissions",
    { preHandler: requirePermission("admin.role.read") },
    async (request) => {
      const auth = (request as any).auth as AuthContext;
      return withTenant(app, auth, async (client) => {
        const { rows } = await client.query(
          `SELECT id, code, name, resource, action, description
           FROM permissions
           WHERE tenant_id = $1
           ORDER BY resource, action, code`,
          [auth.tenantId]
        );
        return { ok: true, items: rows, data: rows };
      });
    }
  );
}
