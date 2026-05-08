import type { FastifyInstance } from "fastify";

type GeoPoint = {
  name: string;
  countryCode: string;
  lat: number;
  lng: number;
};

export async function unifiedFinanceRoutes(app: FastifyInstance) {
  const defaultTenantCode = process.env.TENANT_DEFAULT_CODE ?? "xqt";
  const shenzhenPoint: GeoPoint = { name: "深圳", countryCode: "CN", lat: 22.5431, lng: 114.0579 };
  const countryGeo: Record<string, GeoPoint> = {
    CN: { name: "中国", countryCode: "CN", lat: 35.8617, lng: 104.1954 },
    US: { name: "美国", countryCode: "US", lat: 39.8283, lng: -98.5795 },
    DE: { name: "德国", countryCode: "DE", lat: 51.1657, lng: 10.4515 },
    GB: { name: "英国", countryCode: "GB", lat: 55.3781, lng: -3.4360 },
    FR: { name: "法国", countryCode: "FR", lat: 46.2276, lng: 2.2137 },
    NL: { name: "荷兰", countryCode: "NL", lat: 52.1326, lng: 5.2913 },
    BE: { name: "比利时", countryCode: "BE", lat: 50.5039, lng: 4.4699 },
    IT: { name: "意大利", countryCode: "IT", lat: 41.8719, lng: 12.5674 },
    ES: { name: "西班牙", countryCode: "ES", lat: 40.4637, lng: -3.7492 },
    PL: { name: "波兰", countryCode: "PL", lat: 51.9194, lng: 19.1451 },
    CA: { name: "加拿大", countryCode: "CA", lat: 56.1304, lng: -106.3468 },
    MX: { name: "墨西哥", countryCode: "MX", lat: 23.6345, lng: -102.5528 },
    BR: { name: "巴西", countryCode: "BR", lat: -14.2350, lng: -51.9253 },
    AU: { name: "澳大利亚", countryCode: "AU", lat: -25.2744, lng: 133.7751 },
    JP: { name: "日本", countryCode: "JP", lat: 36.2048, lng: 138.2529 },
    KR: { name: "韩国", countryCode: "KR", lat: 35.9078, lng: 127.7669 },
    SG: { name: "新加坡", countryCode: "SG", lat: 1.3521, lng: 103.8198 },
    MY: { name: "马来西亚", countryCode: "MY", lat: 4.2105, lng: 101.9758 },
    TH: { name: "泰国", countryCode: "TH", lat: 15.8700, lng: 100.9925 },
    VN: { name: "越南", countryCode: "VN", lat: 14.0583, lng: 108.2772 },
    PH: { name: "菲律宾", countryCode: "PH", lat: 12.8797, lng: 121.7740 },
    ID: { name: "印度尼西亚", countryCode: "ID", lat: -0.7893, lng: 113.9213 },
    IN: { name: "印度", countryCode: "IN", lat: 20.5937, lng: 78.9629 },
    AE: { name: "阿联酋", countryCode: "AE", lat: 23.4241, lng: 53.8478 },
    SA: { name: "沙特", countryCode: "SA", lat: 23.8859, lng: 45.0792 },
    ZA: { name: "南非", countryCode: "ZA", lat: -30.5595, lng: 22.9375 },
  };
  const locationGeo: Record<string, GeoPoint> = {
    shenzhen: shenzhenPoint,
    "深圳": shenzhenPoint,
    hongkong: { name: "香港", countryCode: "CN", lat: 22.3193, lng: 114.1694 },
    "hong kong": { name: "香港", countryCode: "CN", lat: 22.3193, lng: 114.1694 },
    guangzhou: { name: "广州", countryCode: "CN", lat: 23.1291, lng: 113.2644 },
    shanghai: { name: "上海", countryCode: "CN", lat: 31.2304, lng: 121.4737 },
    losangeles: { name: "洛杉矶", countryCode: "US", lat: 34.0522, lng: -118.2437 },
    "los angeles": { name: "洛杉矶", countryCode: "US", lat: 34.0522, lng: -118.2437 },
    berlin: { name: "柏林", countryCode: "DE", lat: 52.5200, lng: 13.4050 },
  };

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

  function resolveCountryPoint(countryCode?: string | null): GeoPoint {
    const code = String(countryCode ?? "CN").trim().toUpperCase();
    return countryGeo[code] ?? { name: code || "未知目的地", countryCode: code || "UN", lat: 20, lng: 0 };
  }

  function resolveLocationPoint(location?: string | null, countryCode?: string | null): GeoPoint {
    const rawLocation = String(location ?? "").trim();
    const key = rawLocation.toLowerCase().replace(/\s+/g, " ");
    const compactKey = rawLocation.toLowerCase().replace(/\s+/g, "");
    return locationGeo[key] ?? locationGeo[compactKey] ?? {
      ...resolveCountryPoint(countryCode),
      name: rawLocation || resolveCountryPoint(countryCode).name,
    };
  }

  function trackingProgress(status?: string | null): number {
    const normalizedStatus = String(status ?? "").toUpperCase();
    const progressMap: Record<string, number> = {
      CREATED: 12,
      ORDERED: 16,
      DRAFT: 6,
      IN_WAREHOUSE: 25,
      MEASURED: 34,
      BOOKED: 44,
      IN_TRANSIT: 62,
      OUT_FOR_DELIVERY: 86,
      DELIVERED: 100,
      CLOSED: 100,
      EXCEPTION: 52,
      RETURNED: 70,
      VOID: 0,
    };
    return progressMap[normalizedStatus] ?? 40;
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

      const { rows: trackingSummaryRows } = await client.query(
        `WITH latest_events AS (
            SELECT DISTINCT ON (te.shipment_id)
              te.shipment_id,
              te.normalized_status,
              te.event_time
            FROM tracking_events te
            WHERE te.tenant_id = $1
            ORDER BY te.shipment_id, te.event_time DESC
          )
          SELECT
            COUNT(*) FILTER (
              WHERE s.status NOT IN ('DELIVERED', 'CLOSED')
            )::int AS active_shipments,
            COUNT(*) FILTER (
              WHERE s.status = 'EXCEPTION' OR le.normalized_status = 'EXCEPTION'
            )::int AS exception_count,
            COUNT(*) FILTER (
              WHERE s.delivered_at::date = CURRENT_DATE
                OR (le.normalized_status = 'DELIVERED' AND le.event_time::date = CURRENT_DATE)
            )::int AS delivered_today,
            COUNT(*) FILTER (
              WHERE EXISTS (
                SELECT 1
                FROM cartons c
                WHERE c.tenant_id = s.tenant_id
                  AND c.shipment_id = s.id
                  AND NULLIF(c.tracking_no, '') IS NOT NULL
              )
            )::int AS tracked_shipments,
            COUNT(DISTINCT s.destination_country)::int AS destination_countries
          FROM shipments s
          LEFT JOIN latest_events le ON le.shipment_id = s.id
          WHERE s.tenant_id = $1`,
        [tenantId]
      );

      const { rows: trackingRows } = await client.query(
        `SELECT
            s.id,
            s.shipment_no,
            s.status::text AS shipment_status,
            s.service_mode,
            s.customer_direction,
            s.destination_country,
            s.destination_postal_code,
            s.destination_warehouse_code,
            s.departed_at,
            s.delivered_at,
            ch.name AS channel_name,
            carrier.name AS carrier_name,
            COALESCE(latest.tracking_no, carton.tracking_no, '') AS tracking_no,
            latest.event_time,
            latest.raw_status,
            latest.normalized_status::text AS normalized_status,
            latest.location,
            latest.source::text AS source
          FROM shipments s
          LEFT JOIN channels ch ON ch.tenant_id = s.tenant_id AND ch.id = s.channel_id
          LEFT JOIN LATERAL (
            SELECT
              te.carrier_id,
              te.tracking_no,
              te.event_time,
              te.raw_status,
              te.normalized_status,
              te.location,
              te.source
            FROM tracking_events te
            WHERE te.tenant_id = s.tenant_id
              AND te.shipment_id = s.id
            ORDER BY te.event_time DESC
            LIMIT 1
          ) latest ON true
          LEFT JOIN carriers carrier ON carrier.tenant_id = s.tenant_id AND carrier.id = latest.carrier_id
          LEFT JOIN LATERAL (
            SELECT c.tracking_no
            FROM cartons c
            WHERE c.tenant_id = s.tenant_id
              AND c.shipment_id = s.id
              AND NULLIF(c.tracking_no, '') IS NOT NULL
            ORDER BY c.carton_no DESC
            LIMIT 1
          ) carton ON true
          WHERE s.tenant_id = $1
          ORDER BY COALESCE(latest.event_time, s.departed_at, s.ordered_at, s.created_at) DESC
          LIMIT 12`,
        [tenantId]
      );

      return {
        total: totalRows[0] ?? { shipments: 0, orders: 0, receivable: 0, payable: 0 },
        flows: flowRows,
        invoices: invoiceRows[0] ?? { invoice_count: 0, paid_amount: 0, unpaid_amount: 0 },
        trackingSummary: trackingSummaryRows[0] ?? {
          active_shipments: 0,
          exception_count: 0,
          delivered_today: 0,
          tracked_shipments: 0,
          destination_countries: 0,
        },
        trackingRows,
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
    const trackingRows = dashboardData?.trackingRows ?? [];
    const trackingRoutes = trackingRows.map((row: any) => {
      const status = row.normalized_status ?? row.shipment_status;
      const destination = resolveCountryPoint(row.destination_country);
      const current = resolveLocationPoint(row.location, row.destination_country);
      return {
        id: row.id,
        shipmentNo: row.shipment_no,
        trackingNo: row.tracking_no,
        serviceMode: row.service_mode,
        customerDirection: row.customer_direction,
        carrierName: row.carrier_name ?? row.channel_name ?? "未绑定承运商",
        channelName: row.channel_name,
        status,
        shipmentStatus: row.shipment_status,
        rawStatus: row.raw_status ?? row.shipment_status,
        latestLocation: row.location ?? current.name,
        latestEventTime: row.event_time,
        destinationCountry: row.destination_country,
        destinationPostalCode: row.destination_postal_code,
        progress: trackingProgress(status),
        origin: shenzhenPoint,
        current,
        destination,
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
      tracking: {
        summary: {
          activeShipments: Number(dashboardData?.trackingSummary.active_shipments ?? 0),
          exceptionCount: Number(dashboardData?.trackingSummary.exception_count ?? 0),
          deliveredToday: Number(dashboardData?.trackingSummary.delivered_today ?? 0),
          trackedShipments: Number(dashboardData?.trackingSummary.tracked_shipments ?? 0),
          destinationCountries: Number(dashboardData?.trackingSummary.destination_countries ?? 0),
        },
        routes: trackingRoutes,
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
