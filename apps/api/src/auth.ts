import crypto from "node:crypto";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";

const TOKEN_TTL_SECONDS = Number(process.env.AUTH_TOKEN_TTL_SECONDS ?? 8 * 60 * 60);

export interface AuthContext {
  userId: string;
  tenantId: string;
  tenantCode: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  exp: number;
  jti: string;
}

function secret() {
  return process.env.JWT_SECRET ?? "replace-me-in-real-env";
}

function b64(value: unknown) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function sign(data: string) {
  return crypto.createHmac("sha256", secret()).update(data).digest("base64url");
}

export function hashPassword(password: string, salt = crypto.randomBytes(16).toString("base64url")) {
  const iterations = 210000;
  const hash = crypto.pbkdf2Sync(password, salt, iterations, 32, "sha256").toString("base64url");
  return `pbkdf2$sha256$${iterations}$${salt}$${hash}`;
}

export function verifyPassword(password: string, encoded: string | null | undefined) {
  if (!encoded) return false;
  const [scheme, digest, iterationsRaw, salt, expected] = encoded.split("$");
  if (scheme !== "pbkdf2" || digest !== "sha256" || !iterationsRaw || !salt || !expected) return false;
  const iterations = Number(iterationsRaw);
  if (!Number.isFinite(iterations) || iterations < 100000) return false;
  const actual = crypto.pbkdf2Sync(password, salt, iterations, 32, "sha256").toString("base64url");
  return crypto.timingSafeEqual(Buffer.from(actual), Buffer.from(expected));
}

export function createAuthToken(ctx: Omit<AuthContext, "exp" | "jti">) {
  const now = Math.floor(Date.now() / 1000);
  const payload: AuthContext = {
    ...ctx,
    exp: now + TOKEN_TTL_SECONDS,
    jti: crypto.randomUUID(),
  };
  const header = b64({ alg: "HS256", typ: "JWT" });
  const body = b64(payload);
  return {
    token: `${header}.${body}.${sign(`${header}.${body}`)}`,
    payload,
    expiresIn: TOKEN_TTL_SECONDS,
  };
}

export function verifyAuthToken(token: string): AuthContext {
  const [header, body, signature] = token.split(".");
  if (!header || !body || !signature) throw new Error("Invalid token");
  const expected = sign(`${header}.${body}`);
  if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) throw new Error("Invalid signature");
  const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8")) as AuthContext;
  if (!payload.exp || payload.exp < Math.floor(Date.now() / 1000)) throw new Error("Token expired");
  return payload;
}

export function bearerToken(request: FastifyRequest) {
  const auth = request.headers.authorization;
  if (!auth?.startsWith("Bearer ")) return "";
  return auth.slice("Bearer ".length).trim();
}

export function sessionHash(jti: string) {
  return crypto.createHash("sha256").update(jti).digest("hex");
}

export async function requireAuth(app: FastifyInstance, request: FastifyRequest, reply: FastifyReply) {
  const token = bearerToken(request);
  if (!token) return reply.code(401).send({ ok: false, error: "Missing authorization token" });

  let payload: AuthContext;
  try {
    payload = verifyAuthToken(token);
  } catch {
    return reply.code(401).send({ ok: false, error: "Invalid authorization token" });
  }

  const client = await app.pg.connect();
  try {
    await client.query("SELECT set_config('app.service_role', 'true', false)");
    const { rows } = await client.query(
      `SELECT id
       FROM user_sessions
       WHERE tenant_id = $1
         AND user_id = $2
         AND session_hash = $3
         AND revoked_at IS NULL
         AND expires_at > now()
       LIMIT 1`,
      [payload.tenantId, payload.userId, sessionHash(payload.jti)]
    );
    if (!rows[0]) return reply.code(401).send({ ok: false, error: "Session expired" });
  } finally {
    await client.query("SELECT set_config('app.service_role', '', false)").catch(() => {});
    client.release();
  }

  (request as any).auth = payload;
}

export function requirePermission(permission: string) {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const auth = (request as any).auth as AuthContext | undefined;
    if (!auth) return reply.code(401).send({ ok: false, error: "Unauthenticated" });
    if (auth.permissions.includes("*") || auth.permissions.includes(permission)) return;
    return reply.code(403).send({ ok: false, error: "Forbidden", permission });
  };
}
