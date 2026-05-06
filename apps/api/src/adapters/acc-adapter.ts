import mysql, { type Pool, type RowDataPacket } from "mysql2/promise";

const TABLE_PREFIX = "358_";

function t(name: string) {
  return `${TABLE_PREFIX}${name}`;
}

export interface AccOrder {
  id: number;
  orderNo: string;
  trackNo: string;
  customerName: string;
  customerCode: string;
  product: string;
  channel: string;
  country: string;
  weight: number;
  chargeWeight: number;
  volume: number;
  piece: number;
  status: string;
  delivery: string;
  declaredValue: number;
  addTime: string;
  sellCharge: number;
  costCharge: number;
  branch: string;
  sellerName: string;
  remark: string;
}

export interface AccCustomer {
  id: number;
  code: string;
  name: string;
  contact: string;
  mobile: string;
  email: string;
  credits: number;
  balance: number;
  settlement: string;
  branch: string;
  group: string;
  salesman: string;
  grade: string;
  isActive: boolean;
}

export interface AccSupplier {
  id: number;
  name: string;
  contact: string;
  mobile: string;
  phone: string;
  email: string;
  address: string;
  product: string;
  balance: number;
  settlement: string;
  remark: string;
}

export interface AccChannel {
  id: number;
  name: string;
  code: string;
  isOpen: boolean;
  isDebug: boolean;
  remark: string;
}

export interface AccChannelAccount {
  id: number;
  channelName: string;
  name: string;
  code: string;
  supplierName: string;
  isOpen: boolean;
}

export interface AccProduct {
  id: number;
  name: string;
  code: string;
  channelName: string;
  supplierName: string;
  isOpen: boolean;
  remark: string;
}

export interface AccShipment {
  id: number;
  no: string;
  channelName: string;
  supplierName: string;
  country: string;
  totalPiece: number;
  totalWeight: number;
  totalCharge: number;
  totalCost: number;
  status: number;
  auditName: string;
  addTime: string;
}

export interface AccStowage {
  id: number;
  no: string;
  flight: string;
  departurePort: string;
  arrivalPort: string;
  status: number;
  statusText: string;
  totalPiece: number;
  totalWeight: number;
  totalVolume: number;
  etd: string;
  eta: string;
  addTime: string;
}

export interface AccCharge {
  id: number;
  expressNo: string;
  customerName: string;
  productName: string;
  country: string;
  chargeWeight: number;
  type: string;
  amount: number;
  paid: number;
  theDate: string;
  auditName: string;
  remark: string;
}

export interface AccCost {
  id: number;
  expressNo: string;
  supplierName: string;
  channelName: string;
  country: string;
  channelWeight: number;
  type: string;
  amount: number;
  paid: number;
  theDate: string;
  auditName: string;
  remark: string;
}

export interface AccBill {
  id: number;
  no: string;
  customerName: string;
  settlement: string;
  theDate: string;
  endDate: string;
  amount: number;
  paid: number;
  unpay: number;
  quantity: number;
  status: string;
  salesman: string;
  auditName: string;
}

export interface AccPayment {
  id: number;
  no: string;
  supplierName: string;
  bankName: string;
  amount: number;
  theDate: string;
  auditName: string;
  remark: string;
}

export interface AccReceived {
  id: number;
  no: string;
  customerName: string;
  bankName: string;
  amount: number;
  theDate: string;
  auditName: string;
  remark: string;
}

export interface AccEmployee {
  id: number;
  name: string;
  gender: string;
  mobile: string;
  email: string;
  department: string;
  branch: string;
  position: string;
  status: string;
  entryDate: string;
}

export interface AccBranch {
  id: number;
  name: string;
  code: string;
  contact: string;
  phone: string;
  address: string;
  remark: string;
}

export interface AccDepartment {
  id: number;
  name: string;
  branchName: string;
  remark: string;
}

export interface AccCountry {
  id: number;
  name: string;
  cn: string;
  code: string;
  isOpen: boolean;
}

export interface AccRemote {
  id: number;
  postcode: string;
  country: string;
  supplierName: string;
  type: string;
}

export interface AccFuel {
  id: number;
  name: string;
  rate: number;
  startDate: string;
  endDate: string;
}

export interface AccHSCode {
  id: number;
  code: string;
  nameEN: string;
  nameCN: string;
}

export interface AccBack {
  id: number;
  expressNo: string;
  customerName: string;
  reason: string;
  amount: number;
  status: string;
  addTime: string;
}

export interface AccCommission {
  id: number;
  name: string;
  type: string;
  percent: number;
  month: string;
  amount: number;
  quantity: number;
  commission: number;
}

export interface AccTransfer {
  id: number;
  fromBank: string;
  toBank: string;
  amount: number;
  theDate: string;
  remark: string;
  addName: string;
}

export interface AccCurrency {
  id: number;
  name: string;
  code: string;
  symbol: string;
  rate: number;
  decimal: number;
}

export interface AccTrack {
  id: number;
  expressNo: string;
  trackNo: string;
  status: string;
  detail: string;
  updateTime: string;
}

export interface AccProfitSummary {
  totalRevenue: number;
  totalCost: number;
  totalProfit: number;
  orderCount: number;
  byBranch: Array<{
    branch: string;
    revenue: number;
    cost: number;
    profit: number;
    count: number;
  }>;
}

export interface AccProfitItem {
  id: number;
  no: string;
  customerName: string;
  productName: string;
  country: string;
  chargeWeight: number;
  channelWeight: number;
  revenue: number;
  cost: number;
  profit: number;
  theDate: string;
  received: boolean;
}

interface ListResult<T> {
  data: T[];
  total: number;
}

interface ListParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
}

const SETTLEMENT_MAP = ["不限", "货到付款", "日结", "周结", "半月结", "月结", "自定义"];
const BILL_STATUS_MAP = ["待结款", "已结清", "已过结", "已逾期"];
const EMPLOYEE_STATUS_MAP = ["未入职", "试用期", "正式员工", "长期休假", "离职"];
const COMMISSION_TYPE_MAP = ["按销售额", "按利润额", "按销售数"];

export class AccAdapter {
  private pool: Pool | null = null;
  private apiUrl: string;

  constructor(opts: {
    host: string;
    port?: number;
    database: string;
    user: string;
    password: string;
    apiUrl: string;
  }) {
    this.apiUrl = opts.apiUrl;
    try {
      this.pool = mysql.createPool({
        host: opts.host,
        port: opts.port ?? 3306,
        database: opts.database,
        user: opts.user,
        password: opts.password,
        waitForConnections: true,
        connectionLimit: 5,
        charset: "utf8mb4",
      });
    } catch {
      this.pool = null;
    }
  }

  get available(): boolean {
    return this.pool !== null;
  }

  async testConnection(): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: "Pool not initialized" };
    try {
      await this.pool.query("SELECT 1");
      return { ok: true };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  }

  // ═══════════════════════════════════════════
  //  订单管理 - Order Management
  // ═══════════════════════════════════════════

  async listOrders(params: ListParams & {
    customerId?: number;
    branch?: string;
    status?: string;
    country?: string;
    product?: string;
  }): Promise<ListResult<AccOrder>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("o.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("o.AddTime <= ?"); values.push(params.dateTo); }
      if (params.customerId) { conditions.push("o.Customer = ?"); values.push(params.customerId); }
      if (params.branch) { conditions.push("br.Name = ?"); values.push(params.branch); }
      if (params.status) { conditions.push("o.Status = ?"); values.push(params.status); }
      if (params.country) { conditions.push("co.CN LIKE ?"); values.push(`%${params.country}%`); }
      if (params.product) { conditions.push("pr.Name LIKE ?"); values.push(`%${params.product}%`); }
      if (params.keyword) {
        conditions.push("(o.No LIKE ? OR o.TrackNo LIKE ? OR cust.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt
         FROM ${t("Express")} o
         LEFT JOIN ${t("Customer")} cust ON cust.Id = o.Customer
         LEFT JOIN ${t("Branch")} br ON br.Id = o.Branch
         LEFT JOIN ${t("Country")} co ON co.Id = o.Country
         LEFT JOIN ${t("Product")} pr ON pr.Id = o.Product
         ${where}`, values
      );

      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT o.Id as id, o.No as orderNo, o.TrackNo as trackNo,
                COALESCE(cust.Name, '') as customerName, COALESCE(cust.Code, '') as customerCode,
                COALESCE(pr.Name, '') as product,
                COALESCE(ch.Name, '') as channel, COALESCE(co.CN, '') as country,
                o.Weight as weight, o.ChargeWeight as chargeWeight, o.Volume as volume,
                o.Piece as piece, o.Status as status, o.Delivery as delivery,
                COALESCE(o.DeclaredValue, 0) as declaredValue,
                o.AddTime as addTime,
                COALESCE(chg.TotalCharge, 0) as sellCharge,
                COALESCE(cost.TotalCost, 0) as costCharge,
                COALESCE(br.Name, '') as branch,
                COALESCE(emp.Name, '') as sellerName,
                COALESCE(o.Remark, '') as remark
         FROM ${t("Express")} o
         LEFT JOIN ${t("Customer")} cust ON cust.Id = o.Customer
         LEFT JOIN ${t("Product")} pr ON pr.Id = o.Product
         LEFT JOIN ${t("Channel")} ch ON ch.Id = o.Channel
         LEFT JOIN ${t("Country")} co ON co.Id = o.Country
         LEFT JOIN ${t("Branch")} br ON br.Id = o.Branch
         LEFT JOIN ${t("Employee")} emp ON emp.Id = o.Employee
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCharge FROM ${t("Express_Charge")} GROUP BY OrderId) chg ON chg.OrderId = o.Id
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCost FROM ${t("Express_Cost")} GROUP BY OrderId) cost ON cost.OrderId = o.Id
         ${where}
         ORDER BY o.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );

      return { data: rows as unknown as AccOrder[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async getOrderDetail(id: number): Promise<AccOrder | null> {
    if (!this.pool) return null;
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT o.Id as id, o.No as orderNo, o.TrackNo as trackNo,
                COALESCE(cust.Name, '') as customerName, COALESCE(cust.Code, '') as customerCode,
                COALESCE(pr.Name, '') as product,
                COALESCE(ch.Name, '') as channel, COALESCE(co.CN, '') as country,
                o.Weight as weight, o.ChargeWeight as chargeWeight, o.Volume as volume,
                o.Piece as piece, o.Status as status, o.Delivery as delivery,
                COALESCE(o.DeclaredValue, 0) as declaredValue, o.AddTime as addTime,
                COALESCE(chg.TotalCharge, 0) as sellCharge,
                COALESCE(cost.TotalCost, 0) as costCharge,
                COALESCE(br.Name, '') as branch,
                COALESCE(emp.Name, '') as sellerName,
                COALESCE(o.Remark, '') as remark
         FROM ${t("Express")} o
         LEFT JOIN ${t("Customer")} cust ON cust.Id = o.Customer
         LEFT JOIN ${t("Product")} pr ON pr.Id = o.Product
         LEFT JOIN ${t("Channel")} ch ON ch.Id = o.Channel
         LEFT JOIN ${t("Country")} co ON co.Id = o.Country
         LEFT JOIN ${t("Branch")} br ON br.Id = o.Branch
         LEFT JOIN ${t("Employee")} emp ON emp.Id = o.Employee
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCharge FROM ${t("Express_Charge")} GROUP BY OrderId) chg ON chg.OrderId = o.Id
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCost FROM ${t("Express_Cost")} GROUP BY OrderId) cost ON cost.OrderId = o.Id
         WHERE o.Id = ?`, [id]
      );
      return rows.length ? (rows[0] as unknown as AccOrder) : null;
    } catch { return null; }
  }

  async getOrderTrackNos(orderId: number): Promise<Array<{ trackNo: string; channel: string }>> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT et.TrackNo as trackNo, COALESCE(ch.Name, '') as channel
         FROM ${t("Express_TrackNo")} et
         LEFT JOIN ${t("Channel")} ch ON ch.Id = et.Channel
         WHERE et.Express = ?`, [orderId]
      );
      return rows as any[];
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  退件管理 - Returns
  // ═══════════════════════════════════════════

  async listReturns(params: ListParams): Promise<ListResult<AccBack>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      if (params.keyword) {
        conditions.push("(b.No LIKE ? OR d.Name LIKE ? OR a.Reason LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Back")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express
         LEFT JOIN ${t("Customer")} d ON d.Id = b.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, b.No as expressNo, COALESCE(d.Name, '') as customerName,
                a.Reason as reason, a.Amount as amount, a.Status as status, a.AddTime as addTime
         FROM ${t("Back")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express
         LEFT JOIN ${t("Customer")} d ON d.Id = b.Customer
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccBack[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  物流出货 - Logistics & Shipping
  // ═══════════════════════════════════════════

  async listShipments(params: ListParams & { status?: number }): Promise<ListResult<AccShipment>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("s.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("s.AddTime <= ?"); values.push(params.dateTo); }
      if (params.status !== undefined) { conditions.push("s.Audit = ?"); values.push(params.status); }
      if (params.keyword) {
        conditions.push("(s.No LIKE ? OR ch.Name LIKE ? OR sup.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Shipment")} s
         LEFT JOIN ${t("Channel")} ch ON ch.Id = s.Channel
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = s.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT s.Id as id, s.No as no, COALESCE(ch.Name, '') as channelName,
                COALESCE(sup.Name, '') as supplierName, COALESCE(co.CN, '') as country,
                s.Piece as totalPiece, s.Weight as totalWeight,
                COALESCE(s.Charge, 0) as totalCharge, COALESCE(s.Cost, 0) as totalCost,
                s.Audit as status, COALESCE(s.AuditName, '') as auditName, s.AddTime as addTime
         FROM ${t("Shipment")} s
         LEFT JOIN ${t("Channel")} ch ON ch.Id = s.Channel
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = s.Supplier
         LEFT JOIN ${t("Country")} co ON co.Id = s.Country
         ${where} ORDER BY s.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccShipment[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listStowages(params: ListParams & { status?: number }): Promise<ListResult<AccStowage>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("s.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("s.AddTime <= ?"); values.push(params.dateTo); }
      if (params.status !== undefined) { conditions.push("s.Status = ?"); values.push(params.status); }
      if (params.keyword) {
        conditions.push("(s.No LIKE ? OR s.Flight LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const statusTexts = ["录单中", "国内出发", "国内抵达", "离境出发", "国外抵达", "清关完成", "出口查验", "航班延误", "清关查验"];

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Stowage")} s ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT s.Id as id, s.No as no, COALESCE(s.Flight, '') as flight,
                COALESCE(dp.Name, '') as departurePort, COALESCE(ap.Name, '') as arrivalPort,
                s.Status as status, s.Piece as totalPiece, s.Weight as totalWeight,
                COALESCE(s.Volume, 0) as totalVolume,
                COALESCE(s.ETD, '') as etd, COALESCE(s.ETA, '') as eta, s.AddTime as addTime
         FROM ${t("Stowage")} s
         LEFT JOIN ${t("Stowage_Port")} dp ON dp.Id = s.DeparturePort
         LEFT JOIN ${t("Stowage_Port")} ap ON ap.Id = s.ArrivalPort
         ${where} ORDER BY s.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, statusText: statusTexts[r.status] ?? "未知" }));
      return { data: data as AccStowage[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listTracks(expressId: number): Promise<AccTrack[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT tp.Id as id, e.No as expressNo, COALESCE(et.TrackNo, '') as trackNo,
                tp.Status as status, tp.Detail as detail, tp.AddTime as updateTime
         FROM ${t("Express_TrackProcess")} tp
         LEFT JOIN ${t("Express")} e ON e.Id = tp.Express
         LEFT JOIN ${t("Express_TrackNo")} et ON et.Id = tp.TrackNo
         WHERE tp.Express = ? ORDER BY tp.AddTime DESC`, [expressId]
      );
      return rows as unknown as AccTrack[];
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  财务管理 - Finance
  // ═══════════════════════════════════════════

  async listCharges(params: ListParams & { customerId?: number; auditStatus?: number }): Promise<ListResult<AccCharge>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      if (params.customerId) { conditions.push("a.Customer = ?"); values.push(params.customerId); }
      if (params.auditStatus !== undefined) { conditions.push("a.Audit = ?"); values.push(params.auditStatus); }
      if (params.keyword) {
        conditions.push("(b.No LIKE ? OR b.TrackNo LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Express_Charge")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, b.No as expressNo,
                COALESCE(cust.Name, '') as customerName,
                COALESCE(pr.Name, '') as productName,
                COALESCE(co.CN, '') as country,
                b.ChargeWeight as chargeWeight,
                a.Type as type, a.Amount as amount, a.Paid as paid,
                a.TheDate as theDate, COALESCE(a.AuditName, '') as auditName,
                COALESCE(a.Remark, '') as remark
         FROM ${t("Express_Charge")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express
         LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer
         LEFT JOIN ${t("Product")} pr ON pr.Id = b.Product
         LEFT JOIN ${t("Country")} co ON co.Id = b.Country
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccCharge[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCosts(params: ListParams & { supplierId?: number; auditStatus?: number }): Promise<ListResult<AccCost>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      if (params.supplierId) { conditions.push("a.Supplier = ?"); values.push(params.supplierId); }
      if (params.auditStatus !== undefined) { conditions.push("a.Audit = ?"); values.push(params.auditStatus); }
      if (params.keyword) {
        conditions.push("(b.No LIKE ? OR b.TrackNo LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Express_Cost")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, b.No as expressNo,
                COALESCE(sup.Name, '') as supplierName,
                COALESCE(ch.Name, '') as channelName,
                COALESCE(co.CN, '') as country,
                b.ChargeWeight as channelWeight,
                a.Type as type, a.Amount as amount, a.Paid as paid,
                a.TheDate as theDate, COALESCE(a.AuditName, '') as auditName,
                COALESCE(a.Remark, '') as remark
         FROM ${t("Express_Cost")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier
         LEFT JOIN ${t("Channel")} ch ON ch.Id = b.Channel
         LEFT JOIN ${t("Country")} co ON co.Id = b.Country
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccCost[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listBills(params: ListParams & { status?: number; auditStatus?: number }): Promise<ListResult<AccBill>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      if (params.status !== undefined) { conditions.push("a.Status = ?"); values.push(params.status); }
      if (params.auditStatus !== undefined) { conditions.push("a.Audit = ?"); values.push(params.auditStatus); }
      if (params.keyword) {
        conditions.push("(a.No LIKE ? OR cust.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Customer_Bill")} a
         LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(cust.Name, '') as customerName,
                a.Settlement as settlement, a.TheDate as theDate, a.EndDate as endDate,
                a.Amount as amount, COALESCE(a.Paid, 0) as paid, COALESCE(a.Unpay, 0) as unpay,
                a.Quantity as quantity, a.Status as status,
                COALESCE(emp.Name, '') as salesman, COALESCE(a.AuditName, '') as auditName
         FROM ${t("Customer_Bill")} a
         LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer
         LEFT JOIN ${t("Customer_Commission")} cc ON cc.Customer = a.Customer AND cc.isDefault = 1
         LEFT JOIN ${t("Employee")} emp ON emp.Id = cc.Salesman
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({
        ...r,
        settlement: SETTLEMENT_MAP[r.settlement] ?? r.settlement,
        status: BILL_STATUS_MAP[r.status] ?? r.status,
      }));
      return { data: data as AccBill[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listPayments(params: ListParams & { supplierId?: number }): Promise<ListResult<AccPayment>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      if (params.supplierId) { conditions.push("a.Supplier = ?"); values.push(params.supplierId); }
      if (params.keyword) {
        conditions.push("(a.No LIKE ? OR sup.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Pay")} a
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(sup.Name, '') as supplierName,
                COALESCE(bk.Name, '') as bankName,
                a.Amount as amount, a.TheDate as theDate,
                COALESCE(a.AuditName, '') as auditName, COALESCE(a.Remark, '') as remark
         FROM ${t("Pay")} a
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier
         LEFT JOIN ${t("Bank")} bk ON bk.Id = a.Bank
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccPayment[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listReceiveds(params: ListParams & { customerId?: number }): Promise<ListResult<AccReceived>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      if (params.customerId) { conditions.push("a.Customer = ?"); values.push(params.customerId); }
      if (params.keyword) {
        conditions.push("(a.No LIKE ? OR cust.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Received")} a
         LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(cust.Name, '') as customerName,
                COALESCE(bk.Name, '') as bankName,
                a.Amount as amount, a.TheDate as theDate,
                COALESCE(a.AuditName, '') as auditName, COALESCE(a.Remark, '') as remark
         FROM ${t("Received")} a
         LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer
         LEFT JOIN ${t("Bank")} bk ON bk.Id = a.Bank
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccReceived[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listProfitItems(params: ListParams): Promise<ListResult<AccProfitItem>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = ["a.Delivery BETWEEN 3 AND 6"];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      if (params.keyword) {
        conditions.push("(a.No LIKE ? OR b.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = `WHERE ${conditions.join(" AND ")}`;
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Express")} a
         LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(b.Name, '') as customerName,
                COALESCE(c.Name, '') as productName, COALESCE(d.CN, '') as country,
                a.ChargeWeight as chargeWeight, COALESCE(a.ChannelWeight, 0) as channelWeight,
                COALESCE(a.Paid, 0) as revenue, COALESCE(a.Cost, 0) as cost,
                COALESCE(a.Profit, 0) as profit, a.AddTime as theDate,
                CASE WHEN a.Received > 0 THEN true ELSE false END as received
         FROM ${t("Express")} a
         LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer
         LEFT JOIN ${t("Product")} c ON c.Id = a.Product
         LEFT JOIN ${t("Country")} d ON d.Id = a.Country
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccProfitItem[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCommissions(params: ListParams): Promise<ListResult<AccCommission>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.keyword) { conditions.push("Name LIKE ?"); values.push(`%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("Month >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("Month <= ?"); values.push(params.dateTo); }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Employee_Commission")} ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Type as type, Percent as percent,
                Month as month, Amount as amount, Quantity as quantity,
                Commission as commission
         FROM ${t("Employee_Commission")} ${where}
         ORDER BY Month DESC, Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({
        ...r,
        type: COMMISSION_TYPE_MAP[r.type] ?? r.type,
      }));
      return { data: data as AccCommission[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listTransfers(params: ListParams): Promise<ListResult<AccTransfer>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Transfer")} a ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(fb.Name, '') as fromBank, COALESCE(tb.Name, '') as toBank,
                a.Amount as amount, a.TheDate as theDate,
                COALESCE(a.Remark, '') as remark, COALESCE(a.AddName, '') as addName
         FROM ${t("Transfer")} a
         LEFT JOIN ${t("Bank")} fb ON fb.Id = a.FromBank
         LEFT JOIN ${t("Bank")} tb ON tb.Id = a.ToBank
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccTransfer[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCurrencies(): Promise<AccCurrency[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Code as code, Symbol as symbol,
                Rate as rate, \`Decimal\` as \`decimal\`
         FROM ${t("Currency")} ORDER BY TheOrder, Id`
      );
      return rows as unknown as AccCurrency[];
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  客户与供应商 - Customer & Supplier
  // ═══════════════════════════════════════════

  async listCustomers(params: ListParams & {
    branch?: string;
    grade?: string;
  }): Promise<ListResult<AccCustomer>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.branch) { conditions.push("br.Name = ?"); values.push(params.branch); }
      if (params.grade) { conditions.push("c.Grade = ?"); values.push(params.grade); }
      if (params.keyword) {
        conditions.push("(c.Name LIKE ? OR c.Code LIKE ? OR c.Contact LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Customer")} c
         LEFT JOIN ${t("Branch")} br ON br.Id = c.Branch ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT c.Id as id, c.Code as code, c.Name as name,
                COALESCE(c.Contact, '') as contact, COALESCE(c.Mobile, '') as mobile,
                COALESCE(c.Email, '') as email,
                COALESCE(c.Credits, 0) as credits, COALESCE(c.Balance, 0) as balance,
                c.Settlement as settlement,
                COALESCE(br.Name, '') as branch, COALESCE(g.Name, '') as \`group\`,
                COALESCE(emp.Name, '') as salesman,
                c.Grade as grade, CASE WHEN c.isOpen = 1 THEN true ELSE false END as isActive
         FROM ${t("Customer")} c
         LEFT JOIN ${t("Branch")} br ON br.Id = c.Branch
         LEFT JOIN ${t("Customer_Group")} g ON g.Id = c.Group
         LEFT JOIN ${t("Customer_Commission")} cc ON cc.Customer = c.Id AND cc.isDefault = 1
         LEFT JOIN ${t("Employee")} emp ON emp.Id = cc.Salesman
         ${where} ORDER BY c.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({
        ...r,
        settlement: SETTLEMENT_MAP[r.settlement] ?? r.settlement,
      }));
      return { data: data as AccCustomer[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSuppliers(params: ListParams): Promise<ListResult<AccSupplier>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.keyword) {
        conditions.push("(Name LIKE ? OR Contact LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Supplier")} ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Contact, '') as contact,
                COALESCE(Mobile, '') as mobile, COALESCE(Phone, '') as phone,
                COALESCE(Email, '') as email, COALESCE(Address, '') as address,
                COALESCE(Product, '') as product, COALESCE(Balance, 0) as balance,
                Settlement as settlement, COALESCE(Remark, '') as remark
         FROM ${t("Supplier")} ${where}
         ORDER BY TheOrder DESC, Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({
        ...r,
        settlement: SETTLEMENT_MAP[r.settlement] ?? r.settlement,
      }));
      return { data: data as AccSupplier[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listChannels(): Promise<AccChannel[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code, '') as code,
                CASE WHEN isOpen = 1 THEN true ELSE false END as isOpen,
                CASE WHEN isDebug = 1 THEN true ELSE false END as isDebug,
                COALESCE(Remark, '') as remark
         FROM ${t("Channel")} ORDER BY TheOrder DESC, Id DESC`
      );
      return rows as unknown as AccChannel[];
    } catch { return []; }
  }

  async listChannelAccounts(params: ListParams): Promise<ListResult<AccChannelAccount>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.keyword) {
        conditions.push("(a.Name LIKE ? OR a.Code LIKE ? OR ch.Name LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Channel_Account")} a
         LEFT JOIN ${t("Channel")} ch ON ch.Id = a.Channel ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(ch.Name, '') as channelName,
                a.Name as name, COALESCE(a.Code, '') as code,
                COALESCE(sup.Name, '') as supplierName,
                CASE WHEN a.isOpen = 1 THEN true ELSE false END as isOpen
         FROM ${t("Channel_Account")} a
         LEFT JOIN ${t("Channel")} ch ON ch.Id = a.Channel
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccChannelAccount[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listProducts(params: ListParams): Promise<ListResult<AccProduct>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.keyword) {
        conditions.push("(p.Name LIKE ? OR p.Code LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Product")} p ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT p.Id as id, p.Name as name, COALESCE(p.Code, '') as code,
                COALESCE(ch.Name, '') as channelName,
                COALESCE(sup.Name, '') as supplierName,
                CASE WHEN p.isOpen = 1 THEN true ELSE false END as isOpen,
                COALESCE(p.Remark, '') as remark
         FROM ${t("Product")} p
         LEFT JOIN ${t("Channel")} ch ON ch.Id = p.Channel
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = p.Supplier
         ${where} ORDER BY p.TheOrder DESC, p.Id DESC LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccProduct[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  人事与组织 - HR & Org
  // ═══════════════════════════════════════════

  async listEmployees(params: ListParams & { status?: number }): Promise<ListResult<AccEmployee>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];

      if (params.status !== undefined) { conditions.push("e.Status = ?"); values.push(params.status); }
      if (params.keyword) {
        conditions.push("(e.Name LIKE ? OR e.Mobile LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }

      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Employee")} e ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT e.Id as id, e.Name as name, e.Gender as gender,
                COALESCE(e.Mobile, '') as mobile, COALESCE(e.Email, '') as email,
                COALESCE(dep.Name, '') as department, COALESCE(br.Name, '') as branch,
                COALESCE(e.Position, '') as position, e.Status as status,
                COALESCE(e.EntryDate, '') as entryDate
         FROM ${t("Employee")} e
         LEFT JOIN ${t("Department")} dep ON dep.Id = e.Department
         LEFT JOIN ${t("Branch")} br ON br.Id = e.Branch
         ${where} ORDER BY e.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({
        ...r,
        gender: r.gender === 0 ? "男" : "女",
        status: EMPLOYEE_STATUS_MAP[r.status] ?? r.status,
      }));
      return { data: data as AccEmployee[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listBranches(): Promise<AccBranch[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code, '') as code,
                COALESCE(Contact, '') as contact, COALESCE(Phone, '') as phone,
                COALESCE(Address, '') as address, COALESCE(Remark, '') as remark
         FROM ${t("Branch")} ORDER BY TheOrder, Id`
      );
      return rows as unknown as AccBranch[];
    } catch { return []; }
  }

  async listDepartments(): Promise<AccDepartment[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT d.Id as id, d.Name as name, COALESCE(br.Name, '') as branchName,
                COALESCE(d.Remark, '') as remark
         FROM ${t("Department")} d
         LEFT JOIN ${t("Branch")} br ON br.Id = d.Branch
         ORDER BY d.TheOrder, d.Id`
      );
      return rows as unknown as AccDepartment[];
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  基础数据 - Config & Reference
  // ═══════════════════════════════════════════

  async listCountries(params?: { keyword?: string }): Promise<AccCountry[]> {
    if (!this.pool) return [];
    try {
      const conditions: string[] = [];
      const values: any[] = [];
      if (params?.keyword) {
        conditions.push("(Name LIKE ? OR CN LIKE ? OR Code LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(CN, '') as cn,
                COALESCE(Code, '') as code,
                CASE WHEN isOpen = 1 THEN true ELSE false END as isOpen
         FROM ${t("Country")} ${where} ORDER BY TheOrder, Id`, values
      );
      return rows as unknown as AccCountry[];
    } catch { return []; }
  }

  async listRemotes(params: ListParams): Promise<ListResult<AccRemote>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) {
        conditions.push("(a.Postcode LIKE ? OR co.CN LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`);
      }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Remote")} a
         LEFT JOIN ${t("Country")} co ON co.Id = a.Country ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Postcode as postcode, COALESCE(co.CN, '') as country,
                COALESCE(sup.Name, '') as supplierName, COALESCE(a.Type, '') as type
         FROM ${t("Remote")} a
         LEFT JOIN ${t("Country")} co ON co.Id = a.Country
         LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows as unknown as AccRemote[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listFuels(): Promise<AccFuel[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Rate as rate,
                COALESCE(StartDate, '') as startDate, COALESCE(EndDate, '') as endDate
         FROM ${t("Fuel")} ORDER BY Id DESC`
      );
      return rows as unknown as AccFuel[];
    } catch { return []; }
  }

  async listHSCodes(params: ListParams): Promise<ListResult<AccHSCode>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) {
        conditions.push("(Code LIKE ? OR NameEN LIKE ? OR NameCN LIKE ?)");
        values.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`);
      }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;

      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("HSCode")} ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Code as code, COALESCE(NameEN, '') as nameEN,
                COALESCE(NameCN, '') as nameCN
         FROM ${t("HSCode")} ${where} ORDER BY Code LIMIT ? OFFSET ?`,
        [...values, limit, offset]
      );
      return { data: rows as unknown as AccHSCode[], total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  订单扩展 - Orders Extended
  // ═══════════════════════════════════════════

  async listCollects(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ?)"); values.push(`%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Express_Orders")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.Piece as piece, a.Weight as weight,
                a.Status as status, COALESCE(a.Remark, '') as remark, a.AddTime as addTime,
                COALESCE(a.AddName, '') as addName
         FROM ${t("Express_Orders")} a ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listDetains(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR d.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const statusMap = ["待扣件", "待放行", "已扣件", "已放行", "已退件"];
      const typeMap = ["系统扣件", "人工扣件", "客户扣件"];
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Detain")} a LEFT JOIN ${t("Customer")} d ON d.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(d.Name, '') as customerName,
                a.Status as statusCode, a.Type as typeCode,
                COALESCE(a.Remark, '') as reason, a.AddTime as addTime, COALESCE(a.AddName, '') as addName
         FROM ${t("Detain")} a LEFT JOIN ${t("Customer")} d ON d.Id = a.Customer
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, status: statusMap[r.statusCode] ?? r.statusCode, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listAsks(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.Text LIKE ? OR a.No LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const statusMap = ["待处理", "处理中", "待反馈", "已关闭"];
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Ask")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(a.No, '') as expressNo, a.Text as content,
                a.Source as source, a.Type as type, a.Status as statusCode,
                a.AddTime as addTime, COALESCE(a.AddName, '') as addName
         FROM ${t("Ask")} a ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, status: statusMap[r.statusCode] ?? r.statusCode }));
      return { data, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  物流扩展 - Logistics Extended
  // ═══════════════════════════════════════════

  async listPackages(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR a.Consignee LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Online_package")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(a.Consignee, '') as consignee,
                COALESCE(a.Company, '') as company, COALESCE(co.CN, '') as country,
                a.Piece as piece, a.Quantity as quantity, a.DeclaredValue as declaredValue,
                COALESCE(a.Postcode, '') as postcode, a.AddTime as addTime
         FROM ${t("Online_package")} a LEFT JOIN ${t("Country")} co ON co.Id = a.Country
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listTransits(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR b.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Transit")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(a.Type, '') as type,
                a.Quantity as quantity, a.Piece as piece, a.Weight as weight,
                COALESCE(b.Name, '') as supplierName, COALESCE(a.Tariff, 0) as tariff,
                COALESCE(a.Amount, 0) as amount, COALESCE(a.Remark, '') as remark,
                COALESCE(a.AddName, '') as addName
         FROM ${t("Transit")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listPorts(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Consignee, '') as consignee,
                COALESCE(Company, '') as company, COALESCE(Type, '') as type,
                COALESCE(Remark, '') as remark
         FROM ${t("Stowage_Port")} ORDER BY TheOrder, Id`
      );
      return rows;
    } catch { return []; }
  }

  async listWarehouses(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, COALESCE(a.Code, '') as code,
                COALESCE(a.Consignee, '') as consignee, COALESCE(a.Company, '') as company,
                COALESCE(co.CN, '') as country, COALESCE(a.Province, '') as province,
                COALESCE(a.Postcode, '') as postcode, a.Type as type
         FROM ${t("Online_Warehouse")} a LEFT JOIN ${t("Country")} co ON co.Id = a.Country ORDER BY a.Id DESC`
      );
      const typeMap = ["亚马逊", "海外仓"];
      return (rows as any[]).map(r => ({ ...r, type: typeMap[r.type] ?? r.type }));
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  财务扩展 - Finance Extended
  // ═══════════════════════════════════════════

  async listFees(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Count, 0) as itemCount,
                COALESCE(Total, 0) as linkedProducts, COALESCE(Remark, '') as remark
         FROM ${t("Fee")} ORDER BY TheOrder DESC, Id DESC`
      );
      return rows;
    } catch { return []; }
  }

  async listFeeTypes(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Type, '') as type,
                COALESCE(Unit, '') as unit, COALESCE(Method, '') as method,
                COALESCE(Remark, '') as remark
         FROM ${t("Fee_Type")} ORDER BY TheOrder DESC, Id DESC`
      );
      return rows;
    } catch { return []; }
  }

  async listCustomerAdjusts(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(b.Name LIKE ? OR a.Reason LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Customer_Adjust")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(a.AddName, '') as addName, a.AddTime as addTime,
                COALESCE(b.Name, '') as customerName, a.Amount as amount,
                COALESCE(a.Reason, '') as reason, a.Audit as audit, COALESCE(a.Remark, '') as remark
         FROM ${t("Customer_Adjust")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSupplierAdjusts(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(b.Name LIKE ? OR a.Reason LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Supplier_Adjust")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(a.AddName, '') as addName, a.AddTime as addTime,
                COALESCE(b.Name, '') as supplierName, a.Amount as amount,
                COALESCE(a.Reason, '') as reason, a.Audit as audit, COALESCE(a.Remark, '') as remark
         FROM ${t("Supplier_Adjust")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCustomerFines(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR b.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Customer_Fine")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name, '') as customerName,
                a.Amount as amount, COALESCE(a.Remark, '') as remark, COALESCE(a.AuditName, '') as auditName
         FROM ${t("Customer_Fine")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSupplierFines(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR b.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Supplier_Fine")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name, '') as supplierName,
                a.Amount as amount, COALESCE(a.Remark, '') as remark, COALESCE(a.AuditName, '') as auditName
         FROM ${t("Supplier_Fine")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCustomerRefunds(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = ["a.Amount < 0"];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR cust.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = `WHERE ${conditions.join(" AND ")}`;
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Received")} a LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(cust.Name, '') as customerName,
                ABS(a.Amount) as amount, a.TheDate as theDate,
                COALESCE(a.AuditName, '') as auditName, COALESCE(a.Remark, '') as remark
         FROM ${t("Received")} a LEFT JOIN ${t("Customer")} cust ON cust.Id = a.Customer
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSupplierRefunds(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = ["a.Amount < 0"];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR sup.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = `WHERE ${conditions.join(" AND ")}`;
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Pay")} a LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, COALESCE(sup.Name, '') as supplierName,
                ABS(a.Amount) as amount, a.TheDate as theDate,
                COALESCE(a.AuditName, '') as auditName, COALESCE(a.Remark, '') as remark
         FROM ${t("Pay")} a LEFT JOIN ${t("Supplier")} sup ON sup.Id = a.Supplier
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCustomerRebates(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR b.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Customer_Rebate")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name, '') as customerName,
                a.Amount as amount, COALESCE(a.Remark, '') as remark, COALESCE(a.AuditName, '') as auditName
         FROM ${t("Customer_Rebate")} a LEFT JOIN ${t("Customer")} b ON b.Id = a.Customer
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSupplierRebates(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR b.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Supplier_Rebate")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name, '') as supplierName,
                a.Amount as amount, COALESCE(a.Remark, '') as remark, COALESCE(a.AuditName, '') as auditName
         FROM ${t("Supplier_Rebate")} a LEFT JOIN ${t("Supplier")} b ON b.Id = a.Supplier
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listReparations(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(b.No LIKE ? OR d.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.AddTime >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.AddTime <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Reparation")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express LEFT JOIN ${t("Customer")} d ON d.Id = b.Customer ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, b.No as expressNo, COALESCE(d.Name, '') as customerName,
                a.Amount as applyAmount, COALESCE(a.Paid, 0) as paidAmount,
                COALESCE(a.Remark, '') as reason, a.Audit as audit,
                a.AddTime as addTime, COALESCE(a.AddName, '') as addName
         FROM ${t("Reparation")} a
         LEFT JOIN ${t("Express")} b ON b.Id = a.Express LEFT JOIN ${t("Customer")} d ON d.Id = b.Customer
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listExpenses(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.Name LIKE ? OR a.Remark LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Expenses")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, a.TheDate as theDate,
                COALESCE(cat.Name, '') as category, a.Amount as amount,
                COALESCE(bk.Name, '') as bankName,
                COALESCE(a.Remark, '') as remark, COALESCE(a.AuditName, '') as auditName,
                COALESCE(a.AddName, '') as addName
         FROM ${t("Expenses")} a
         LEFT JOIN ${t("Expenses_Category")} cat ON cat.Id = a.Category
         LEFT JOIN ${t("Bank")} bk ON bk.Id = a.Bank
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listBanks(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, COALESCE(b.Name, '') as currency,
                COALESCE(a.Deposit, 0) as deposit, COALESCE(a.Remark, '') as remark,
                COALESCE(a.LastUpdate, '') as lastUpdate,
                CASE WHEN a.isShow = 1 THEN true ELSE false END as isShow
         FROM ${t("Bank")} a LEFT JOIN ${t("Currency")} b ON b.Id = a.Currency
         WHERE a.isOpen = 1 ORDER BY a.TheOrder DESC, a.Id DESC`
      );
      return rows;
    } catch { return []; }
  }

  async listDividends(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.No LIKE ? OR a.Name LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const typeMap = ["入股", "分红"];
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Dividend")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, a.Type as typeCode,
                COALESCE(a.Name, '') as name, a.Amount as amount,
                COALESCE(bk.Name, '') as bankName, COALESCE(a.Remark, '') as remark,
                COALESCE(a.AuditName, '') as auditName
         FROM ${t("Dividend")} a LEFT JOIN ${t("Bank")} bk ON bk.Id = a.Bank
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listBorrowings(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.Name LIKE ? OR a.Remark LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const typeMap = ["借入", "借出", "还款", "回款", "还息", "收息"];
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Borrowing")} a ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, a.TheDate as theDate, a.Type as typeCode,
                a.Amount as amount, COALESCE(a.Rate, 0) as rate,
                COALESCE(a.Remark, '') as remark, COALESCE(a.AddName, '') as addName
         FROM ${t("Borrowing")} a ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  客户/供应商扩展 - Customer/Supplier Extended
  // ═══════════════════════════════════════════

  async listCustomerGroups(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Remark, '') as remark FROM ${t("Customer_Group")} ORDER BY TheOrder, Id`
      );
      return rows;
    } catch { return []; }
  }

  async listProductItems(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(NameEN LIKE ? OR NameCN LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Product_Item")} ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, COALESCE(NameEN, '') as nameEN, COALESCE(NameCN, '') as nameCN,
                COALESCE(HSCode, '') as hsCode
         FROM ${t("Product_Item")} ${where} ORDER BY Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listPostcodes(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("(a.Postcode LIKE ? OR co.CN LIKE ?)"); values.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Postcode")} a LEFT JOIN ${t("Country")} co ON co.Id = a.Country ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Postcode as postcode, COALESCE(co.CN, '') as country,
                COALESCE(a.City, '') as city, COALESCE(a.Province, '') as province
         FROM ${t("Postcode")} a LEFT JOIN ${t("Country")} co ON co.Id = a.Country
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listZones(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Count, 0) as zoneCount,
                COALESCE(Country, 0) as countryCount, COALESCE(Remark, '') as remark
         FROM ${t("Zone")} ORDER BY TheOrder, Id`
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  人事扩展 - HR Extended
  // ═══════════════════════════════════════════

  async listWages(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("Name LIKE ?"); values.push(`%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("Month >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("Month <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const [countRows] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Employee_wage")} ${where}`, values);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Month as month,
                COALESCE(Basic, 0) as basic, COALESCE(Bonus, 0) as bonus,
                COALESCE(Commission, 0) as commission, COALESCE(Deduction, 0) as deduction,
                COALESCE(Total, 0) as total, COALESCE(AuditName, '') as auditName
         FROM ${t("Employee_wage")} ${where} ORDER BY Month DESC, Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      return { data: rows, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listAttendances(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool;
      const conditions: string[] = [];
      const values: any[] = [];
      if (params.keyword) { conditions.push("b.Name LIKE ?"); values.push(`%${params.keyword}%`); }
      if (params.dateFrom) { conditions.push("a.TheDate >= ?"); values.push(params.dateFrom); }
      if (params.dateTo) { conditions.push("a.TheDate <= ?"); values.push(params.dateTo); }
      const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
      const limit = params.pageSize ?? 50;
      const offset = ((params.page ?? 1) - 1) * limit;
      const typeMap = ["免打卡", "事假", "病假", "法定假", "迟到", "早退", "旷工", "违纪", "加班", "假日加班"];
      const [countRows] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Employee_Attence")} a LEFT JOIN ${t("Employee")} b ON b.Id = a.Employee ${where}`, values
      );
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(b.Name, '') as name, a.TheDate as theDate,
                a.Type as typeCode, COALESCE(a.Hours, 0) as hours, COALESCE(a.Remark, '') as remark
         FROM ${t("Employee_Attence")} a LEFT JOIN ${t("Employee")} b ON b.Id = a.Employee
         ${where} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...values, limit, offset]
      );
      const data = (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: countRows[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  汇总报表 - Reports
  // ═══════════════════════════════════════════

  async getProfitReport(dateFrom: string, dateTo: string): Promise<AccProfitSummary> {
    const empty: AccProfitSummary = { totalRevenue: 0, totalCost: 0, totalProfit: 0, orderCount: 0, byBranch: [] };
    if (!this.pool) return empty;
    try {
      const pool = this.pool;
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT COALESCE(br.Name, '未分配') as branch,
                COUNT(DISTINCT o.Id) as cnt,
                COALESCE(SUM(chg.TotalCharge), 0) as revenue,
                COALESCE(SUM(cost.TotalCost), 0) as cost
         FROM ${t("Express")} o
         LEFT JOIN ${t("Branch")} br ON br.Id = o.Branch
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCharge FROM ${t("Express_Charge")} GROUP BY OrderId) chg ON chg.OrderId = o.Id
         LEFT JOIN (SELECT OrderId, SUM(Charge) as TotalCost FROM ${t("Express_Cost")} GROUP BY OrderId) cost ON cost.OrderId = o.Id
         WHERE o.AddTime BETWEEN ? AND ?
         GROUP BY br.Name`, [dateFrom, dateTo]
      );

      let totalRevenue = 0, totalCost = 0, totalCount = 0;
      const byBranch = (rows as any[]).map(r => {
        const revenue = Number(r.revenue);
        const cost = Number(r.cost);
        totalRevenue += revenue;
        totalCost += cost;
        totalCount += Number(r.cnt);
        return { branch: r.branch, revenue, cost, profit: revenue - cost, count: Number(r.cnt) };
      });

      return { totalRevenue, totalCost, totalProfit: totalRevenue - totalCost, orderCount: totalCount, byBranch };
    } catch { return empty; }
  }

  async getStats(): Promise<{
    orderCount: number;
    customerCount: number;
    supplierCount: number;
    channelCount: number;
  }> {
    if (!this.pool) return { orderCount: 0, customerCount: 0, supplierCount: 0, channelCount: 0 };
    try {
      const pool = this.pool;
      const [[orders], [customers], [suppliers], [channels]] = await Promise.all([
        pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Express")}`),
        pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Customer")}`),
        pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Supplier")}`),
        pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Channel")} WHERE isOpen = 1`),
      ]);
      return {
        orderCount: orders[0]?.cnt ?? 0,
        customerCount: customers[0]?.cnt ?? 0,
        supplierCount: suppliers[0]?.cnt ?? 0,
        channelCount: channels[0]?.cnt ?? 0,
      };
    } catch { return { orderCount: 0, customerCount: 0, supplierCount: 0, channelCount: 0 }; }
  }

  // ═══════════════════════════════════════════
  //  补充模块 - Additional Modules
  // ═══════════════════════════════════════════

  async listAssets(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.Name LIKE ?)"); v.push(`%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Assets")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, a.TheDate as theDate, COALESCE(b.Name,'') as currency,
                COALESCE(a.Amount,0) as amount, COALESCE(a.Depreciation,0) as depreciation,
                COALESCE(a.Surplus,0) as surplus, COALESCE(a.Month,0) as month, COALESCE(a.Remark,'') as remark
         FROM ${t("Assets")} a LEFT JOIN ${t("Currency")} b ON b.Id=a.Currency
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listBankNames(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Remark,'') as remark FROM ${t("Bank_Name")} ORDER BY TheOrder, Id`);
      return rows;
    } catch { return []; }
  }

  async listStowageCategories(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Color,'') as color, COALESCE(Remark,'') as remark FROM ${t("Stowage_Category")} ORDER BY TheOrder, Id`);
      return rows;
    } catch { return []; }
  }

  async listClientNotices(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(Name LIKE ? OR Title LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Client_Notice")} ${w}`, v);
      const categoryMap = ['', '业务调整通知', '公司重要公告', '系统更新通知', '常用文档下载'];
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, COALESCE(Name,'') as name, COALESCE(Title,'') as title,
                COALESCE(Summary,'') as summary, Category as categoryCode, AddTime as addTime
         FROM ${t("Client_Notice")} ${w} ORDER BY TheOrder DESC, Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, category: categoryMap[r.categoryCode] ?? r.categoryCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCycles(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.Name LIKE ?)"); v.push(`%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const cycleMap = ['每年', '每月', '每周', '每天'];
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Cycle")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, a.StartDate as startDate, a.EndDate as endDate,
                a.Cycle as cycleCode, COALESCE(b.Name,'') as currency, a.Amount as amount,
                COALESCE(c.Name,'') as account, COALESCE(a.Remark,'') as remark
         FROM ${t("Cycle")} a LEFT JOIN ${t("Currency")} b ON b.Id=a.Currency
         LEFT JOIN ${t("Bank")} c ON c.Id=a.Account
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, cycle: cycleMap[r.cycleCode] ?? r.cycleCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listDispatches(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.No LIKE ? OR b.Name LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const statusMap = ['待收件', '已安排', '已收货', '已取消'];
      const [cr] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Dispatch")} a LEFT JOIN ${t("Customer")} b ON b.Id=a.Customer ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name,'') as customerName,
                COALESCE(c.Name,'') as productName, a.Count as count, a.Piece as piece,
                a.Weight as weight, a.Volume as volume, COALESCE(d.Name,'') as employee,
                COALESCE(a.Picker,'') as picker, COALESCE(a.Phone,'') as phone,
                a.Status as statusCode, COALESCE(a.Remark,'') as remark
         FROM ${t("Dispatch")} a LEFT JOIN ${t("Customer")} b ON b.Id=a.Customer
         LEFT JOIN ${t("Product")} c ON c.Id=a.Product LEFT JOIN ${t("Employee")} d ON d.Id=a.Employee
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, status: statusMap[r.statusCode] ?? r.statusCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listDistricts(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(Name LIKE ? OR CN LIKE ? OR Code2 LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("District")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(CN,'') as cn, COALESCE(Code2,'') as code2,
                COALESCE(Code3,'') as code3, COALESCE(Phone,'') as phone, Parent as parent
         FROM ${t("District")} ${w} ORDER BY TheOrder, Id LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listExpenseCategories(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const typeMap = ['管理费用', '销售费用', '财务费用', '采购费用'];
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Type as typeCode, CASE WHEN isComing=1 THEN true ELSE false END as isComing,
                COALESCE(Remark,'') as remark FROM ${t("Expenses_Category")} ORDER BY TheOrder, Id`);
      return (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
    } catch { return []; }
  }

  async listFunds(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("a.Month LIKE ?"); v.push(`%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Fund")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Month as month, a.TheDate as theDate,
                COALESCE(a.MinWage,0) as minWage, COALESCE(a.MaxWage,0) as maxWage,
                COALESCE(a.Rate,0) as rate, COALESCE(a.Amount,0) as amount,
                COALESCE(b.Name,'') as bankName, COALESCE(a.Remark,'') as remark
         FROM ${t("Fund")} a LEFT JOIN ${t("Bank")} b ON b.Id=a.Bank
         ${w} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listFundPersons(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("Name LIKE ?"); v.push(`%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Fund_Person")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code,'') as code,
                COALESCE(Wage,0) as wage, COALESCE(Rate,0) as rate,
                COALESCE(Company,'') as company, COALESCE(Remark,'') as remark
         FROM ${t("Fund_Person")} ${w} ORDER BY Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listLogistics(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code,'') as code,
                CASE WHEN isOpen=1 THEN true ELSE false END as isOpen,
                COALESCE(Remark,'') as remark FROM ${t("Logistics")} ORDER BY TheOrder, Id`);
      return rows;
    } catch { return []; }
  }

  async listForecasts(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.No LIKE ? OR a.Consignee LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Forecast_Package")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.No as no, a.TheDate as theDate, COALESCE(b.Name,'') as customerName,
                COALESCE(c.Name,'') as productName, COALESCE(a.Consignee,'') as consignee,
                COALESCE(a.Company,'') as company, COALESCE(co.CN,'') as country,
                COALESCE(a.Postcode,'') as postcode, a.AddTime as addTime
         FROM ${t("Forecast_Package")} a LEFT JOIN ${t("Customer")} b ON b.Id=a.Customer
         LEFT JOIN ${t("Product")} c ON c.Id=a.Product LEFT JOIN ${t("Country")} co ON co.Id=a.Country
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listPotentials(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(Name LIKE ? OR Contacts LIKE ? OR Phone LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const statusMap = ['待分配', '公关中', '待回复', '不合作', '已签单'];
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Potential")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Contacts,'') as contacts,
                COALESCE(Phone,'') as phone, COALESCE(Address,'') as address,
                COALESCE(Product,'') as product, Status as statusCode,
                COALESCE(QQ,'') as qq, COALESCE(Weixin,'') as weixin, AddTime as addTime
         FROM ${t("Potential")} ${w} ORDER BY Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, status: statusMap[r.statusCode] ?? r.statusCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listReceivedSMS(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.Name LIKE ? OR a.Account LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Received_SMS")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(a.Name,'') as name, COALESCE(a.Account,'') as account,
                COALESCE(a.Pay,'') as pay, COALESCE(a.Amount,0) as amount,
                COALESCE(a.Time,'') as time, COALESCE(b.Name,'') as bankName,
                COALESCE(a.Remark,'') as remark
         FROM ${t("Received_SMS")} a LEFT JOIN ${t("Bank")} b ON b.Id=a.Binding
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listCommissionRules(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("b.Name LIKE ?"); v.push(`%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const typeMap = ['销售额', '利润额', '销售数量'];
      const [cr] = await pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Employee_Rule_Item")} a INNER JOIN ${t("Employee_Rule")} b ON b.Id=a.Rule ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(b.Name,'') as ruleName, a.Quota as quota,
                a.Type as typeCode, a.Percent as percent,
                COALESCE(a.StartDate,'') as startDate, COALESCE(a.EndDate,'') as endDate,
                COALESCE(a.Remark,'') as remark
         FROM ${t("Employee_Rule_Item")} a INNER JOIN ${t("Employee_Rule")} b ON b.Id=a.Rule
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSocials(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("a.Month LIKE ?"); v.push(`%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Social")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Month as month, a.TheDate as theDate,
                COALESCE(a.Wage,0) as wage, COALESCE(a.Average,0) as average,
                COALESCE(a.Rate,0) as rate, COALESCE(a.Amount,0) as amount,
                COALESCE(b.Name,'') as bankName, COALESCE(a.Remark,'') as remark
         FROM ${t("Social")} a LEFT JOIN ${t("Bank")} b ON b.Id=a.Bank
         ${w} ORDER BY a.TheDate DESC, a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSocialPersons(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("Name LIKE ?"); v.push(`%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const typeMap = ['不购买', '深户社保', '综合社保', '住院社保', '民工社保'];
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Social_Person")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code,'') as code,
                Type as typeCode, COALESCE(Wage,0) as wage,
                COALESCE(Rate,0) as rate, COALESCE(Remark,'') as remark
         FROM ${t("Social_Person")} ${w} ORDER BY Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      const data = (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
      return { data, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listSoldTos(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(a.Name LIKE ? OR a.Code LIKE ? OR a.Consignee LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Online_SoldTo")} a ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, COALESCE(a.Code,'') as code,
                COALESCE(a.Consignee,'') as consignee, COALESCE(a.Company,'') as company,
                COALESCE(b.CN,'') as country, COALESCE(a.Phone,'') as phone,
                COALESCE(a.Postcode,'') as postcode, COALESCE(a.Address,'') as address
         FROM ${t("Online_SoldTo")} a LEFT JOIN ${t("Country")} b ON b.Id=a.Country
         ${w} ORDER BY a.Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listStowageSteps(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const statusMap = ['录单中', '国内出发', '国内抵达', '离境出发', '国外抵达', '清关完成', '出口查验', '航班延误', '清关查验'];
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, a.Name as name, COALESCE(b.Name,'') as category,
                a.Status as statusCode, COALESCE(a.Remark,'') as remark
         FROM ${t("Stowage_Step")} a LEFT JOIN ${t("Stowage_Category")} b ON b.Id=a.Category
         ORDER BY b.TheOrder DESC, b.Id DESC, a.TheOrder DESC, a.Id DESC`);
      return (rows as any[]).map(r => ({ ...r, status: statusMap[r.statusCode] ?? r.statusCode }));
    } catch { return []; }
  }

  async listTasks(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("Name LIKE ?"); v.push(`%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Task")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Code,'') as code,
                COALESCE(\`Interval\`,0) as \`interval\`, COALESCE(Port,0) as port,
                COALESCE(Count,0) as count, CASE WHEN isOpen=1 THEN true ELSE false END as isOpen,
                COALESCE(Remark,'') as remark
         FROM ${t("Task")} ${w} ORDER BY TheOrder, Id LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listTemplates(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const pool = this.pool; const c: string[] = []; const v: any[] = [];
      if (params.keyword) { c.push("(Name LIKE ? OR Title LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const lim = params.pageSize ?? 50; const off = ((params.page ?? 1) - 1) * lim;
      const [cr] = await pool.query<RowDataPacket[]>(`SELECT COUNT(*) as cnt FROM ${t("Template")} ${w}`, v);
      const [rows] = await pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(Title,'') as title,
                CASE WHEN SendCustomer=1 THEN true ELSE false END as sendCustomer,
                CASE WHEN SendSelf=1 THEN true ELSE false END as sendSelf,
                CASE WHEN isSave=1 THEN true ELSE false END as isSave
         FROM ${t("Template")} ${w} ORDER BY Id DESC LIMIT ? OFFSET ?`, [...v, lim, off]);
      return { data: rows, total: cr[0]?.cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async listTrackItems(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const typeMap = ['人工轨迹', '追踪轨迹', '已提取', '送货中', '已签收', '普通延误', '严重延误', '信息错误', '补充费用', '快件丢失', '快件损坏', '快件拒收', '快件退件', '快件赔索'];
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, COALESCE(ShortName,'') as shortName, Type as typeCode
         FROM ${t("Track_Item")} ORDER BY Id`);
      return (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
    } catch { return []; }
  }

  async listFeeItemTypes(): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const typeMap = ['费用', '成本'];
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, Name as name, Type as typeCode, COALESCE(Color,'') as color,
                COALESCE(Remark,'') as remark FROM ${t("Express_Fee_Type")} ORDER BY TheOrder, Id`);
      return (rows as any[]).map(r => ({ ...r, type: typeMap[r.typeCode] ?? r.typeCode }));
    } catch { return []; }
  }

  async listQuickOrders(params: ListParams): Promise<ListResult<any>> {
    return this.listCollects(params);
  }

  // ═══════════════════════════════════════════
  //  通用 CRUD - Generic CRUD Operations
  // ═══════════════════════════════════════════

  async getRecord(table: string, id: number): Promise<Record<string, any> | null> {
    if (!this.pool) return null;
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT * FROM ${t(table)} WHERE Id = ?`, [id]
      );
      return rows.length ? (rows[0] as Record<string, any>) : null;
    } catch { return null; }
  }

  async insertRecord(table: string, fields: Record<string, any>): Promise<number> {
    if (!this.pool) return 0;
    try {
      const cols = Object.keys(fields).filter(k => /^[a-zA-Z_]\w*$/.test(k));
      if (cols.length === 0) return 0;
      const vals = cols.map(k => fields[k]);
      const [result] = await this.pool.query<any>(
        `INSERT INTO ${t(table)} (\`${cols.join('`, `')}\`) VALUES (${cols.map(() => '?').join(', ')})`,
        vals
      );
      return result.insertId ?? 0;
    } catch { return 0; }
  }

  async updateRecord(table: string, id: number, fields: Record<string, any>): Promise<boolean> {
    if (!this.pool) return false;
    try {
      const cols = Object.keys(fields).filter(k => /^[a-zA-Z_]\w*$/.test(k));
      if (cols.length === 0) return false;
      const sets = cols.map(k => `\`${k}\` = ?`).join(', ');
      const vals = [...cols.map(k => fields[k]), id];
      await this.pool.query(`UPDATE ${t(table)} SET ${sets} WHERE Id = ?`, vals);
      return true;
    } catch { return false; }
  }

  async deleteRecord(table: string, id: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(`DELETE FROM ${t(table)} WHERE Id = ?`, [id]);
      return true;
    } catch { return false; }
  }

  // ═══════════════════════════════════════════
  //  审核工作流 - Audit Workflow
  // ═══════════════════════════════════════════

  async auditRecord(table: string, id: number, auditor: string): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=? AND Audit=0`,
        [auditor, id]
      );
      return true;
    } catch { return false; }
  }

  async undoAudit(table: string, id: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=? AND Audit=1`,
        [id]
      );
      return true;
    } catch { return false; }
  }

  async batchAudit(table: string, ids: number[], auditor: string): Promise<number> {
    if (!this.pool || ids.length === 0) return 0;
    try {
      const placeholders = ids.map(() => '?').join(',');
      const [result] = await this.pool.query<any>(
        `UPDATE ${t(table)} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id IN (${placeholders}) AND Audit=0`,
        [auditor, ...ids]
      );
      return result.affectedRows ?? 0;
    } catch { return 0; }
  }

  async batchUndoAudit(table: string, ids: number[], _auditor: string): Promise<number> {
    if (!this.pool || ids.length === 0) return 0;
    try {
      const placeholders = ids.map(() => '?').join(',');
      const [result] = await this.pool.query<any>(
        `UPDATE ${t(table)} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id IN (${placeholders}) AND Audit=1`,
        ids
      );
      return result.affectedRows ?? 0;
    } catch { return 0; }
  }

  // ═══════════════════════════════════════════
  //  订单业务 - Order Business Logic
  // ═══════════════════════════════════════════

  async verifyOrder(id: number): Promise<{ ok: boolean; errors: string[] }> {
    if (!this.pool) return { ok: false, errors: ['数据库未连接'] };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT o.*, COUNT(e.Id) as itemCount FROM ${t("Express_Orders")} o
         LEFT JOIN ${t("Express")} e ON e.Orders=o.Id WHERE o.Id=?`, [id]
      );
      if (!rows.length) return { ok: false, errors: ['订单不存在'] };
      const o = rows[0];
      const errors: string[] = [];
      if (o.Audit !== 0) errors.push('订单已审核，不可重复审核');
      if (o.itemCount === 0) errors.push('订单下无快件，请先添加快件');
      return { ok: errors.length === 0, errors };
    } catch { return { ok: false, errors: ['验证失败'] }; }
  }

  async auditOrder(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const verify = await this.verifyOrder(id);
      if (!verify.ok) return { ok: false, error: verify.errors.join('; ') };
      await this.pool.query(
        `UPDATE ${t("Express_Orders")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=? AND Audit=0`,
        [auditor, id]
      );
      await this.pool.query(
        `UPDATE ${t("Express")} SET Type=1 WHERE Orders=? AND Type=0`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoOrder(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Express_Orders", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '订单未审核' };
      await this.pool.query(
        `UPDATE ${t("Express_Orders")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      await this.pool.query(
        `UPDATE ${t("Express")} SET Type=0 WHERE Orders=? AND Type=1`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async calculateVolume(length: number, width: number, height: number, divisor: number = 5000): Promise<number> {
    return (length * width * height) / divisor;
  }

  async calculateFreight(expressId: number): Promise<{ charge: number; cost: number; chargeWeight: number; error?: string }> {
    if (!this.pool) return { charge: 0, cost: 0, chargeWeight: 0, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT e.*, p.Name as productName, c.Name as customerName,
                COALESCE(ch.Name,'') as channelName
         FROM ${t("Express")} e
         LEFT JOIN ${t("Product")} p ON p.Id=e.Product
         LEFT JOIN ${t("Customer")} c ON c.Id=e.Customer
         LEFT JOIN ${t("Channel")} ch ON ch.Id=e.Channel
         WHERE e.Id=?`, [expressId]
      );
      if (!rows.length) return { charge: 0, cost: 0, chargeWeight: 0, error: '快件不存在' };
      const e = rows[0];
      const weight = e.Weight ?? 0;
      const volume = e.Volume ?? 0;
      const volumeWeight = volume * 167;
      const chargeWeight = Math.max(weight, volumeWeight);
      const charge = chargeWeight * 30;
      const cost = chargeWeight * 20;
      return { charge, cost, chargeWeight };
    } catch (e: any) { return { charge: 0, cost: 0, chargeWeight: 0, error: e.message }; }
  }

  async reloadFreight(expressId: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      const result = await this.calculateFreight(expressId);
      if (result.error) return false;
      await this.pool.query(
        `UPDATE ${t("Express")} SET ChargeWeight=?, SellCharge=?, CostCharge=? WHERE Id=?`,
        [result.chargeWeight, result.charge, result.cost, expressId]
      );
      return true;
    } catch { return false; }
  }

  async importOrders(data: Array<Record<string, any>>, auditor: string): Promise<{ imported: number; errors: string[] }> {
    if (!this.pool) return { imported: 0, errors: ['数据库未连接'] };
    const errors: string[] = [];
    let imported = 0;
    for (let i = 0; i < data.length; i++) {
      try {
        const row = data[i];
        const id = await this.insertRecord("Express", {
          No: row.No ?? row['单号'] ?? '',
          Customer: row.Customer ?? row['客户ID'] ?? 0,
          Product: row.Product ?? row['产品ID'] ?? 0,
          Country: row.Country ?? row['国家ID'] ?? 0,
          Piece: row.Piece ?? row['件数'] ?? 1,
          Weight: row.Weight ?? row['重量'] ?? 0,
          Volume: row.Volume ?? row['体积'] ?? 0,
          DeclaredValue: row.DeclaredValue ?? row['申报价值'] ?? 0,
          Remark: row.Remark ?? row['备注'] ?? '',
          AddName: auditor,
          AddTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
        });
        if (id > 0) imported++;
        else errors.push(`第${i + 1}行: 插入失败`);
      } catch (e: any) {
        errors.push(`第${i + 1}行: ${e.message}`);
      }
    }
    return { imported, errors };
  }

  // ═══════════════════════════════════════════
  //  出货业务 - Shipment Business Logic
  // ═══════════════════════════════════════════

  async verifyShipment(id: number): Promise<{ ok: boolean; errors: string[] }> {
    if (!this.pool) return { ok: false, errors: ['数据库未连接'] };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT s.*, COUNT(si.Id) as itemCount FROM ${t("Shipment")} s
         LEFT JOIN ${t("Shipment_Item")} si ON si.Shipment=s.Id WHERE s.Id=?`, [id]
      );
      if (!rows.length) return { ok: false, errors: ['出货单不存在'] };
      const s = rows[0];
      const errors: string[] = [];
      if (s.Audit !== 0) errors.push('已审核');
      if (s.itemCount === 0) errors.push('出货单下无快件');
      if (!s.Channel_Account) errors.push('未指定渠道账号');
      return { ok: errors.length === 0, errors };
    } catch { return { ok: false, errors: ['验证失败'] }; }
  }

  async auditShipment(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const verify = await this.verifyShipment(id);
      if (!verify.ok) return { ok: false, error: verify.errors.join('; ') };
      await this.pool.query(
        `UPDATE ${t("Shipment")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      await this.pool.query(
        `UPDATE ${t("Express")} e INNER JOIN ${t("Shipment_Item")} si ON si.Express=e.Id
         SET e.Delivery=1 WHERE si.Shipment=?`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoShipment(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      await this.pool.query(
        `UPDATE ${t("Shipment")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=? AND Audit=1`, [id]
      );
      await this.pool.query(
        `UPDATE ${t("Express")} e INNER JOIN ${t("Shipment_Item")} si ON si.Express=e.Id
         SET e.Delivery=0 WHERE si.Shipment=?`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async changeShipmentChannel(id: number, channelAccountId: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `UPDATE ${t("Shipment")} SET Channel_Account=? WHERE Id=? AND Audit=0`,
        [channelAccountId, id]
      );
      return true;
    } catch { return false; }
  }

  async getShipmentItems(shipmentId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT si.Id as id, si.Express as expressId, e.No as expressNo,
                COALESCE(c.Name,'') as customerName, COALESCE(p.Name,'') as productName,
                COALESCE(co.CN,'') as country, e.Piece as piece, e.Weight as weight,
                e.ChargeWeight as chargeWeight
         FROM ${t("Shipment_Item")} si
         INNER JOIN ${t("Express")} e ON e.Id=si.Express
         LEFT JOIN ${t("Customer")} c ON c.Id=e.Customer
         LEFT JOIN ${t("Product")} p ON p.Id=e.Product
         LEFT JOIN ${t("Country")} co ON co.Id=e.Country
         WHERE si.Shipment=? ORDER BY si.Id`, [shipmentId]
      );
      return rows;
    } catch { return []; }
  }

  async addShipmentItem(shipmentId: number, expressId: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `INSERT INTO ${t("Shipment_Item")} (Shipment, Express) VALUES (?, ?)`,
        [shipmentId, expressId]
      );
      await this.updateShipmentTotals(shipmentId);
      return true;
    } catch { return false; }
  }

  async removeShipmentItem(shipmentId: number, expressId: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `DELETE FROM ${t("Shipment_Item")} WHERE Shipment=? AND Express=?`,
        [shipmentId, expressId]
      );
      await this.updateShipmentTotals(shipmentId);
      return true;
    } catch { return false; }
  }

  private async updateShipmentTotals(shipmentId: number): Promise<void> {
    if (!this.pool) return;
    try {
      await this.pool.query(
        `UPDATE ${t("Shipment")} s SET
          Quantity=(SELECT COUNT(*) FROM ${t("Shipment_Item")} WHERE Shipment=s.Id),
          Piece=(SELECT COALESCE(SUM(e.Piece),0) FROM ${t("Shipment_Item")} si INNER JOIN ${t("Express")} e ON e.Id=si.Express WHERE si.Shipment=s.Id),
          Weight=(SELECT COALESCE(SUM(e.Weight),0) FROM ${t("Shipment_Item")} si INNER JOIN ${t("Express")} e ON e.Id=si.Express WHERE si.Shipment=s.Id)
         WHERE s.Id=?`, [shipmentId]
      );
    } catch { /* ignore */ }
  }

  // ═══════════════════════════════════════════
  //  运费审核 - Charge Audit & Calculation
  // ═══════════════════════════════════════════

  async auditCharge(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Express_Charge", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Express_Charge")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      if (rec.Customer) {
        await this.pool.query(
          `UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [rec.Amount ?? 0, rec.Customer]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoCharge(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Express_Charge", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Express_Charge")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Customer) {
        await this.pool.query(
          `UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [rec.Amount ?? 0, rec.Customer]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async batchAuditCharges(ids: number[], auditor: string): Promise<{ audited: number; errors: string[] }> {
    if (!this.pool || ids.length === 0) return { audited: 0, errors: [] };
    const errors: string[] = [];
    let audited = 0;
    for (const id of ids.slice(0, 500)) {
      const result = await this.auditCharge(id, auditor);
      if (result.ok) audited++;
      else errors.push(`ID ${id}: ${result.error}`);
    }
    return { audited, errors };
  }

  async getUnpaidCharges(customerId?: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const c: string[] = ["a.Audit=1", "a.Paid < a.Amount"];
      const v: any[] = [];
      if (customerId) { c.push("a.Customer=?"); v.push(customerId); }
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id as id, COALESCE(e.No,'') as expressNo, COALESCE(cu.Name,'') as customerName,
                a.Amount as amount, a.Paid as paid, (a.Amount-a.Paid) as unpaid, a.TheDate as theDate
         FROM ${t("Express_Charge")} a
         LEFT JOIN ${t("Express")} e ON e.Id=a.Express
         LEFT JOIN ${t("Customer")} cu ON cu.Id=a.Customer
         WHERE ${c.join(" AND ")} ORDER BY a.TheDate DESC LIMIT 500`, v
      );
      return rows;
    } catch { return []; }
  }

  async importCharges(data: Array<Record<string, any>>, auditor: string): Promise<{ imported: number; errors: string[] }> {
    if (!this.pool) return { imported: 0, errors: ['数据库未连接'] };
    const errors: string[] = [];
    let imported = 0;
    for (let i = 0; i < data.length; i++) {
      try {
        const row = data[i];
        const id = await this.insertRecord("Express_Charge", {
          Express: row.Express ?? row['快件ID'] ?? 0,
          Customer: row.Customer ?? row['客户ID'] ?? 0,
          Amount: row.Amount ?? row['金额'] ?? 0,
          TheDate: row.TheDate ?? row['日期'] ?? new Date().toISOString().slice(0, 10),
          Type: row.Type ?? 1,
          AddName: auditor,
          AddTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
        });
        if (id > 0) imported++;
        else errors.push(`第${i + 1}行: 插入失败`);
      } catch (e: any) {
        errors.push(`第${i + 1}行: ${e.message}`);
      }
    }
    return { imported, errors };
  }

  // ═══════════════════════════════════════════
  //  成本审核 - Cost Audit & Import
  // ═══════════════════════════════════════════

  async auditCost(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Express_Cost", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Express_Cost")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      if (rec.Supplier) {
        await this.pool.query(
          `UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [rec.Amount ?? 0, rec.Supplier]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoCost(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Express_Cost", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Express_Cost")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Supplier) {
        await this.pool.query(
          `UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [rec.Amount ?? 0, rec.Supplier]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async batchAuditCosts(ids: number[], auditor: string): Promise<{ audited: number; errors: string[] }> {
    if (!this.pool || ids.length === 0) return { audited: 0, errors: [] };
    const errors: string[] = [];
    let audited = 0;
    for (const id of ids.slice(0, 500)) {
      const result = await this.auditCost(id, auditor);
      if (result.ok) audited++;
      else errors.push(`ID ${id}: ${result.error}`);
    }
    return { audited, errors };
  }

  async importCosts(data: Array<Record<string, any>>, mappings: Record<string, string>, auditor: string): Promise<{ imported: number; errors: string[] }> {
    if (!this.pool) return { imported: 0, errors: ['数据库未连接'] };
    const errors: string[] = [];
    let imported = 0;
    for (let i = 0; i < data.length; i++) {
      try {
        const row = data[i];
        const mapped: Record<string, any> = { AddName: auditor, AddTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
        for (const [dbCol, fileCol] of Object.entries(mappings)) {
          if (row[fileCol] !== undefined) mapped[dbCol] = row[fileCol];
        }
        if (!mapped.Express && !mapped.No) { errors.push(`第${i + 1}行: 缺少快件单号`); continue; }
        if (mapped.No && !mapped.Express) {
          const [exRows] = await this.pool.query<RowDataPacket[]>(
            `SELECT Id FROM ${t("Express")} WHERE No=? LIMIT 1`, [mapped.No]
          );
          if (exRows.length) mapped.Express = exRows[0].Id;
          delete mapped.No;
        }
        const id = await this.insertRecord("Express_Cost", mapped);
        if (id > 0) imported++;
        else errors.push(`第${i + 1}行: 插入失败`);
      } catch (e: any) {
        errors.push(`第${i + 1}行: ${e.message}`);
      }
    }
    return { imported, errors };
  }

  // ═══════════════════════════════════════════
  //  客户账单 - Customer Bill Operations
  // ═══════════════════════════════════════════

  async generateBill(customerId: number, dateFrom: string, dateTo: string, auditor: string): Promise<{ ok: boolean; billId?: number; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [charges] = await this.pool.query<RowDataPacket[]>(
        `SELECT COALESCE(SUM(Amount),0) as total, COUNT(*) as cnt
         FROM ${t("Express_Charge")} WHERE Customer=? AND Audit=1 AND Bill=0
         AND TheDate>=? AND TheDate<=?`, [customerId, dateFrom, dateTo]
      );
      const chargeTotal = charges[0]?.total ?? 0;
      const chargeCount = charges[0]?.cnt ?? 0;
      if (chargeCount === 0) return { ok: false, error: '无可结算运费' };

      const [cust] = await this.pool.query<RowDataPacket[]>(
        `SELECT Balance, Settlement FROM ${t("Customer")} WHERE Id=?`, [customerId]
      );
      const priorBalance = cust[0]?.Balance ?? 0;

      const billNo = `B${Date.now().toString(36).toUpperCase()}`;
      const billId = await this.insertRecord("Customer_Bill", {
        No: billNo,
        Customer: customerId,
        TheDate: dateTo,
        Settlement: cust[0]?.Settlement ?? 0,
        Amount: chargeTotal,
        Paid: priorBalance > 0 ? Math.min(priorBalance, chargeTotal) : 0,
        Unpay: chargeTotal - (priorBalance > 0 ? Math.min(priorBalance, chargeTotal) : 0),
        Quantity: chargeCount,
        Status: 0,
        AddName: auditor,
        AddTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      });
      if (billId > 0) {
        await this.pool.query(
          `UPDATE ${t("Express_Charge")} SET Bill=? WHERE Customer=? AND Audit=1 AND Bill=0
           AND TheDate>=? AND TheDate<=?`, [billId, customerId, dateFrom, dateTo]
        );
      }
      return { ok: true, billId };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async reloadBill(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [sums] = await this.pool.query<RowDataPacket[]>(
        `SELECT COALESCE(SUM(Amount),0) as chargeTotal, COUNT(*) as cnt
         FROM ${t("Express_Charge")} WHERE Bill=? AND Audit=1`, [id]
      );
      const [refunds] = await this.pool.query<RowDataPacket[]>(
        `SELECT COALESCE(SUM(Amount),0) as total FROM ${t("Received")}
         WHERE Bill=? AND Audit=1`, [id]
      );
      const amount = sums[0]?.chargeTotal ?? 0;
      const paid = refunds[0]?.total ?? 0;
      await this.pool.query(
        `UPDATE ${t("Customer_Bill")} SET Amount=?, Paid=?, Unpay=?, Quantity=? WHERE Id=?`,
        [amount, paid, amount - paid, sums[0]?.cnt ?? 0, id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditBill(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Customer_Bill", id);
      if (!rec) return { ok: false, error: '账单不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Customer_Bill")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoBill(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      await this.pool.query(
        `UPDATE ${t("Customer_Bill")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=? AND Audit=1`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async getBillItems(billId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT c.Id as id, COALESCE(e.No,'') as expressNo, COALESCE(cu.Name,'') as customerName,
                c.Amount as amount, c.Paid as paid, c.TheDate as theDate, 'charge' as lineType
         FROM ${t("Express_Charge")} c
         LEFT JOIN ${t("Express")} e ON e.Id=c.Express
         LEFT JOIN ${t("Customer")} cu ON cu.Id=c.Customer
         WHERE c.Bill=? ORDER BY c.TheDate`, [billId]
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  收款审核 - Payment Collection
  // ═══════════════════════════════════════════

  async auditReceived(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Received", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      const actual = (rec.Amount ?? 0) - (rec.Poundage ?? 0);
      await this.pool.query(
        `UPDATE ${t("Received")} SET Audit=1, AuditName=?, AuditTime=NOW(), Actual=? WHERE Id=?`,
        [auditor, actual, id]
      );
      if (rec.Customer) {
        await this.pool.query(
          `UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [actual, rec.Customer]
        );
      }
      if (rec.Bank) {
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [actual, rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoReceived(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Received", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      const actual = rec.Actual ?? ((rec.Amount ?? 0) - (rec.Poundage ?? 0));
      await this.pool.query(
        `UPDATE ${t("Received")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Customer) {
        await this.pool.query(
          `UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [actual, rec.Customer]
        );
      }
      if (rec.Bank) {
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [actual, rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async quickPayment(customerId: number, amount: number, bankId: number, auditor: string): Promise<{ ok: boolean; id?: number; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const no = `R${Date.now().toString(36).toUpperCase()}`;
      const recId = await this.insertRecord("Received", {
        No: no, Customer: customerId, Bank: bankId,
        Amount: amount, Poundage: 0, Actual: amount,
        TheDate: new Date().toISOString().slice(0, 10),
        Audit: 1, AuditName: auditor,
        AuditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
        AddName: auditor,
        AddTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
      });
      if (recId > 0) {
        await this.pool.query(
          `UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, customerId]
        );
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, bankId]
        );
      }
      return { ok: recId > 0, id: recId };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  供应商付款 - Supplier Payment
  // ═══════════════════════════════════════════

  async auditPay(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Pay", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Pay")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      if (rec.Supplier) {
        await this.pool.query(
          `UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [rec.Amount ?? 0, rec.Supplier]
        );
      }
      if (rec.Bank) {
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [rec.Amount ?? 0, rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoPay(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Pay", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Pay")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Supplier) {
        await this.pool.query(
          `UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [rec.Amount ?? 0, rec.Supplier]
        );
      }
      if (rec.Bank) {
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [rec.Amount ?? 0, rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  利润分析 - Profit Analysis
  // ═══════════════════════════════════════════

  async getOverdueCosts(days: number = 30): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT e.Id as id, e.No as expressNo, COALESCE(c.Name,'') as customerName,
                COALESCE(p.Name,'') as productName, e.ChargeWeight as chargeWeight,
                e.SellCharge as revenue, e.CostCharge as cost,
                (e.SellCharge - e.CostCharge) as profit, e.AddTime as addTime,
                DATEDIFF(NOW(), e.AddTime) as ageDays
         FROM ${t("Express")} e
         LEFT JOIN ${t("Customer")} c ON c.Id=e.Customer
         LEFT JOIN ${t("Product")} p ON p.Id=e.Product
         WHERE e.isCostOK=0 AND DATEDIFF(NOW(), e.AddTime) > ?
         ORDER BY e.AddTime ASC LIMIT 500`, [days]
      );
      return rows;
    } catch { return []; }
  }

  async markCostComplete(ids: number[]): Promise<number> {
    if (!this.pool || ids.length === 0) return 0;
    try {
      const placeholders = ids.map(() => '?').join(',');
      const [result] = await this.pool.query<any>(
        `UPDATE ${t("Express")} SET isCostOK=1 WHERE Id IN (${placeholders})`, ids
      );
      return result.affectedRows ?? 0;
    } catch { return 0; }
  }

  async getProfitSummary(dateFrom: string, dateTo: string, groupBy: 'customer' | 'product' | 'branch' | 'employee' = 'customer'): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const groupCol = groupBy === 'customer' ? 'c.Name' : groupBy === 'product' ? 'p.Name' : groupBy === 'branch' ? 'b.Name' : 'emp.Name';
      const joins = [
        'LEFT JOIN ' + t("Customer") + ' c ON c.Id=e.Customer',
        'LEFT JOIN ' + t("Product") + ' p ON p.Id=e.Product',
        'LEFT JOIN ' + t("Branch") + ' b ON b.Id=e.Branch',
        'LEFT JOIN ' + t("Employee") + ' emp ON emp.Id=e.Employee',
      ].join(' ');
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT COALESCE(${groupCol},'未分配') as groupName,
                COUNT(*) as count,
                COALESCE(SUM(e.Piece),0) as pieces,
                COALESCE(SUM(e.ChargeWeight),0) as totalWeight,
                COALESCE(SUM(e.SellCharge),0) as revenue,
                COALESCE(SUM(e.CostCharge),0) as cost,
                COALESCE(SUM(e.SellCharge - e.CostCharge),0) as profit
         FROM ${t("Express")} e ${joins}
         WHERE e.TheDate>=? AND e.TheDate<=?
         GROUP BY ${groupCol} ORDER BY profit DESC`, [dateFrom, dateTo]
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  员工提成 - Commission Calculation
  // ═══════════════════════════════════════════

  async calculateCommission(month: string): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const dateFrom = month + '-01';
      const dateToDate = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0);
      const dateTo = dateToDate.toISOString().slice(0, 10);

      const [employees] = await this.pool.query<RowDataPacket[]>(
        `SELECT e.Id as id, e.Name as name, ri.Type as type, ri.Percent as percent, ri.Quota as quota
         FROM ${t("Employee")} e
         INNER JOIN ${t("Employee_Rule")} r ON r.Employee=e.Id
         INNER JOIN ${t("Employee_Rule_Item")} ri ON ri.Rule=r.Id
         WHERE e.Status IN (1,2)
         AND (ri.StartDate IS NULL OR ri.StartDate <= ?)
         AND (ri.EndDate IS NULL OR ri.EndDate >= ?)`,
        [dateTo, dateFrom]
      );

      const results: any[] = [];
      for (const emp of employees) {
        const [sales] = await this.pool.query<RowDataPacket[]>(
          `SELECT COALESCE(SUM(SellCharge),0) as sAmount,
                  COALESCE(SUM(SellCharge-CostCharge),0) as profit,
                  COUNT(*) as sQuantity
           FROM ${t("Express")} WHERE Employee=? AND TheDate>=? AND TheDate<=?`,
          [emp.id, dateFrom, dateTo]
        );
        const s = sales[0] ?? { sAmount: 0, profit: 0, sQuantity: 0 };
        let commission = 0;
        const pct = (emp.percent ?? 0) / 100;
        if (emp.type === 0) commission = s.sAmount * pct;
        else if (emp.type === 1) commission = s.profit * pct;
        else if (emp.type === 2) commission = s.sQuantity * (emp.percent ?? 0);
        results.push({
          employeeId: emp.id, name: emp.name, type: emp.type,
          percent: emp.percent, month,
          sAmount: s.sAmount, profit: s.profit, sQuantity: s.sQuantity,
          commission: Math.round(commission * 100) / 100,
        });
      }
      return results;
    } catch { return []; }
  }

  async auditCommission(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Employee_Commission", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Employee_Commission")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`,
        [auditor, id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoCommission(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Employee_Commission", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Employee_Commission")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  配载业务 - Stowage Business Logic
  // ═══════════════════════════════════════════

  async auditStowage(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Stowage", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Stowage")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoStowage(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      await this.pool.query(
        `UPDATE ${t("Stowage")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=? AND Audit=1`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async updateStowageStatus(id: number, status: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `UPDATE ${t("Stowage")} SET Status=? WHERE Id=?`, [status, id]
      );
      return true;
    } catch { return false; }
  }

  async syncStowage(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      await this.pool.query(
        `UPDATE ${t("Stowage")} s SET
          Quantity=(SELECT COUNT(*) FROM ${t("Online_Package")} WHERE Stowage=s.Id),
          Piece=(SELECT COALESCE(SUM(Piece),0) FROM ${t("Online_Package")} WHERE Stowage=s.Id),
          Weight=(SELECT COALESCE(SUM(Weight),0) FROM ${t("Online_Package")} WHERE Stowage=s.Id),
          Volume=(SELECT COALESCE(SUM(Volume),0) FROM ${t("Online_Package")} WHERE Stowage=s.Id),
          DeclaredValue=(SELECT COALESCE(SUM(DeclaredValue),0) FROM ${t("Online_Package")} WHERE Stowage=s.Id)
         WHERE s.Id=?`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async getStowagePackages(stowageId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT p.Id as id, p.No as no, COALESCE(c.Name,'') as customerName,
                COALESCE(p.Consignee,'') as consignee, COALESCE(co.CN,'') as country,
                p.Piece as piece, p.Weight as weight, p.Volume as volume,
                p.DeclaredValue as declaredValue, COALESCE(p.Postcode,'') as postcode
         FROM ${t("Online_Package")} p
         LEFT JOIN ${t("Customer")} c ON c.Id=p.Customer
         LEFT JOIN ${t("Country")} co ON co.Id=p.Country
         WHERE p.Stowage=? ORDER BY p.Id`, [stowageId]
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  转运审核 - Transit Audit
  // ═══════════════════════════════════════════

  async auditTransit(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Transit", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Transit")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoTransit(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      await this.pool.query(
        `UPDATE ${t("Transit")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=? AND Audit=1`, [id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  财务杂项审核 - Finance Misc Audit
  // ═══════════════════════════════════════════

  async auditExpense(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Expenses", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Expenses")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      if (rec.Bank) {
        const sign = rec.isComing ? 1 : -1;
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [sign * (rec.Amount ?? 0), rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoExpense(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Expenses", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Expenses")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Bank) {
        const sign = rec.isComing ? -1 : 1;
        await this.pool.query(
          `UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [sign * (rec.Amount ?? 0), rec.Bank]
        );
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditTransfer(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Transfer", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Transfer")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.FromBank) await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.FromBank]);
      if (rec.ToBank) await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.ToBank]);
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoTransfer(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Transfer", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Transfer")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.FromBank) await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.FromBank]);
      if (rec.ToBank) await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.ToBank]);
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditFine(table: 'Customer_Fine' | 'Supplier_Fine', id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Fine' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Fine' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoFine(table: 'Customer_Fine' | 'Supplier_Fine', id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Fine' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Fine' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditAdjust(table: 'Customer_Adjust' | 'Supplier_Adjust', id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Adjust' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Adjust' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoAdjust(table: 'Customer_Adjust' | 'Supplier_Adjust', id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Adjust' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Adjust' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditRebate(table: 'Customer_Rebate' | 'Supplier_Rebate', id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Rebate' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Rebate' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoRebate(table: 'Customer_Rebate' | 'Supplier_Rebate', id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord(table, id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t(table)} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (table === 'Customer_Rebate' && rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      } else if (table === 'Supplier_Rebate' && rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Supplier]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditDividend(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Dividend", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Dividend")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      if (rec.Bank) {
        const sign = rec.Type === 0 ? 1 : -1;
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [sign * (rec.Amount ?? 0), rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoDividend(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Dividend", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Dividend")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      if (rec.Bank) {
        const sign = rec.Type === 0 ? -1 : 1;
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [sign * (rec.Amount ?? 0), rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditBorrowing(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Borrowing", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Borrowing")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoBorrowing(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Borrowing", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Borrowing")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditWage(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Employee_wage", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Employee_wage")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const paid = rec.Paid ?? rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [paid, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoWage(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Employee_wage", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Employee_wage")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const paid = rec.Paid ?? rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [paid, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditAssets(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Assets", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Assets")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoAssets(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Assets", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Assets")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditFund(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Fund", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Fund")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoFund(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Fund", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Fund")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditSocial(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Social", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Social")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoSocial(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Social", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Social")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditReparation(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Reparation", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Reparation")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoReparation(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Reparation", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Reparation")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditReturn(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Back", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Back")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      }
      if (rec.Express) {
        await this.pool.query(`UPDATE ${t("Express")} SET Paid=Paid-? WHERE Id=?`, [amount, rec.Express]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoReturn(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Back", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Back")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      }
      if (rec.Express) {
        await this.pool.query(`UPDATE ${t("Express")} SET Paid=Paid+? WHERE Id=?`, [amount, rec.Express]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditCRefund(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Customer_Refund", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Customer_Refund")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Customer]);
      }
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoCRefund(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Customer_Refund", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Customer_Refund")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Customer) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Customer]);
      }
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async auditSRefund(id: number, auditor: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Supplier_Refund", id);
      if (!rec) return { ok: false, error: '记录不存在' };
      if (rec.Audit !== 0) return { ok: false, error: '已审核' };
      await this.pool.query(
        `UPDATE ${t("Supplier_Refund")} SET Audit=1, AuditName=?, AuditTime=NOW() WHERE Id=?`, [auditor, id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance-? WHERE Id=?`, [amount, rec.Supplier]);
      }
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit+? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async undoSRefund(id: number): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const rec = await this.getRecord("Supplier_Refund", id);
      if (!rec || rec.Audit === 0) return { ok: false, error: '未审核' };
      await this.pool.query(
        `UPDATE ${t("Supplier_Refund")} SET Audit=0, AuditName='', AuditTime=NULL WHERE Id=?`, [id]
      );
      const amount = rec.Amount ?? 0;
      if (rec.Supplier) {
        await this.pool.query(`UPDATE ${t("Supplier")} SET Balance=Balance+? WHERE Id=?`, [amount, rec.Supplier]);
      }
      if (rec.Bank) {
        await this.pool.query(`UPDATE ${t("Bank")} SET Deposit=Deposit-? WHERE Id=?`, [amount, rec.Bank]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  数据导出 - Export
  // ═══════════════════════════════════════════

  async exportModuleData(table: string, params: ListParams): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const c: string[] = [];
      const v: any[] = [];
      if (params.keyword) { c.push("(Name LIKE ? OR No LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { c.push("TheDate >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("TheDate <= ?"); v.push(params.dateTo); }
      const w = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT * FROM ${t(table)} ${w} ORDER BY Id DESC LIMIT 10000`, v
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  上门揽收 - Dispatch Operations
  // ═══════════════════════════════════════════

  async updateDispatchStatus(id: number, status: number): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(`UPDATE ${t("Dispatch")} SET Status=? WHERE Id=?`, [status, id]);
      return true;
    } catch { return false; }
  }

  // ═══════════════════════════════════════════
  //  快件轨迹 - Express Tracking
  // ═══════════════════════════════════════════

  async addTrackRecord(expressId: number, trackItemId: number, remark: string, operator: string): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `INSERT INTO ${t("Express_Process")} (Express, TrackItem, Remark, AddName, AddTime)
         VALUES (?, ?, ?, ?, NOW())`, [expressId, trackItemId, remark, operator]
      );
      return true;
    } catch { return false; }
  }

  async getExpressProcess(expressId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT p.Id as id, COALESCE(ti.Name,'') as trackName, COALESCE(ti.ShortName,'') as shortName,
                COALESCE(p.Remark,'') as remark, p.AddName as addName, p.AddTime as addTime
         FROM ${t("Express_Process")} p
         LEFT JOIN ${t("Track_Item")} ti ON ti.Id=p.TrackItem
         WHERE p.Express=? ORDER BY p.AddTime DESC`, [expressId]
      );
      return rows;
    } catch { return []; }
  }

  async updateTrackNo(expressId: number, trackNo: string): Promise<boolean> {
    if (!this.pool) return false;
    try {
      await this.pool.query(
        `UPDATE ${t("Express")} SET TrackNo=? WHERE Id=?`, [trackNo, expressId]
      );
      return true;
    } catch { return false; }
  }

  async getExpressTrackNos(expressId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id as id, No as no, COALESCE(Name,'') as name, COALESCE(Remark,'') as remark
         FROM ${t("Express_TrackNo")} WHERE Express=? ORDER BY Id`, [expressId]
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  在线取消 - Online Cancel / Void
  // ═══════════════════════════════════════════

  async listVoidOrders(params: ListParams): Promise<ListResult<any>> {
    if (!this.pool) return { data: [], total: 0 };
    try {
      const c: string[] = [];
      const v: any[] = [];
      if (params.keyword) { c.push("(b.No LIKE ? OR b.TrackNo LIKE ?)"); v.push(`%${params.keyword}%`, `%${params.keyword}%`); }
      if (params.dateFrom) { c.push("a.AddTime >= ?"); v.push(params.dateFrom); }
      if (params.dateTo) { c.push("a.AddTime <= ?"); v.push(params.dateTo); }
      const where = c.length ? `WHERE ${c.join(" AND ")}` : "";
      const values = [...v];
      const [[{ cnt }]] = await this.pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as cnt FROM ${t("Online_Void")} a LEFT JOIN ${t("Express")} b ON b.Id=a.Express ${where}`, values
      ) as any;
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.*, b.No, b.TrackNo, c.Name as CustomerName
         FROM ${t("Online_Void")} a LEFT JOIN ${t("Express")} b ON b.Id=a.Express
         LEFT JOIN ${t("Customer")} c ON c.Id=a.Customer
         ${where} ORDER BY a.Id DESC LIMIT ? OFFSET ?`,
        [...values, params.pageSize ?? 50, ((params.page ?? 1) - 1) * (params.pageSize ?? 50)]
      );
      return { data: rows as any[], total: cnt ?? 0 };
    } catch { return { data: [], total: 0 }; }
  }

  async voidOrder(expressId: number, operator: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id,Customer,Status,Currency,Amount,Charges,Surcharge,Additional,Discount,Paid,Cost,Profit FROM ${t("Express")} WHERE Id=?`, [expressId]
      );
      const exp = (rows as any[])[0];
      if (!exp) return { ok: false, error: '订单不存在' };
      if (exp.Status === 99) return { ok: false, error: '订单已作废' };

      await this.pool.query(
        `INSERT INTO ${t("Online_Void")} (Express,Customer,Status,ExpressStatus,Currency,Amount,Charges,Surcharge,Additional,Discount,Paid,Cost,Profit,AddName,AddTime)
         VALUES (?,?,1,?,?,?,?,?,?,?,?,?,?,?,NOW())`,
        [expressId, exp.Customer, exp.Status, exp.Currency ?? '', exp.Amount ?? 0, exp.Charges ?? 0,
         exp.Surcharge ?? 0, exp.Additional ?? 0, exp.Discount ?? 0, exp.Paid ?? 0, exp.Cost ?? 0, exp.Profit ?? 0, operator]
      );

      await this.pool.query(
        `UPDATE ${t("Express")} SET Status=99,Amount=0,Charges=0,Surcharge=0,Additional=0,Discount=0,Paid=0,Cost=0,Profit=0 WHERE Id=?`, [expressId]
      );

      // Move charges to void archive
      await this.pool.query(
        `INSERT INTO ${t("Express_Charge_Void")} SELECT * FROM ${t("Express_Charge")} WHERE Express=?`, [expressId]
      );
      await this.pool.query(`DELETE FROM ${t("Express_Charge")} WHERE Express=?`, [expressId]);

      // Restore customer balance
      const totalPaid = exp.Paid ?? 0;
      if (exp.Customer && totalPaid > 0) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance-? WHERE Id=?`, [totalPaid, exp.Customer]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async recoverVoidedOrder(expressId: number, operator: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT * FROM ${t("Online_Void")} WHERE Express=? AND Status=1 ORDER BY Id DESC LIMIT 1`, [expressId]
      );
      const v = (rows as any[])[0];
      if (!v) return { ok: false, error: '未找到作废记录' };

      await this.pool.query(
        `UPDATE ${t("Express")} SET Status=?,Amount=?,Charges=?,Surcharge=?,Additional=?,Discount=?,Paid=?,Cost=?,Profit=? WHERE Id=?`,
        [v.ExpressStatus, v.Amount, v.Charges, v.Surcharge, v.Additional, v.Discount, v.Paid, v.Cost, v.Profit, expressId]
      );

      // Restore charges from void archive
      await this.pool.query(
        `INSERT INTO ${t("Express_Charge")} SELECT * FROM ${t("Express_Charge_Void")} WHERE Express=?`, [expressId]
      );
      await this.pool.query(`DELETE FROM ${t("Express_Charge_Void")} WHERE Express=?`, [expressId]);

      await this.pool.query(`UPDATE ${t("Online_Void")} SET Status=3 WHERE Id=?`, [v.Id]);

      // Re-deduct customer balance
      const totalPaid = v.Paid ?? 0;
      if (v.Customer && totalPaid > 0) {
        await this.pool.query(`UPDATE ${t("Customer")} SET Balance=Balance+? WHERE Id=?`, [totalPaid, v.Customer]);
      }
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  在线制单 - Online Order Creation
  // ═══════════════════════════════════════════

  async createOnlineOrder(data: {
    customer: number; theDate: string; no?: string; trackNo?: string;
    country: number; postcode?: string; product?: number; type?: number;
    batteryType?: number; specialType?: number; materialsEN?: string; materialsCN?: string;
    piece?: number; weight?: number; volume?: number; declaredValue?: number; remark?: string;
    recipient: { company?: string; consignee: string; province?: string; city?: string; address: string; phone?: string; postcode?: string; taxNo?: string };
    items?: Array<{ name: string; cnName?: string; origin?: string; price: number; quantity: number; hsCode?: string }>;
  }, operator: string): Promise<{ ok: boolean; expressId?: number; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const d = data;
      // Insert Online (recipient) record
      const [onlineRes] = await this.pool.query<any>(
        `INSERT INTO ${t("Online")} (Company,Consignee,Province,City,Address,Phone,Postcode,TaxNo,Piece,Weight,Volume,DeclaredValue,AddName,AddTime,ModifyTime)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())`,
        [d.recipient.company ?? '', d.recipient.consignee, d.recipient.province ?? '', d.recipient.city ?? '',
         d.recipient.address, d.recipient.phone ?? '', d.recipient.postcode ?? '', d.recipient.taxNo ?? '',
         d.piece ?? 1, d.weight ?? 0, d.volume ?? 0, d.declaredValue ?? 0, operator]
      );
      const onlineId = onlineRes.insertId;

      // Insert Express record
      const [expRes] = await this.pool.query<any>(
        `INSERT INTO ${t("Express")} (Customer,No,TrackNo,TheDate,Country,Postcode,Product,Type,BatteryType,SpecialType,MaterialsEN,MaterialsCN,Piece,Weight,Volume,DeclaredValue,Receipt,Remark,Status,AddName,AddTime,ModifyTime)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,NOW(),NOW())`,
        [d.customer, d.no ?? '', d.trackNo ?? '', d.theDate, d.country, d.postcode ?? '',
         d.product ?? 0, d.type ?? 1, d.batteryType ?? 0, d.specialType ?? 0,
         d.materialsEN ?? '', d.materialsCN ?? '', d.piece ?? 1, d.weight ?? 0,
         d.volume ?? 0, d.declaredValue ?? 0, onlineId, d.remark ?? '', operator]
      );
      const expressId = expRes.insertId;

      // Insert declaration items
      if (d.items && d.items.length > 0) {
        const itemValues = d.items.map(it => [expressId, it.name, it.cnName ?? '', it.origin ?? '', it.price, it.quantity, it.hsCode ?? '']);
        for (const iv of itemValues) {
          await this.pool.query(
            `INSERT INTO ${t("Express_Item")} (Express,Name,CNName,Origin,Price,Quantity,HSCode) VALUES (?,?,?,?,?,?,?)`, iv
          );
        }
      }

      // Insert status log
      await this.pool.query(
        `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'创建订单',?,NOW())`, [expressId, operator]
      );

      return { ok: true, expressId };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  批量操作 - Express Batch Operations
  // ═══════════════════════════════════════════

  async batchUpdateWeights(updates: Array<{ id: number; weight: number }>, operator: string): Promise<{ ok: boolean; updated: number; error?: string }> {
    if (!this.pool) return { ok: false, updated: 0, error: '数据库未连接' };
    try {
      let count = 0;
      for (const u of updates) {
        await this.pool.query(
          `UPDATE ${t("Express")} SET ChargeWeight=?, ModifyTime=NOW() WHERE Id=?`, [u.weight, u.id]
        );
        await this.pool.query(
          `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'批量更新计重',?,NOW())`, [u.id, operator]
        );
        count++;
      }
      return { ok: true, updated: count };
    } catch (e: any) { return { ok: false, updated: 0, error: e.message }; }
  }

  async batchUpdateTrackNos(updates: Array<{ id: number; trackNo: string }>, operator: string): Promise<{ ok: boolean; updated: number; error?: string }> {
    if (!this.pool) return { ok: false, updated: 0, error: '数据库未连接' };
    try {
      let count = 0;
      for (const u of updates) {
        await this.pool.query(
          `UPDATE ${t("Express")} SET TrackNo=?, ModifyTime=NOW() WHERE Id=?`, [u.trackNo, u.id]
        );
        await this.pool.query(
          `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'批量更新单号',?,NOW())`, [u.id, operator]
        );
        count++;
      }
      return { ok: true, updated: count };
    } catch (e: any) { return { ok: false, updated: 0, error: e.message }; }
  }

  async batchUpdateCustomer(ids: number[], customerId: number, operator: string): Promise<{ ok: boolean; updated: number; error?: string }> {
    if (!this.pool || ids.length === 0) return { ok: false, updated: 0, error: '参数错误' };
    try {
      const placeholders = ids.map(() => '?').join(',');
      await this.pool.query(
        `UPDATE ${t("Express")} SET Customer=?, ModifyTime=NOW() WHERE Id IN (${placeholders})`, [customerId, ...ids]
      );
      for (const id of ids) {
        await this.pool.query(
          `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'批量更改客户',?,NOW())`, [id, operator]
        );
      }
      return { ok: true, updated: ids.length };
    } catch (e: any) { return { ok: false, updated: 0, error: e.message }; }
  }

  async batchUpdateRemarks(ids: number[], remark: string, operator: string): Promise<{ ok: boolean; updated: number; error?: string }> {
    if (!this.pool || ids.length === 0) return { ok: false, updated: 0, error: '参数错误' };
    try {
      const placeholders = ids.map(() => '?').join(',');
      await this.pool.query(
        `UPDATE ${t("Express")} SET Remark=?, ModifyTime=NOW() WHERE Id IN (${placeholders})`, [remark, ...ids]
      );
      return { ok: true, updated: ids.length };
    } catch (e: any) { return { ok: false, updated: 0, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  报表分析 - Reports & Analytics
  // ═══════════════════════════════════════════

  async getProductReport(dateFrom: string, dateTo: string): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Product, b.Name as ProductName, COUNT(*) as Count, SUM(a.Piece) as Piece,
                SUM(a.ChargeWeight) as Weight, SUM(a.CNY) as Paid, SUM(a.Profit) as Profit
         FROM ${t("Express")} a LEFT JOIN ${t("Product")} b ON b.Id=a.Product
         WHERE a.TheDate>=? AND a.TheDate<=? AND a.Status>0
         GROUP BY a.Product ORDER BY Paid DESC`, [dateFrom, dateTo]
      );
      return rows;
    } catch { return []; }
  }

  async getMonthlyReport(year: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT MONTH(TheDate) as Month, COUNT(*) as Count, SUM(Piece) as Piece,
                SUM(ChargeWeight) as Weight, SUM(CNY) as Paid, SUM(Profit) as Profit
         FROM ${t("Express")}
         WHERE YEAR(TheDate)=? AND Status>0
         GROUP BY MONTH(TheDate) ORDER BY Month`, [year]
      );
      return rows;
    } catch { return []; }
  }

  async getCountryReport(dateFrom: string, dateTo: string, limit = 20): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Country, b.CN as CountryName, COUNT(*) as Count, SUM(a.Piece) as Piece,
                SUM(a.ChargeWeight) as Weight, SUM(a.CNY) as Paid, SUM(a.Profit) as Profit
         FROM ${t("Express")} a LEFT JOIN ${t("Country")} b ON b.Id=a.Country
         WHERE a.TheDate>=? AND a.TheDate<=? AND a.Status>0
         GROUP BY a.Country ORDER BY Paid DESC LIMIT ?`, [dateFrom, dateTo, limit]
      );
      return rows;
    } catch { return []; }
  }

  async getCustomerReport(dateFrom: string, dateTo: string, limit = 50): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Customer, b.Name as CustomerName, COUNT(*) as Count, SUM(a.Piece) as Piece,
                SUM(a.ChargeWeight) as Weight, SUM(a.CNY) as Paid, SUM(a.Profit) as Profit
         FROM ${t("Express")} a LEFT JOIN ${t("Customer")} b ON b.Id=a.Customer
         WHERE a.TheDate>=? AND a.TheDate<=? AND a.Status>0
         GROUP BY a.Customer ORDER BY Paid DESC LIMIT ?`, [dateFrom, dateTo, limit]
      );
      return rows;
    } catch { return []; }
  }

  async getEmployeeReport(dateFrom: string, dateTo: string): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT b.Id as EmployeeId, b.Name as EmployeeName, COUNT(*) as Count, SUM(a.Piece) as Piece,
                SUM(a.ChargeWeight) as Weight, SUM(a.CNY) as Paid, SUM(a.Profit) as Profit
         FROM ${t("Express")} a LEFT JOIN ${t("Employee")} b ON b.Id=a.Employee
         WHERE a.TheDate>=? AND a.TheDate<=? AND a.Status>0
         GROUP BY b.Id ORDER BY Paid DESC`, [dateFrom, dateTo]
      );
      return rows;
    } catch { return []; }
  }

  // ═══════════════════════════════════════════
  //  仓库操作 - Warehouse / Mobile Operations
  // ═══════════════════════════════════════════

  async warehouseCheckin(no: string, operator: string): Promise<{ ok: boolean; expressId?: number; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id, Status FROM ${t("Express")} WHERE No=? OR TrackNo=? LIMIT 1`, [no, no]
      );
      const exp = (rows as any[])[0];
      if (!exp) return { ok: false, error: '未找到该运单' };

      await this.pool.query(
        `INSERT INTO ${t("Express_Stock")} (Express,Item,Status,isCheck,AddName,AddTime) VALUES (?,0,0,0,?,NOW())
         ON DUPLICATE KEY UPDATE Status=0, AddTime=NOW()`,
        [exp.Id, operator]
      );
      await this.pool.query(
        `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'仓库签入',?,NOW())`, [exp.Id, operator]
      );
      return { ok: true, expressId: exp.Id };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async warehouseCheckout(no: string, shipmentId: number, operator: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id FROM ${t("Express")} WHERE No=? OR TrackNo=? LIMIT 1`, [no, no]
      );
      const exp = (rows as any[])[0];
      if (!exp) return { ok: false, error: '未找到该运单' };

      await this.pool.query(
        `UPDATE ${t("Express_Stock")} SET Status=1 WHERE Express=?`, [exp.Id]
      );
      if (shipmentId) {
        await this.pool.query(
          `INSERT IGNORE INTO ${t("Shipment_Item")} (Shipment,Express) VALUES (?,?)`, [shipmentId, exp.Id]
        );
      }
      await this.pool.query(
        `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'仓库出货',?,NOW())`, [exp.Id, operator]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async warehouseInventoryCheck(operator: string): Promise<{ total: number; checked: number; unchecked: number }> {
    if (!this.pool) return { total: 0, checked: 0, unchecked: 0 };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT COUNT(*) as total, SUM(isCheck=1) as checked, SUM(isCheck=0) as unchecked
         FROM ${t("Express_Stock")} WHERE Status=0`
      );
      const r = (rows as any[])[0];
      return { total: r?.total ?? 0, checked: r?.checked ?? 0, unchecked: r?.unchecked ?? 0 };
    } catch { return { total: 0, checked: 0, unchecked: 0 }; }
  }

  async warehouseScanCheck(no: string, operator: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id, a.isCheck FROM ${t("Express_Stock")} a
         LEFT JOIN ${t("Express")} b ON b.Id=a.Express
         WHERE (b.No=? OR b.TrackNo=?) AND a.Status=0 LIMIT 1`, [no, no]
      );
      const stock = (rows as any[])[0];
      if (!stock) return { ok: false, error: '留仓中找不到该货件' };
      if (stock.isCheck === 1) return { ok: false, error: '已盘点' };

      await this.pool.query(
        `UPDATE ${t("Express_Stock")} SET isCheck=1, Time=NOW() WHERE Id=?`, [stock.Id]
      );
      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async warehouseResetCheck(): Promise<{ ok: boolean }> {
    if (!this.pool) return { ok: false };
    try {
      await this.pool.query(`UPDATE ${t("Express_Stock")} SET isCheck=0 WHERE Status=0`);
      return { ok: true };
    } catch { return { ok: false }; }
  }

  // ═══════════════════════════════════════════
  //  运费计算引擎 - Freight Calculation Engine
  // ═══════════════════════════════════════════

  async calculateFreightFull(params: {
    productId: number; type: number; batteryType?: number; specialType?: number;
    country: string; postcode?: string; customerId?: number;
    items: Array<{ piece: number; weight: number; length?: number; width?: number; height?: number }>;
    declaredValue?: number;
  }): Promise<{ ok: boolean; data?: any; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const { productId, type, country, postcode, customerId, items, declaredValue } = params;

      // 1. Get product and pricing info
      const [priceRows] = await this.pool.query<RowDataPacket[]>(
        `SELECT pd.Id as PriceId, pe.Id as ItemId, pe.Type as ItemType, pe.Method, pe.Header, pe.Value as PriceValue,
                pd.Currency, pd.VolumeRate, pd.MinWeight, pd.MaxWeight, pd.FuelType,
                pa.Name as ProductName, pb.Name as ChannelName, pa.Channel,
                COALESCE(pc.Fuel, 0) as FuelRate, pd.Fee
         FROM ${t("Product")} pa
         INNER JOIN ${t("Channel")} pb ON pb.Id=pa.Channel
         INNER JOIN ${t("Product_Price")} pd ON pd.Product=pa.Id AND pd.isOpen=1 AND pd.Type=?
         INNER JOIN ${t("Product_Item")} pe ON pe.Price=pd.Id AND pe.StartTime<NOW() AND pe.EndTime>NOW()
         LEFT JOIN ${t("Fuel")} pc ON pc.FuelType=COALESCE(NULLIF(pd.FuelType,0), pa.FuelType) AND pc.StartTime<NOW()
         WHERE pa.Id=?
         ORDER BY pc.StartTime DESC
         LIMIT 10`,
        [type, productId]
      );

      if (!priceRows.length) return { ok: false, error: '未找到匹配的价格表' };
      const price = (priceRows as any[])[0];

      // 2. Calculate billing weight
      const volumeRate = price.VolumeRate || 5000;
      let totalWeight = 0;
      let totalVolumeWeight = 0;

      for (const item of items) {
        const actualW = (item.weight || 0) * (item.piece || 1);
        const volW = (item.length && item.width && item.height)
          ? ((item.length * item.width * item.height) / volumeRate) * (item.piece || 1)
          : 0;
        totalWeight += actualW;
        totalVolumeWeight += volW;
      }

      const chargeWeight = Math.max(totalWeight, totalVolumeWeight);
      const billingWeight = Math.max(chargeWeight, price.MinWeight || 0);

      if (price.MaxWeight && billingWeight > price.MaxWeight) {
        return { ok: false, error: `超过最大重量限制 ${price.MaxWeight}kg` };
      }

      // 3. Look up price from zone/country mapping
      const [zoneRows] = await this.pool.query<RowDataPacket[]>(
        `SELECT pf.Zone, pg.Name as ZoneName, pf.Value as ZoneValue
         FROM ${t("Product_Zone")} pf
         LEFT JOIN ${t("Zone_Country")} pg ON pg.Id=pf.Zone
         WHERE pf.Item=? AND pg.Country LIKE ?
         LIMIT 1`,
        [price.ItemId, `%,${country},%`]
      );

      // 4. Calculate base freight from pricing structure
      let baseAmount = 0;
      const header = (price.Header || '').split(',').map(Number);
      const values = (price.PriceValue || '').split(',').map(Number);

      if (header.length > 0 && values.length > 0) {
        // Find weight bracket
        for (let i = 0; i < header.length - 1; i++) {
          if (billingWeight >= header[i] && billingWeight <= header[i + 1]) {
            baseAmount = values[i] || values[0] || 0;
            if (values[i] && String(values[i]).includes('.')) {
              baseAmount = billingWeight * values[i];
            }
            break;
          }
        }
        if (baseAmount === 0 && values.length > 0) {
          baseAmount = billingWeight * (values[values.length - 1] || values[0] || 0);
        }
      } else if ((zoneRows as any[]).length > 0) {
        const zoneValue = (zoneRows as any[])[0].ZoneValue;
        if (zoneValue) {
          const zoneValues = String(zoneValue).split(',').map(Number);
          for (let i = 0; i < header.length - 1; i++) {
            if (billingWeight >= header[i] && billingWeight <= header[i + 1]) {
              baseAmount = zoneValues[i] || 0;
              break;
            }
          }
          if (baseAmount === 0 && zoneValues.length > 0) {
            baseAmount = billingWeight * zoneValues[0];
          }
        }
      }

      // Fallback: simple per-kg rate from first value
      if (baseAmount === 0 && values.length > 0) {
        baseAmount = billingWeight * values[0];
      }

      baseAmount = Math.ceil(baseAmount * 100) / 100;

      // 5. Calculate fuel surcharge
      const fuelRate = price.FuelRate || 0;
      const fuelAmount = Math.ceil(baseAmount * (fuelRate / 100) * 100) / 100;

      // 6. Query additional surcharges
      let surchargeTotal = 0;
      const surchargeItems: any[] = [];

      if (price.Fee) {
        const [feeRows] = await this.pool.query<RowDataPacket[]>(
          `SELECT fa.Id, fb.Name, fb.Formula, fb.Condition, fb.MinAmount, fb.MaxAmount, fb.Unit
           FROM ${t("Fee_Item")} fa
           INNER JOIN ${t("Fee_Type")} fb ON fb.Id=fa.Type AND fb.isOpen=1
           WHERE fa.Fee=?`,
          [price.Fee]
        );
        for (const fee of feeRows as any[]) {
          let applicable = true;
          let amount = 0;

          // Evaluate simple conditions
          if (fee.Condition) {
            const cond = fee.Condition
              .replace('{计费重}', String(billingWeight))
              .replace('{实重}', String(totalWeight))
              .replace('{申报金额}', String(declaredValue ?? 0))
              .replace('{件数}', String(items.reduce((s, i) => s + (i.piece || 1), 0)));
            try { applicable = !!eval(cond); } catch { applicable = false; }
          }

          if (applicable && fee.Formula) {
            const formula = fee.Formula
              .replace('{计费重}', String(billingWeight))
              .replace('{实重}', String(totalWeight))
              .replace('{申报金额}', String(declaredValue ?? 0))
              .replace('{件数}', String(items.reduce((s, i) => s + (i.piece || 1), 0)));
            try { amount = eval(formula) || 0; } catch { amount = 0; }
          }

          if (amount > 0) {
            if (fee.MinAmount && amount < fee.MinAmount) amount = fee.MinAmount;
            if (fee.MaxAmount && amount > fee.MaxAmount) amount = fee.MaxAmount;
            surchargeTotal += amount;
            surchargeItems.push({ name: fee.Name, amount: Math.ceil(amount * 100) / 100 });
          }
        }
      }

      // 7. Get currency info
      const [currRows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Name, Rate FROM ${t("Currency")} WHERE Id=? LIMIT 1`, [price.Currency || 1]
      );
      const currency = (currRows as any[])[0] || { Name: 'CNY', Rate: 1 };

      const total = baseAmount + fuelAmount + surchargeTotal;
      const cnyTotal = Math.ceil(total * (currency.Rate || 1) * 100) / 100;

      return {
        ok: true,
        data: {
          productName: price.ProductName,
          channelName: price.ChannelName,
          currency: currency.Name,
          currencyRate: currency.Rate,
          chargeWeight: Math.ceil(billingWeight * 100) / 100,
          actualWeight: Math.ceil(totalWeight * 100) / 100,
          volumeWeight: Math.ceil(totalVolumeWeight * 100) / 100,
          volumeRate,
          baseAmount,
          fuelRate,
          fuelAmount,
          surcharges: surchargeItems,
          surchargeTotal: Math.ceil(surchargeTotal * 100) / 100,
          total: Math.ceil(total * 100) / 100,
          cnyTotal,
        }
      };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  面单管理 - Label Management
  // ═══════════════════════════════════════════

  async getLabelFile(expressId: number, labelType = 'PDF'): Promise<{ ok: boolean; data?: any; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id, Express, Type, Serial, TrackNo, Size, Ext, Hash, Time
         FROM ${t("Online_File")} WHERE Express=? AND Ext=? ORDER BY Id DESC LIMIT 1`,
        [expressId, labelType.toLowerCase() === 'zpl' ? 'zpl' : 'pdf']
      );
      const file = (rows as any[])[0];
      if (!file) return { ok: false, error: '未找到面单文件' };
      return { ok: true, data: file };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async listLabels(expressId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id, Type, Serial, TrackNo, Size, Ext, Hash, Time
         FROM ${t("Online_File")} WHERE Express=? ORDER BY Id`, [expressId]
      );
      return rows as any[];
    } catch { return []; }
  }

  async requestLabel(expressId: number, operator: string): Promise<{ ok: boolean; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      // Get the order info for label generation
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id, a.No, a.TrackNo, a.Product, a.Country, a.Postcode, a.Piece, a.Weight,
                b.Consignee, b.Company, b.Address, b.Phone, b.Province, b.City, b.Postcode as RecvPost,
                c.Name as ProductName, d.Channel, e.Name as ChannelName
         FROM ${t("Express")} a
         LEFT JOIN ${t("Online")} b ON b.Id=a.Receipt
         LEFT JOIN ${t("Product")} c ON c.Id=a.Product
         LEFT JOIN ${t("Channel")} d ON d.Id=c.Channel
         LEFT JOIN ${t("Channel_Account")} e ON e.Channel=d.Id AND e.isOpen=1
         WHERE a.Id=? LIMIT 1`, [expressId]
      );
      const order = (rows as any[])[0];
      if (!order) return { ok: false, error: '订单不存在' };

      // Record label request status
      await this.pool.query(
        `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'请求生成面单',?,NOW())`,
        [expressId, operator]
      );

      return { ok: true };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  // ═══════════════════════════════════════════
  //  物流轨迹查询 - Tracking Query
  // ═══════════════════════════════════════════

  async getTrackingInfo(expressId: number): Promise<{ ok: boolean; data?: any; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      // Get express info
      const [expRows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Id, No, TrackNo, Status, Delivery, Current, CurrentTime, DeliverdTime, ShipmentTime
         FROM ${t("Express")} WHERE Id=?`, [expressId]
      );
      const exp = (expRows as any[])[0];
      if (!exp) return { ok: false, error: '订单不存在' };

      // Get tracking history
      const [processRows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id, a.Time, a.TrackNo, b.Name as Activity, c.Name as Location
         FROM ${t("Express_Process")} a
         LEFT JOIN ${t("Track_Item")} b ON b.Id=a.Activity
         LEFT JOIN ${t("Track_Location")} c ON c.Id=a.Location
         WHERE a.Express=? ORDER BY a.Time DESC`, [expressId]
      );

      // Get all track numbers
      const [trackNos] = await this.pool.query<RowDataPacket[]>(
        `SELECT No, Name FROM ${t("Express_TrackNo")} WHERE Express=?`, [expressId]
      );

      return {
        ok: true,
        data: {
          no: exp.No,
          trackNo: exp.TrackNo,
          status: exp.Status,
          delivery: exp.Delivery,
          current: exp.Current,
          currentTime: exp.CurrentTime,
          deliveredTime: exp.DeliverdTime,
          shipmentTime: exp.ShipmentTime,
          trackNumbers: trackNos,
          history: processRows,
        }
      };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async queryTracking(expressIds: number[], operator: string): Promise<{ ok: boolean; queried: number; error?: string }> {
    if (!this.pool) return { ok: false, queried: 0, error: '数据库未连接' };
    try {
      // Record tracking query request for each order
      for (const id of expressIds) {
        await this.pool.query(
          `INSERT INTO ${t("Express_Status")} (Express,Text,AddName,AddTime) VALUES (?,'查询物流轨迹',?,NOW())`,
          [id, operator]
        );
      }
      return { ok: true, queried: expressIds.length };
    } catch (e: any) { return { ok: false, queried: 0, error: e.message }; }
  }

  async updateTrackingStatus(expressId: number, delivery: number, current: string, currentTime: string): Promise<{ ok: boolean }> {
    if (!this.pool) return { ok: false };
    try {
      await this.pool.query(
        `UPDATE ${t("Express")} SET Delivery=?, Current=?, CurrentTime=? WHERE Id=?`,
        [delivery, current, currentTime, expressId]
      );
      return { ok: true };
    } catch { return { ok: false }; }
  }

  async addTrackingEvent(expressId: number, trackNo: string, activity: number, location: number, time: string): Promise<{ ok: boolean }> {
    if (!this.pool) return { ok: false };
    try {
      await this.pool.query(
        `INSERT INTO ${t("Express_Process")} (Express,TrackNo,Activity,Location,Time) VALUES (?,?,?,?,?)`,
        [expressId, trackNo, activity, location, time]
      );
      return { ok: true };
    } catch { return { ok: false }; }
  }

  // ═══════════════════════════════════════════
  //  客户在线API - Customer Online API
  // ═══════════════════════════════════════════

  async apiAuthenticate(userId: string, sign: string, timestamp: string): Promise<{ ok: boolean; customerId?: number; error?: string }> {
    if (!this.pool) return { ok: false, error: '数据库未连接' };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Customer, b.APIKey FROM ${t("Customer_API")} a
         INNER JOIN ${t("Customer")} b ON b.Id=a.Customer
         WHERE a.UserId=? AND a.isOpen=1 LIMIT 1`, [userId]
      );
      const api = (rows as any[])[0];
      if (!api) return { ok: false, error: 'API账号无效' };

      // Validate signature (simple MD5 check)
      const crypto = await import('crypto');
      const expected = crypto.createHash('md5').update(`${userId}${timestamp}${api.APIKey}`).digest('hex');
      if (sign.toLowerCase() !== expected.toLowerCase()) {
        return { ok: false, error: '签名验证失败' };
      }

      return { ok: true, customerId: api.Customer };
    } catch (e: any) { return { ok: false, error: e.message }; }
  }

  async apiGetBalance(customerId: number): Promise<{ balance: number; credit: number }> {
    if (!this.pool) return { balance: 0, credit: 0 };
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT Balance, Credit FROM ${t("Customer")} WHERE Id=?`, [customerId]
      );
      const c = (rows as any[])[0];
      return { balance: c?.Balance ?? 0, credit: c?.Credit ?? 0 };
    } catch { return { balance: 0, credit: 0 }; }
  }

  async apiGetProducts(customerId: number): Promise<any[]> {
    if (!this.pool) return [];
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT DISTINCT pa.Id, pa.Name, pb.Name as ChannelName
         FROM ${t("Product")} pa
         INNER JOIN ${t("Channel")} pb ON pb.Id=pa.Channel
         INNER JOIN ${t("Product_Price")} pd ON pd.Product=pa.Id AND pd.isOpen=1
         INNER JOIN ${t("Product_Item")} pe ON pe.Price=pd.Id AND pe.StartTime<NOW() AND pe.EndTime>NOW()
         WHERE pa.isOpen=1
         ORDER BY pa.Name`
      );
      return rows as any[];
    } catch { return []; }
  }

  async apiQueryOrder(customerId: number, no: string): Promise<any> {
    if (!this.pool) return null;
    try {
      const [rows] = await this.pool.query<RowDataPacket[]>(
        `SELECT a.Id, a.No, a.TrackNo, a.Status, a.Delivery, a.Current, a.CurrentTime,
                a.TheDate, a.Piece, a.Weight, a.ChargeWeight, a.Paid, a.Country
         FROM ${t("Express")} a
         WHERE a.Customer=? AND (a.No=? OR a.TrackNo=?) LIMIT 1`,
        [customerId, no, no]
      );
      return (rows as any[])[0] || null;
    } catch { return null; }
  }

  async apiGetPrice(params: {
    productId: number; country: string; weight: number; postcode?: string;
    length?: number; width?: number; height?: number; declaredValue?: number;
  }): Promise<{ ok: boolean; data?: any; error?: string }> {
    return this.calculateFreightFull({
      productId: params.productId,
      type: 1,
      country: params.country,
      postcode: params.postcode,
      items: [{ piece: 1, weight: params.weight, length: params.length, width: params.width, height: params.height }],
      declaredValue: params.declaredValue,
    });
  }

  async close() {
    await this.pool?.end();
  }
}

export function createAccAdapter(): AccAdapter {
  return new AccAdapter({
    host: process.env.ACC_DB_HOST ?? "localhost",
    port: Number(process.env.ACC_DB_PORT ?? 3306),
    database: process.env.ACC_DB_NAME ?? "acc_db",
    user: process.env.ACC_DB_USER ?? "root",
    password: process.env.ACC_DB_PASS ?? "",
    apiUrl: process.env.ACC_API_URL ?? "http://localhost/acc/api",
  });
}
