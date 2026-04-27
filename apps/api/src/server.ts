import cors from "@fastify/cors";
import postgres from "@fastify/postgres";
import Fastify from "fastify";

const server = Fastify({ logger: true });
const port = Number(process.env.API_PORT ?? 8080);
const defaultTenantCode = process.env.TENANT_DEFAULT_CODE ?? "xqt";

await server.register(cors, { origin: true });
await server.register(postgres, {
  connectionString: process.env.DATABASE_URL ?? "postgres://xqt:xqt_dev_password@localhost:15432/xqt_saas"
});

server.get("/health", async () => ({ ok: true, service: "xqt-api" }));

async function resolveTenantId(tenantCode: string) {
  const { rows } = await server.pg.query<{ id: string }>("select id from tenants where code = $1", [tenantCode]);
  return rows[0]?.id;
}

async function withTenant<T>(tenantCode: string, fn: (client: any, tenantId: string) => Promise<T>) {
  const tenantId = await resolveTenantId(tenantCode);
  if (!tenantId) {
    const error = new Error(`Unknown tenant: ${tenantCode}`);
    (error as Error & { statusCode?: number }).statusCode = 404;
    throw error;
  }

  const client = await server.pg.connect();
  try {
    await client.query("begin");
    await client.query("select set_config('app.current_tenant_id', $1, true)", [tenantId]);
    const result = await fn(client, tenantId);
    await client.query("commit");
    return result;
  } catch (error) {
    await client.query("rollback");
    throw error;
  } finally {
    client.release();
  }
}

server.get("/api/finance/overview", async (request, reply) => {
  const tenantCode = (request.headers["x-tenant-code"] as string | undefined) ?? defaultTenantCode;
  const row = await withTenant(tenantCode, async (client, tenantId) => {
    const { rows } = await client.query(
      `
      select
        t.code as tenant_code,
        count(distinct s.id)::int as shipments,
        coalesce(sum(case when c.side = 'AR' then c.amount else 0 end), 0)::numeric(14,2) as receivable,
        coalesce(sum(case when c.side = 'AP' then c.amount else 0 end), 0)::numeric(14,2) as payable,
        coalesce(sum(case when c.side = 'AR' then c.amount else -c.amount end), 0)::numeric(14,2) as gross_profit
      from tenants t
      left join shipments s on s.tenant_id = t.id
      left join charges c on c.shipment_id = s.id
      where t.id = $1
      group by t.code
      `,
      [tenantId]
    );

    return rows[0];
  });

  return reply.send(
    row ?? { tenant_code: tenantCode, shipments: 0, receivable: 0, payable: 0, gross_profit: 0 }
  );
});

server.get("/api/finance/painpoint-map", async () => ({
  coverage: [
    { code: "A1-A12", module: "费率引擎", tables: ["charge_rules", "rule_versions", "rate_cards", "charges"] },
    { code: "B1-B7", module: "成本账单导入与对账", tables: ["carrier_bill_imports", "carrier_bill_lines", "reconciliation_results"] },
    { code: "四项补充-1/2", module: "派送比价与风险预警", tables: ["delivery_quote_options", "cartons", "charge_rules"] },
    { code: "四项补充-3", module: "保险 API", tables: ["insurance_policies", "insurance_events"] },
    { code: "四项补充-4", module: "前置规则化成本", tables: ["channel_cost_policies", "background_jobs", "reconciliation_results"] }
  ]
}));

server.listen({ port, host: "0.0.0.0" });
