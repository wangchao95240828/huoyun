import type { FastifyInstance } from "fastify";
import { createAccAdapter } from "../adapters/acc-adapter.js";

const acc = createAccAdapter();

function qp(v: unknown): string | undefined {
  return typeof v === "string" && v ? v : undefined;
}
function qn(v: unknown): number | undefined {
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

export async function accRoutes(app: FastifyInstance) {

  // ── Overview stats ──
  app.get("/api/acc/stats", async () => {
    return acc.getStats();
  });

  // ── Orders ──
  app.get("/api/acc/orders", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listOrders({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
      customerId: qn(q.customerId), branch: qp(q.branch),
      status: qp(q.status), country: qp(q.country), product: qp(q.product),
    });
  });

  app.get("/api/acc/orders/:id", async (req) => {
    const { id } = req.params as { id: string };
    const order = await acc.getOrderDetail(Number(id));
    if (!order) return { error: "not found" };
    const trackNos = await acc.getOrderTrackNos(Number(id));
    return { ...order, trackNos };
  });

  app.get("/api/acc/orders/:id/tracks", async (req) => {
    const { id } = req.params as { id: string };
    return acc.listTracks(Number(id));
  });

  // ── Returns ──
  app.get("/api/acc/returns", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listReturns({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
    });
  });

  // ── Shipments ──
  app.get("/api/acc/shipments", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listShipments({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo), status: qn(q.status),
    });
  });

  // ── Stowages ──
  app.get("/api/acc/stowages", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listStowages({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo), status: qn(q.status),
    });
  });

  // ── Charges (应收运费) ──
  app.get("/api/acc/charges", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCharges({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
      customerId: qn(q.customerId), auditStatus: qn(q.auditStatus),
    });
  });

  // ── Costs (应付成本) ──
  app.get("/api/acc/costs", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCosts({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
      supplierId: qn(q.supplierId), auditStatus: qn(q.auditStatus),
    });
  });

  // ── Bills (客户账单) ──
  app.get("/api/acc/bills", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listBills({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
      status: qn(q.status), auditStatus: qn(q.auditStatus),
    });
  });

  // ── Payments (供应商付款) ──
  app.get("/api/acc/payments", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listPayments({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo), supplierId: qn(q.supplierId),
    });
  });

  // ── Receiveds (客户收款) ──
  app.get("/api/acc/receiveds", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listReceiveds({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo), customerId: qn(q.customerId),
    });
  });

  // ── Profit ──
  app.get("/api/acc/profits", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listProfitItems({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
    });
  });

  app.get("/api/acc/profit-report", async (req) => {
    const q = req.query as Record<string, string>;
    const now = new Date();
    const dateFrom = q.dateFrom ?? new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
    const dateTo = q.dateTo ?? now.toISOString().slice(0, 10);
    return acc.getProfitReport(dateFrom, dateTo);
  });

  // ── Commissions (提成) ──
  app.get("/api/acc/commissions", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCommissions({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
    });
  });

  // ── Transfers (转账) ──
  app.get("/api/acc/transfers", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listTransfers({
      page: qn(q.page), pageSize: qn(q.pageSize),
      dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
    });
  });

  // ── Currencies ──
  app.get("/api/acc/currencies", async () => {
    return acc.listCurrencies();
  });

  // ── Customers ──
  app.get("/api/acc/customers", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCustomers({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      branch: qp(q.branch), grade: qp(q.grade),
    });
  });

  // ── Suppliers ──
  app.get("/api/acc/suppliers", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSuppliers({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
    });
  });

  // ── Channels ──
  app.get("/api/acc/channels", async () => {
    return acc.listChannels();
  });

  // ── Channel Accounts ──
  app.get("/api/acc/channel-accounts", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listChannelAccounts({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
    });
  });

  // ── Products (价格表) ──
  app.get("/api/acc/products", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listProducts({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
    });
  });

  // ── Employees ──
  app.get("/api/acc/employees", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listEmployees({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
      status: qn(q.status),
    });
  });

  // ── Branches ──
  app.get("/api/acc/branches", async () => {
    return acc.listBranches();
  });

  // ── Departments ──
  app.get("/api/acc/departments", async () => {
    return acc.listDepartments();
  });

  // ── Countries ──
  app.get("/api/acc/countries", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCountries({ keyword: qp(q.keyword) });
  });

  // ── Remotes (偏远邮编) ──
  app.get("/api/acc/remotes", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listRemotes({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
    });
  });

  // ── Fuels ──
  app.get("/api/acc/fuels", async () => {
    return acc.listFuels();
  });

  // ── HS Codes ──
  app.get("/api/acc/hscodes", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listHSCodes({
      page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword),
    });
  });

  // ══════════ Orders Extended ══════════

  app.get("/api/acc/collects", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCollects({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/detains", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listDetains({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/asks", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listAsks({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  // ══════════ Logistics Extended ══════════

  app.get("/api/acc/packages", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listPackages({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/transits", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listTransits({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/ports", async () => acc.listPorts());

  app.get("/api/acc/warehouses", async () => acc.listWarehouses());

  // ══════════ Finance Extended ══════════

  app.get("/api/acc/fees", async () => acc.listFees());

  app.get("/api/acc/fee-types", async () => acc.listFeeTypes());

  app.get("/api/acc/customer-adjusts", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCustomerAdjusts({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/supplier-adjusts", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSupplierAdjusts({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/customer-fines", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCustomerFines({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/supplier-fines", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSupplierFines({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/customer-refunds", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCustomerRefunds({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/supplier-refunds", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSupplierRefunds({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/customer-rebates", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCustomerRebates({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/supplier-rebates", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSupplierRebates({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/reparations", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listReparations({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/expenses", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listExpenses({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/banks", async () => acc.listBanks());

  app.get("/api/acc/dividends", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listDividends({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/borrowings", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listBorrowings({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  // ══════════ Customer/Supplier Extended ══════════

  app.get("/api/acc/customer-groups", async () => acc.listCustomerGroups());

  app.get("/api/acc/product-items", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listProductItems({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/postcodes", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listPostcodes({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/zones", async () => acc.listZones());

  // ══════════ HR Extended ══════════

  app.get("/api/acc/wages", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listWages({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/attendances", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listAttendances({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  // ══════════ New Modules ══════════

  app.get("/api/acc/quick-orders", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listQuickOrders({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/dispatches", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listDispatches({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/stowage-categories", async () => acc.listStowageCategories());

  app.get("/api/acc/stowage-steps", async () => acc.listStowageSteps());

  app.get("/api/acc/forecasts", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listForecasts({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/tracks", async () => acc.listTrackItems());

  app.get("/api/acc/assets", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listAssets({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/cycles", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCycles({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/received-sms", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listReceivedSMS({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/expense-categories", async () => acc.listExpenseCategories());

  app.get("/api/acc/fee-item-types", async () => acc.listFeeItemTypes());

  app.get("/api/acc/potentials", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listPotentials({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/sold-tos", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSoldTos({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/notices", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listClientNotices({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/socials", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSocials({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/social-persons", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listSocialPersons({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/funds", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listFunds({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo) });
  });

  app.get("/api/acc/fund-persons", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listFundPersons({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/commission-rules", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listCommissionRules({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/bank-names", async () => acc.listBankNames());

  app.get("/api/acc/districts", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listDistricts({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/logistics-interfaces", async () => acc.listLogistics());

  app.get("/api/acc/tasks", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listTasks({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  app.get("/api/acc/templates", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listTemplates({ page: qn(q.page), pageSize: qn(q.pageSize), keyword: qp(q.keyword) });
  });

  // ══════════ Business Operations ══════════

  // -- Audit workflow (generic) --
  app.post("/api/acc/:module/audit", async (req) => {
    const { module } = req.params as { module: string };
    const { id, ids, auditor = "admin" } = req.body as { id?: number; ids?: number[]; auditor?: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    if (ids && ids.length > 0) {
      const count = await acc.batchAudit(table, ids, auditor);
      return { ok: true, audited: count };
    }
    if (id) {
      const ok = await acc.auditRecord(table, id, auditor);
      return { ok };
    }
    return { ok: false, error: "missing id or ids" };
  });

  app.post("/api/acc/:module/undo", async (req) => {
    const { module } = req.params as { module: string };
    const { id, ids, auditor = "admin" } = req.body as { id?: number; ids?: number[]; auditor?: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    if (ids && ids.length > 0) {
      const count = await acc.batchUndoAudit(table, ids, auditor);
      return { ok: true, undone: count };
    }
    if (id) {
      const ok = await acc.undoAudit(table, id);
      return { ok };
    }
    return { ok: false, error: "missing id or ids" };
  });

  // -- Order business operations --
  app.post("/api/acc/orders/verify", async (req) => {
    const { id } = req.body as { id: number };
    return acc.verifyOrder(id);
  });

  app.post("/api/acc/orders/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditOrder(id, auditor);
  });

  app.post("/api/acc/orders/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoOrder(id);
  });

  app.post("/api/acc/orders/calc-freight", async (req) => {
    const { expressId } = req.body as { expressId: number };
    return acc.calculateFreight(expressId);
  });

  app.post("/api/acc/orders/reload-freight", async (req) => {
    const { expressId } = req.body as { expressId: number };
    const ok = await acc.reloadFreight(expressId);
    return { ok };
  });

  app.post("/api/acc/orders/calc-volume", async (req) => {
    const { length, width, height, divisor } = req.body as { length: number; width: number; height: number; divisor?: number };
    const volume = await acc.calculateVolume(length, width, height, divisor);
    return { volume };
  });

  app.post("/api/acc/orders/import", async (req) => {
    const { data, auditor = "admin" } = req.body as { data: any[]; auditor?: string };
    return acc.importOrders(data, auditor);
  });

  // -- Shipment business operations --
  app.post("/api/acc/shipments/verify", async (req) => {
    const { id } = req.body as { id: number };
    return acc.verifyShipment(id);
  });

  app.post("/api/acc/shipments/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditShipment(id, auditor);
  });

  app.post("/api/acc/shipments/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoShipment(id);
  });

  app.post("/api/acc/shipments/change-channel", async (req) => {
    const { id, channelAccountId } = req.body as { id: number; channelAccountId: number };
    const ok = await acc.changeShipmentChannel(id, channelAccountId);
    return { ok };
  });

  app.get("/api/acc/shipments/:id/items", async (req) => {
    const { id } = req.params as { id: string };
    return acc.getShipmentItems(Number(id));
  });

  app.post("/api/acc/shipments/:id/items", async (req) => {
    const { id } = req.params as { id: string };
    const { expressId } = req.body as { expressId: number };
    const ok = await acc.addShipmentItem(Number(id), expressId);
    return { ok };
  });

  app.delete("/api/acc/shipments/:sid/items/:eid", async (req) => {
    const { sid, eid } = req.params as { sid: string; eid: string };
    const ok = await acc.removeShipmentItem(Number(sid), Number(eid));
    return { ok };
  });

  // -- Charge business operations --
  app.post("/api/acc/charges/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditCharge(id, auditor);
  });

  app.post("/api/acc/charges/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoCharge(id);
  });

  app.post("/api/acc/charges/batch-audit", async (req) => {
    const { ids, auditor = "admin" } = req.body as { ids: number[]; auditor?: string };
    return acc.batchAuditCharges(ids, auditor);
  });

  app.get("/api/acc/charges/unpaid", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getUnpaidCharges(qn(q.customerId));
  });

  app.post("/api/acc/charges/import", async (req) => {
    const { data, auditor = "admin" } = req.body as { data: any[]; auditor?: string };
    return acc.importCharges(data, auditor);
  });

  // -- Cost business operations --
  app.post("/api/acc/costs/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditCost(id, auditor);
  });

  app.post("/api/acc/costs/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoCost(id);
  });

  app.post("/api/acc/costs/batch-audit", async (req) => {
    const { ids, auditor = "admin" } = req.body as { ids: number[]; auditor?: string };
    return acc.batchAuditCosts(ids, auditor);
  });

  app.post("/api/acc/costs/import", async (req) => {
    const { data, mappings, auditor = "admin" } = req.body as { data: any[]; mappings: Record<string, string>; auditor?: string };
    return acc.importCosts(data, mappings, auditor);
  });

  // -- Bill business operations --
  app.post("/api/acc/bills/generate", async (req) => {
    const { customerId, dateFrom, dateTo, auditor = "admin" } = req.body as { customerId: number; dateFrom: string; dateTo: string; auditor?: string };
    return acc.generateBill(customerId, dateFrom, dateTo, auditor);
  });

  app.post("/api/acc/bills/reload", async (req) => {
    const { id } = req.body as { id: number };
    return acc.reloadBill(id);
  });

  app.post("/api/acc/bills/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditBill(id, auditor);
  });

  app.post("/api/acc/bills/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoBill(id);
  });

  app.get("/api/acc/bills/:id/items", async (req) => {
    const { id } = req.params as { id: string };
    return acc.getBillItems(Number(id));
  });

  // -- Payment (Received) business operations --
  app.post("/api/acc/receiveds/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditReceived(id, auditor);
  });

  app.post("/api/acc/receiveds/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoReceived(id);
  });

  app.post("/api/acc/receiveds/quick", async (req) => {
    const { customerId, amount, bankId, auditor = "admin" } = req.body as { customerId: number; amount: number; bankId: number; auditor?: string };
    return acc.quickPayment(customerId, amount, bankId, auditor);
  });

  // -- Supplier Payment (Pay) business operations --
  app.post("/api/acc/payments/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditPay(id, auditor);
  });

  app.post("/api/acc/payments/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoPay(id);
  });

  // -- Profit operations --
  app.get("/api/acc/profits/overdue", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getOverdueCosts(qn(q.days) ?? 30);
  });

  app.post("/api/acc/profits/complete", async (req) => {
    const { ids } = req.body as { ids: number[] };
    const count = await acc.markCostComplete(ids);
    return { ok: true, completed: count };
  });

  app.get("/api/acc/profits/summary", async (req) => {
    const q = req.query as Record<string, string>;
    const now = new Date();
    const dateFrom = q.dateFrom ?? new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
    const dateTo = q.dateTo ?? now.toISOString().slice(0, 10);
    const groupBy = (q.groupBy as 'customer' | 'product' | 'branch' | 'employee') ?? 'customer';
    return acc.getProfitSummary(dateFrom, dateTo, groupBy);
  });

  // -- Commission operations --
  app.post("/api/acc/commissions/calculate", async (req) => {
    const { month } = req.body as { month: string };
    return acc.calculateCommission(month);
  });

  app.post("/api/acc/commissions/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditCommission(id, auditor);
  });
  app.post("/api/acc/commissions/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoCommission(id);
  });

  // -- Stowage operations --
  app.post("/api/acc/stowages/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditStowage(id, auditor);
  });

  app.post("/api/acc/stowages/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoStowage(id);
  });

  app.post("/api/acc/stowages/status", async (req) => {
    const { id, status } = req.body as { id: number; status: number };
    const ok = await acc.updateStowageStatus(id, status);
    return { ok };
  });

  app.post("/api/acc/stowages/sync", async (req) => {
    const { id } = req.body as { id: number };
    return acc.syncStowage(id);
  });

  app.get("/api/acc/stowages/:id/packages", async (req) => {
    const { id } = req.params as { id: string };
    return acc.getStowagePackages(Number(id));
  });

  // -- Transit operations --
  app.post("/api/acc/transits/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditTransit(id, auditor);
  });

  app.post("/api/acc/transits/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoTransit(id);
  });

  // -- Expense operations --
  app.post("/api/acc/expenses/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditExpense(id, auditor);
  });

  app.post("/api/acc/expenses/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoExpense(id);
  });

  // -- Transfer operations --
  app.post("/api/acc/transfers/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditTransfer(id, auditor);
  });

  app.post("/api/acc/transfers/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoTransfer(id);
  });

  // -- Fine operations --
  app.post("/api/acc/customer-fines/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditFine("Customer_Fine", id, auditor);
  });

  app.post("/api/acc/customer-fines/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoFine("Customer_Fine", id);
  });

  app.post("/api/acc/supplier-fines/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditFine("Supplier_Fine", id, auditor);
  });

  app.post("/api/acc/supplier-fines/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoFine("Supplier_Fine", id);
  });

  // -- Adjust operations --
  app.post("/api/acc/customer-adjusts/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditAdjust("Customer_Adjust", id, auditor);
  });

  app.post("/api/acc/customer-adjusts/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoAdjust("Customer_Adjust", id);
  });

  app.post("/api/acc/supplier-adjusts/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditAdjust("Supplier_Adjust", id, auditor);
  });

  app.post("/api/acc/supplier-adjusts/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoAdjust("Supplier_Adjust", id);
  });

  // -- Rebate operations --
  app.post("/api/acc/customer-rebates/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditRebate("Customer_Rebate", id, auditor);
  });

  app.post("/api/acc/customer-rebates/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoRebate("Customer_Rebate", id);
  });

  app.post("/api/acc/supplier-rebates/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditRebate("Supplier_Rebate", id, auditor);
  });

  app.post("/api/acc/supplier-rebates/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoRebate("Supplier_Rebate", id);
  });

  // -- Dividend operations --
  app.post("/api/acc/dividends/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditDividend(id, auditor);
  });

  app.post("/api/acc/dividends/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoDividend(id);
  });

  // -- Borrowing operations --
  app.post("/api/acc/borrowings/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditBorrowing(id, auditor);
  });
  app.post("/api/acc/borrowings/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoBorrowing(id);
  });

  // -- Wage operations --
  app.post("/api/acc/wages/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditWage(id, auditor);
  });
  app.post("/api/acc/wages/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoWage(id);
  });

  // -- Assets operations --
  app.post("/api/acc/assets/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditAssets(id, auditor);
  });
  app.post("/api/acc/assets/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoAssets(id);
  });

  // -- Fund operations --
  app.post("/api/acc/funds/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditFund(id, auditor);
  });
  app.post("/api/acc/funds/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoFund(id);
  });

  // -- Social operations --
  app.post("/api/acc/socials/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditSocial(id, auditor);
  });
  app.post("/api/acc/socials/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoSocial(id);
  });

  // -- Reparation operations --
  app.post("/api/acc/reparations/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditReparation(id, auditor);
  });
  app.post("/api/acc/reparations/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoReparation(id);
  });

  // -- Returns (Back) operations --
  app.post("/api/acc/returns/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditReturn(id, auditor);
  });
  app.post("/api/acc/returns/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoReturn(id);
  });

  // -- Customer Refund operations --
  app.post("/api/acc/customer-refunds/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditCRefund(id, auditor);
  });
  app.post("/api/acc/customer-refunds/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoCRefund(id);
  });

  // -- Supplier Refund operations --
  app.post("/api/acc/supplier-refunds/audit-biz", async (req) => {
    const { id, auditor = "admin" } = req.body as { id: number; auditor?: string };
    return acc.auditSRefund(id, auditor);
  });
  app.post("/api/acc/supplier-refunds/undo-biz", async (req) => {
    const { id } = req.body as { id: number };
    return acc.undoSRefund(id);
  });

  // -- Dispatch status --
  app.post("/api/acc/dispatches/status", async (req) => {
    const { id, status } = req.body as { id: number; status: number };
    const ok = await acc.updateDispatchStatus(id, status);
    return { ok };
  });

  // -- Express tracking --
  app.get("/api/acc/express/:id/process", async (req) => {
    const { id } = req.params as { id: string };
    return acc.getExpressProcess(Number(id));
  });

  app.post("/api/acc/express/:id/process", async (req) => {
    const { id } = req.params as { id: string };
    const { trackItemId, remark, operator = "admin" } = req.body as { trackItemId: number; remark: string; operator?: string };
    const ok = await acc.addTrackRecord(Number(id), trackItemId, remark, operator);
    return { ok };
  });

  app.post("/api/acc/express/:id/trackno", async (req) => {
    const { id } = req.params as { id: string };
    const { trackNo } = req.body as { trackNo: string };
    const ok = await acc.updateTrackNo(Number(id), trackNo);
    return { ok };
  });

  app.get("/api/acc/express/:id/tracknos", async (req) => {
    const { id } = req.params as { id: string };
    return acc.getExpressTrackNos(Number(id));
  });

  // -- Online Cancel / Void --
  app.get("/api/acc/void-orders", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.listVoidOrders({ keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo), page: Number(q.page) || 1, pageSize: Number(q.pageSize) || 50 });
  });
  app.post("/api/acc/void-orders/void", async (req) => {
    const { expressId, operator = "admin" } = req.body as { expressId: number; operator?: string };
    return acc.voidOrder(expressId, operator);
  });
  app.post("/api/acc/void-orders/recover", async (req) => {
    const { expressId, operator = "admin" } = req.body as { expressId: number; operator?: string };
    return acc.recoverVoidedOrder(expressId, operator);
  });

  // -- Online Order Creation --
  app.post("/api/acc/online/create", async (req) => {
    const { data, operator = "admin" } = req.body as { data: any; operator?: string };
    return acc.createOnlineOrder(data, operator);
  });

  // -- Express Batch Operations --
  app.post("/api/acc/express-batch/weights", async (req) => {
    const { updates, operator = "admin" } = req.body as { updates: Array<{ id: number; weight: number }>; operator?: string };
    return acc.batchUpdateWeights(updates, operator);
  });
  app.post("/api/acc/express-batch/track-nos", async (req) => {
    const { updates, operator = "admin" } = req.body as { updates: Array<{ id: number; trackNo: string }>; operator?: string };
    return acc.batchUpdateTrackNos(updates, operator);
  });
  app.post("/api/acc/express-batch/customers", async (req) => {
    const { ids, customerId, operator = "admin" } = req.body as { ids: number[]; customerId: number; operator?: string };
    return acc.batchUpdateCustomer(ids, customerId, operator);
  });
  app.post("/api/acc/express-batch/remarks", async (req) => {
    const { ids, remark, operator = "admin" } = req.body as { ids: number[]; remark: string; operator?: string };
    return acc.batchUpdateRemarks(ids, remark, operator);
  });

  // -- Reports --
  app.get("/api/acc/reports/product", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getProductReport(q.dateFrom ?? '', q.dateTo ?? '');
  });
  app.get("/api/acc/reports/monthly", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getMonthlyReport(Number(q.year) || new Date().getFullYear());
  });
  app.get("/api/acc/reports/country", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getCountryReport(q.dateFrom ?? '', q.dateTo ?? '', Number(q.limit) || 20);
  });
  app.get("/api/acc/reports/customer", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getCustomerReport(q.dateFrom ?? '', q.dateTo ?? '', Number(q.limit) || 50);
  });
  app.get("/api/acc/reports/employee", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.getEmployeeReport(q.dateFrom ?? '', q.dateTo ?? '');
  });

  // -- Warehouse / Mobile --
  app.post("/api/acc/warehouse/checkin", async (req) => {
    const { no, operator = "admin" } = req.body as { no: string; operator?: string };
    return acc.warehouseCheckin(no, operator);
  });
  app.post("/api/acc/warehouse/checkout", async (req) => {
    const { no, shipmentId, operator = "admin" } = req.body as { no: string; shipmentId: number; operator?: string };
    return acc.warehouseCheckout(no, shipmentId, operator);
  });
  app.get("/api/acc/warehouse/inventory", async (_req) => {
    return acc.warehouseInventoryCheck('admin');
  });
  app.post("/api/acc/warehouse/scan-check", async (req) => {
    const { no, operator = "admin" } = req.body as { no: string; operator?: string };
    return acc.warehouseScanCheck(no, operator);
  });
  app.post("/api/acc/warehouse/reset-check", async (_req) => {
    return acc.warehouseResetCheck();
  });

  // -- Freight Calculation --
  app.post("/api/acc/freight/calculate", async (req) => {
    const params = req.body as any;
    return acc.calculateFreightFull(params);
  });

  // -- Label Management --
  app.get("/api/acc/labels/:expressId", async (req) => {
    const { expressId } = req.params as { expressId: string };
    return acc.listLabels(Number(expressId));
  });
  app.get("/api/acc/labels/:expressId/file", async (req) => {
    const { expressId } = req.params as { expressId: string };
    const q = req.query as Record<string, string>;
    return acc.getLabelFile(Number(expressId), q.type ?? 'PDF');
  });
  app.post("/api/acc/labels/:expressId/request", async (req) => {
    const { expressId } = req.params as { expressId: string };
    const { operator = "admin" } = req.body as { operator?: string };
    return acc.requestLabel(Number(expressId), operator);
  });

  // -- Tracking --
  app.get("/api/acc/tracking/:expressId", async (req) => {
    const { expressId } = req.params as { expressId: string };
    return acc.getTrackingInfo(Number(expressId));
  });
  app.post("/api/acc/tracking/query", async (req) => {
    const { ids, operator = "admin" } = req.body as { ids: number[]; operator?: string };
    return acc.queryTracking(ids, operator);
  });
  app.post("/api/acc/tracking/:expressId/status", async (req) => {
    const { expressId } = req.params as { expressId: string };
    const { delivery, current, currentTime } = req.body as { delivery: number; current: string; currentTime: string };
    return acc.updateTrackingStatus(Number(expressId), delivery, current, currentTime);
  });
  app.post("/api/acc/tracking/:expressId/event", async (req) => {
    const { expressId } = req.params as { expressId: string };
    const { trackNo, activity, location, time } = req.body as { trackNo: string; activity: number; location: number; time: string };
    return acc.addTrackingEvent(Number(expressId), trackNo, activity, location, time);
  });

  // -- Customer Online API --
  app.post("/api/acc/customer-api/auth", async (req) => {
    const { userId, sign, timestamp } = req.body as { userId: string; sign: string; timestamp: string };
    return acc.apiAuthenticate(userId, sign, timestamp);
  });
  app.get("/api/acc/customer-api/balance", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.apiGetBalance(Number(q.customerId));
  });
  app.get("/api/acc/customer-api/products", async (req) => {
    const q = req.query as Record<string, string>;
    return acc.apiGetProducts(Number(q.customerId));
  });
  app.post("/api/acc/customer-api/query", async (req) => {
    const { customerId, no } = req.body as { customerId: number; no: string };
    return acc.apiQueryOrder(customerId, no);
  });
  app.post("/api/acc/customer-api/price", async (req) => {
    const params = req.body as any;
    return acc.apiGetPrice(params);
  });
  app.post("/api/acc/customer-api/order", async (req) => {
    const { customerId, data } = req.body as { customerId: number; data: any };
    return acc.createOnlineOrder({ ...data, customer: customerId }, 'API');
  });

  // -- Export --
  app.get("/api/acc/export/:module", async (req) => {
    const { module } = req.params as { module: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    const q = req.query as Record<string, string>;
    return acc.exportModuleData(table, {
      keyword: qp(q.keyword), dateFrom: qp(q.dateFrom), dateTo: qp(q.dateTo),
    });
  });

  // ══════════ Generic CRUD ══════════

  const tableMap: Record<string, string> = {
    orders: "Express", returns: "Back", shipments: "Shipment", stowages: "Stowage", "void-orders": "Online_Void",
    collects: "Express_Orders", detains: "Detain", asks: "Ask", reparations: "Reparation",
    packages: "Online_package", transits: "Transit", ports: "Stowage_Port", warehouses: "Online_Warehouse",
    charges: "Express_Charge", costs: "Express_Cost", bills: "Customer_Bill",
    payments: "Pay", receiveds: "Received",
    fees: "Fee", "fee-types": "Fee_Type",
    "customer-fines": "Customer_Fine", "supplier-fines": "Supplier_Fine",
    "customer-adjusts": "Customer_Adjust", "supplier-adjusts": "Supplier_Adjust",
    "customer-refunds": "Received", "supplier-refunds": "Pay",
    "customer-rebates": "Customer_Rebate", "supplier-rebates": "Supplier_Rebate",
    expenses: "Expenses", banks: "Bank", transfers: "Transfer",
    dividends: "Dividend", borrowings: "Borrowing", currencies: "Currency",
    customers: "Customer", "customer-groups": "Customer_Group",
    suppliers: "Supplier", channels: "Channel", "channel-accounts": "Channel_Account",
    products: "Product", "product-items": "Product_Item",
    employees: "Employee", wages: "Employee_wage", attendances: "Employee_Attence",
    branches: "Branch", departments: "Department",
    countries: "Country", zones: "Zone", postcodes: "Postcode",
    remotes: "Remote", fuels: "Fuel", hscodes: "HSCode",
    commissions: "Employee_Commission", profits: "Express",
    "quick-orders": "Express_Orders", dispatches: "Dispatch",
    "stowage-categories": "Stowage_Category", "stowage-steps": "Stowage_Step",
    forecasts: "Forecast_Package", tracks: "Track_Item",
    assets: "Assets", cycles: "Cycle", "received-sms": "Received_SMS",
    "expense-categories": "Expenses_Category", "fee-item-types": "Express_Fee_Type",
    potentials: "Potential", "sold-tos": "Online_SoldTo", notices: "Client_Notice",
    socials: "Social", "social-persons": "Social_Person",
    funds: "Fund", "fund-persons": "Fund_Person",
    "commission-rules": "Employee_Rule_Item",
    "bank-names": "Bank_Name", districts: "District",
    "logistics-interfaces": "Logistics", tasks: "Task", templates: "Template",
  };

  app.get("/api/acc/:module/:id/raw", async (req) => {
    const { module, id } = req.params as { module: string; id: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    return acc.getRecord(table, Number(id));
  });

  app.post("/api/acc/:module", async (req) => {
    const { module } = req.params as { module: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    const body = req.body as Record<string, any>;
    const id = await acc.insertRecord(table, body);
    return { id, ok: id > 0 };
  });

  app.put("/api/acc/:module/:id", async (req) => {
    const { module, id } = req.params as { module: string; id: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    const body = req.body as Record<string, any>;
    const ok = await acc.updateRecord(table, Number(id), body);
    return { ok };
  });

  app.delete("/api/acc/:module/:id", async (req) => {
    const { module, id } = req.params as { module: string; id: string };
    const table = tableMap[module];
    if (!table) return { error: "unknown module" };
    const ok = await acc.deleteRecord(table, Number(id));
    return { ok };
  });
}
