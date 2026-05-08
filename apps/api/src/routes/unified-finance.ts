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

  function toMoney(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
  }

  // 统一财务驾驶舱
  app.get<{
    Querystring: { dateFrom?: string; dateTo?: string; branch?: string };
  }>("/api/finance/dashboard", async (request) => {
    const { dateFrom, dateTo } = request.query;
    const from = dateFrom ?? new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
    const to = dateTo ?? new Date().toISOString().slice(0, 10);

    const dashboardData = await withTenant(app, defaultTenantCode, async (client, tenantId) => {
      const { rows: totalRows } = await client.query(
        `SELECT
            COUNT(DISTINCT s.id)::int AS shipments,
            COUNT(DISTINCT o.id)::int AS orders,
            COALESCE(SUM(CASE WHEN c.side='AR' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS receivable,
            COALESCE(SUM(CASE WHEN c.side='AP' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS payable
          FROM shipments s
          LEFT JOIN shipment_order_links sol ON sol.tenant_id = s.tenant_id AND sol.shipment_id = s.id
          LEFT JOIN orders o ON o.tenant_id = s.tenant_id AND o.id = sol.order_id
          LEFT JOIN charges c ON c.shipment_id = s.id
          WHERE s.tenant_id = $1`,
        [tenantId]
      );

      const { rows: flowRows } = await client.query(
        `WITH flow_orders AS (
            SELECT
              o.customer_direction,
              o.service_mode,
              COUNT(DISTINCT o.id)::int AS orders
            FROM orders o
            WHERE o.tenant_id = $1
              AND o.deleted_at IS NULL
            GROUP BY o.customer_direction, o.service_mode
          ),
          flow_shipments AS (
            SELECT
              s.customer_direction,
              s.service_mode,
              COUNT(DISTINCT s.id)::int AS shipments,
              COALESCE(SUM(CASE WHEN c.side='AR' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS receivable,
              COALESCE(SUM(CASE WHEN c.side='AP' THEN c.amount ELSE 0 END), 0)::numeric(14,2) AS payable
            FROM shipments s
            LEFT JOIN charges c ON c.tenant_id = s.tenant_id AND c.shipment_id = s.id
            WHERE s.tenant_id = $1
            GROUP BY s.customer_direction, s.service_mode
          )
          SELECT
            COALESCE(fo.customer_direction, fs.customer_direction) AS customer_direction,
            COALESCE(fo.service_mode, fs.service_mode) AS service_mode,
            COALESCE(fo.orders, 0)::int AS orders,
            COALESCE(fs.shipments, 0)::int AS shipments,
            COALESCE(fs.receivable, 0)::numeric(14,2) AS receivable,
            COALESCE(fs.payable, 0)::numeric(14,2) AS payable
          FROM flow_orders fo
          FULL JOIN flow_shipments fs
            ON fs.customer_direction = fo.customer_direction
           AND fs.service_mode = fo.service_mode
          ORDER BY COALESCE(fo.service_mode, fs.service_mode)`,
        [tenantId]
      );

      const { rows: invoiceRows } = await client.query(
        `SELECT
            COUNT(*)::int AS invoice_count,
            COALESCE(SUM(paid_amount), 0)::numeric(14,2) AS paid_amount,
            COALESCE(SUM(unpaid_amount), 0)::numeric(14,2) AS unpaid_amount
          FROM customer_invoices
          WHERE tenant_id = $1`,
        [tenantId]
      );

      return {
        total: totalRows[0] ?? { shipments: 0, orders: 0, receivable: 0, payable: 0 },
        flows: flowRows,
        invoices: invoiceRows[0] ?? { invoice_count: 0, paid_amount: 0, unpaid_amount: 0 },
      };
    });

    const localRevenue = toMoney(Number(dashboardData?.total.receivable ?? 0));
    const localCost = toMoney(Number(dashboardData?.total.payable ?? 0));
    const localProfit = toMoney(localRevenue - localCost);
    const invoiceCount = Number(dashboardData?.invoices.invoice_count ?? 0);
    const paidAmount = toMoney(Number(dashboardData?.invoices.paid_amount ?? 0));
    const unpaidAmount = toMoney(Number(dashboardData?.invoices.unpaid_amount ?? 0));
    const flowLabels: Record<string, string> = {
      SELLER_FULFILLMENT: "卖货客户履约",
      DOCUMENT_SHIPPING: "制单客户发货",
      WAREHOUSE_ONLY: "仓储服务",
      VALUE_ADDED: "增值服务",
    };
    const flows = (dashboardData?.flows ?? []).map((row: any) => {
      const receivable = toMoney(Number(row.receivable ?? 0));
      const payable = toMoney(Number(row.payable ?? 0));
      return {
        code: row.service_mode,
        customerDirection: row.customer_direction,
        label: flowLabels[row.service_mode] ?? row.service_mode,
        orders: Number(row.orders ?? 0),
        shipments: Number(row.shipments ?? 0),
        receivable,
        payable,
        profit: toMoney(receivable - payable),
      };
    });

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
        orders: dashboardData?.total.orders ?? 0,
        shipments: dashboardData?.total.shipments ?? 0,
        receivable: localRevenue,
        payable: localCost,
        profit: localProfit,
        invoiceCount,
        paid: paidAmount,
        unpaid: unpaidAmount,
      },
      combined: {
        totalRevenue: localRevenue,
        totalCost: localCost,
        totalProfit: localProfit,
      },
      flows,
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
