export interface XqtShipment {
  id: string;
  shipmentNumber: string;
  customer: string;
  service: string;
  country: string;
  parcelCount: number;
  actualWeight: number;
  chargeableWeight: number;
  sellCharge: number;
  costCharge: number;
  sellerCost: number;
  sellerCommission: number;
  profit: number;
  servicer: string;
  seller: string;
  pickingTime: number;
  status: string;
}

export interface XqtInvoice {
  number: string;
  customer: string;
  organizationId: string;
  currency: string;
  chargeAmount: number;
  paidCharge: number;
  remainingCharge: number;
  seller: string;
  servicer: string;
  finance: string;
  invoiceDate: number;
  dueDate: number;
  status: string;
}

export interface XqtFinanceSummary {
  invoiceCount: number;
  totalReceivable: number;
  totalPaid: number;
  totalUnpaid: number;
  shipmentCount: number;
}

interface XqtPagination {
  page: number;
  pageSize: number;
  total: number;
}

export class XqtAdapter {
  private baseUrl: string;
  private cookies: string = "";
  private username: string;
  private password: string;

  constructor(opts: { baseUrl: string; username: string; password: string }) {
    this.baseUrl = opts.baseUrl.replace(/\/$/, "");
    this.username = opts.username;
    this.password = opts.password;
  }

  get available(): boolean {
    return !!this.baseUrl && !!this.username;
  }

  async login(): Promise<{ ok: boolean; error?: string }> {
    try {
      const res = await fetch(`${this.baseUrl}/rest/tms/aos/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: this.username, password: this.password }),
        redirect: "manual",
      });
      const setCookie = res.headers.get("set-cookie");
      if (setCookie) {
        this.cookies = setCookie;
      }
      return { ok: res.ok || !!setCookie };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  }

  async testConnection(): Promise<{ ok: boolean; error?: string }> {
    try {
      const res = await fetch(`${this.baseUrl}/rest/tms/aos/home/lists`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(this.cookies ? { Cookie: this.cookies } : {}),
        },
        body: JSON.stringify({}),
      });
      const json = await res.json() as any;
      return { ok: json.success === 1 };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  }

  private async fetchModule(
    subsys: "aos" | "csos",
    module: string,
    params: Record<string, any> = {}
  ): Promise<{ rows: any[]; pagination: XqtPagination | null }> {
    try {
      const res = await fetch(`${this.baseUrl}/rest/tms/${subsys}/${module}/lists`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(this.cookies ? { Cookie: this.cookies } : {}),
        },
        body: JSON.stringify({ timeLimit: 0, scenes: 1, ...params }),
      });
      const json = await res.json() as any;
      const gridView = json.data?.components?.gridView;
      if (!gridView) return { rows: [], pagination: null };

      const rows = gridView.table?.dataSource ?? [];
      const pag = gridView.table?.pagination ?? null;
      return { rows, pagination: pag };
    } catch {
      return { rows: [], pagination: null };
    }
  }

  private stripHtml(s: any): string {
    if (typeof s !== "string") return String(s ?? "");
    return s.replace(/<[^>]*>/g, "").replace(/<br\s*\/?>/gi, " ").trim();
  }

  private parseNumber(s: any): number {
    if (typeof s === "number") return s;
    if (!s) return 0;
    const cleaned = String(s).replace(/<[^>]*>/g, "").replace(/[^0-9.\-]/g, "");
    return parseFloat(cleaned) || 0;
  }

  async listShipments(params?: {
    page?: number;
    dateFrom?: string;
    dateTo?: string;
  }): Promise<{ data: XqtShipment[]; total: number }> {
    const reqParams: Record<string, any> = {};
    if (params?.page) reqParams.page = params.page;
    if (params?.dateFrom && params?.dateTo) {
      reqParams.created_daterange = [params.dateFrom, params.dateTo];
    }

    const { rows, pagination } = await this.fetchModule("aos", "shipment", reqParams);

    const data: XqtShipment[] = rows.map((r: any) => ({
      id: r.id,
      shipmentNumber: r.shipment_number ?? this.stripHtml(r.shipment_id).split(/\s/)[0],
      customer: this.stripHtml(r.uid),
      service: this.stripHtml(r.service),
      country: this.stripHtml(r.to_country),
      parcelCount: Number(r.parcel_count ?? 0),
      actualWeight: this.parseNumber(r.actual_weight),
      chargeableWeight: this.parseNumber(r.chargeable_weight),
      sellCharge: this.parseNumber(r.sell_charge_amount),
      costCharge: this.parseNumber(r.cost_charge_amount),
      sellerCost: this.parseNumber(r.seller_charge_amount),
      sellerCommission: this.parseNumber(r.seller_commission),
      profit: this.parseNumber(r.sell_profit),
      servicer: this.stripHtml(r.servicer_id),
      seller: this.stripHtml(r.seller_id),
      pickingTime: Number(r.picking_time ?? 0),
      status: r.status ?? "unknown",
    }));

    return { data, total: pagination?.total ?? data.length };
  }

  async listInvoices(params?: {
    page?: number;
    status?: string;
  }): Promise<{ data: XqtInvoice[]; total: number }> {
    const reqParams: Record<string, any> = {};
    if (params?.page) reqParams.page = params.page;

    const { rows, pagination } = await this.fetchModule("aos", "invoice", reqParams);

    const data: XqtInvoice[] = rows.map((r: any) => ({
      number: r.number,
      customer: this.stripHtml(r.user_id),
      organizationId: r.organization_id ?? "",
      currency: this.stripHtml(r.currency),
      chargeAmount: this.parseNumber(r.charge_amount),
      paidCharge: this.parseNumber(r.paid_charge),
      remainingCharge: this.parseNumber(r.remaining_charge),
      seller: this.stripHtml(r.seller_id),
      servicer: this.stripHtml(r.servicer_id),
      finance: this.stripHtml(r.finance_id),
      invoiceDate: Number(r.invoice_date ?? 0),
      dueDate: Number(r.due_date ?? 0),
      status: r.status ?? "unknown",
    }));

    return { data, total: pagination?.total ?? data.length };
  }

  async getFinanceSummary(): Promise<XqtFinanceSummary> {
    const [invoiceResult, shipmentResult] = await Promise.all([
      this.fetchModule("aos", "user_report"),
      this.fetchModule("aos", "shipment"),
    ]);

    let totalReceivable = 0, totalPaid = 0, totalUnpaid = 0;
    for (const r of invoiceResult.rows) {
      totalReceivable += this.parseNumber(r.total);
      totalPaid += this.parseNumber(r.paid);
      totalUnpaid += this.parseNumber(r.unpaid);
    }

    return {
      invoiceCount: invoiceResult.pagination?.total ?? invoiceResult.rows.length,
      totalReceivable,
      totalPaid,
      totalUnpaid,
      shipmentCount: shipmentResult.pagination?.total ?? 0,
    };
  }

  async getRates(): Promise<any[]> {
    const { rows } = await this.fetchModule("aos", "rates");
    return rows.map((r: any) => ({
      id: r.id,
      name: r.name,
      service: this.stripHtml(r.service_code),
      zones: r.zone_id,
      userGrades: r.user_grade_id,
      status: r.status,
      type: r.type,
      priority: r.priority,
    }));
  }

  async close() {}
}

export function createXqtAdapter(): XqtAdapter {
  return new XqtAdapter({
    baseUrl: process.env.XQT_BASE_URL ?? "https://xqtgyl.nextsls.com",
    username: process.env.XQT_USERNAME ?? "",
    password: process.env.XQT_PASSWORD ?? "",
  });
}
