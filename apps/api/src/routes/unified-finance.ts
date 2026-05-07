import type { FastifyInstance } from "fastify";

export async function unifiedFinanceRoutes(app: FastifyInstance) {
  const defaultTenantCode = process.env.TENANT_DEFAULT_CODE ?? "xqt";

  async function withTenant<T>(
    app: FastifyInstance,
    tenantCode: string,
    fn: (client: any, tenantId: string) => Promise<T>
  ): Promise<T | null> {
    const { rows } = await app.pg.query<{ id: string }>(
      "SELECT id FROM tenants WHERE code = $1",
      [tenantCode]
    );
    const tenantId = rows[0]?.id;
    if (!tenantId) return null;

    const client = await app.pg.connect();
    try {
      await client.query("BEGIN");
      await client.query("SELECT set_config('app.current_tenant_id', $1, true)", [tenantId]);
      const result = await fn(client, tenantId);
      await client.query("COMMIT");
      return result;
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    } finally {
      client.release();
    }
  }

  // 统一财务驾驶舱
  app.get<{
    Querystring: { dateFrom?: string; dateTo?: string; branch?: string };
  }>("/api/finance/dashboard", async (request) => {
    const { dateFrom, dateTo } = request.query;
    const from = dateFrom ?? new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
    const to = dateTo ?? new Date().toISOString().slice(0, 10);

    const localData = await withTenant(app, defaultTenantCode, async (client, tenantId) => {
      const { rows } = await client.query(
        `SELECT
            COUNT(DISTINCT s.id)::int AS shipments,
            COALESCE(SUM(CASE WHEN c.side='AR' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS receivable,
            COALESCE(SUM(CASE WHEN c.side='AP' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS payable
          FROM shipments s
          LEFT JOIN charges c ON c.shipment_id = s.id
          WHERE s.tenant_id = $1`,
        [tenantId]
      );
      return rows[0] ?? { shipments: 0, receivable: 0, payable: 0 };
    });

    const localRevenue = Number(localData?.receivable ?? 0);
    const localCost = Number(localData?.payable ?? 0);

    return {
      acc: {
        label: "制单客户对照 (ACC)",
        referenceOnly: true,
        revenue: 0,
        cost: 0,
        profit: 0,
        orderCount: 0,
        byBranch: [],
      },
      xqt: {
        label: "卖货客户对照 (XQT)",
        referenceOnly: true,
        revenue: 0,
        paid: 0,
        unpaid: 0,
        shipmentCount: 0,
        invoiceCount: 0,
      },
      local: {
        label: "本地数据 (新系统)",
        shipments: localData?.shipments ?? 0,
        receivable: localRevenue,
        payable: localCost,
        profit: localRevenue - localCost,
      },
      combined: {
        totalRevenue: localRevenue,
        totalCost: localCost,
        totalProfit: localRevenue - localCost,
      },
      period: { from, to },
    };
  });

  // 分公司报表
  app.get("/api/finance/branches", async () => {
    const result = await withTenant(app, defaultTenantCode, async (client, tenantId) => {
      const { rows } = await client.query(
        `SELECT o.id, o.code, o.name, o.org_type, o.parent_id, o.is_active
         FROM organizations o
         WHERE o.tenant_id = $1
         ORDER BY o.org_type DESC, o.code`,
        [tenantId]
      );
      return rows;
    });
    return { data: result ?? [] };
  });

  // 原有的 overview 和 painpoint-map 保留
  app.get("/api/finance/overview", async (request: any) => {
    const tenantCode = (request.headers["x-tenant-code"] as string) ?? defaultTenantCode;
    const row = await withTenant(app, tenantCode, async (client, tenantId) => {
      const { rows } = await client.query(
        `SELECT
          t.code AS tenant_code,
          COUNT(DISTINCT s.id)::int AS shipments,
          COALESCE(SUM(CASE WHEN c.side='AR' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS receivable,
          COALESCE(SUM(CASE WHEN c.side='AP' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS payable,
          COALESCE(SUM(CASE WHEN c.side='AR' THEN c.amount ELSE -c.amount END), 0)::numeric(14,2) AS gross_profit
        FROM tenants t
        LEFT JOIN shipments s ON s.tenant_id = t.id
        LEFT JOIN charges c ON c.shipment_id = s.id
        WHERE t.id = $1
        GROUP BY t.code`,
        [tenantId]
      );
      return rows[0];
    });
    return row ?? { tenant_code: tenantCode, shipments: 0, receivable: 0, payable: 0, gross_profit: 0 };
  });

  app.get("/api/finance/painpoint-map", async () => ({
    coverage: [
      { code: "A1-A12", module: "费率引擎", tables: ["charge_rules", "rule_versions", "rate_cards", "charges"] },
      { code: "B1-B7", module: "成本账单导入与对账", tables: ["carrier_bill_imports", "carrier_bill_lines", "reconciliation_results"] },
      { code: "C1-C6", module: "时效/轨迹/退件", tables: ["sla_rules", "tracking_events", "return_orders"] },
      { code: "D1-D2", module: "退件与二次制单", tables: ["return_orders", "shipment_relations", "relabel_tasks"] },
      { code: "E1-E2", module: "派送比价与风险预警", tables: ["delivery_quote_options", "channel_cost_policies"] },
      { code: "G1-G2", module: "多币种汇率", tables: ["exchange_rates", "charges", "payments"] },
      { code: "H1", module: "销售提成", tables: ["commission_plans", "commission_runs", "commission_lines"] },
      { code: "I1-I3", module: "平台能力", tables: ["organizations", "approval_requests", "background_jobs"] },
    ],
  }));
}
