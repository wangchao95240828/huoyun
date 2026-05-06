import type { FastifyInstance } from "fastify";
import { createAccAdapter } from "../adapters/acc-adapter.js";
import { createXqtAdapter } from "../adapters/xqt-adapter.js";

interface UnifiedOrder {
  id: string;
  orderNo: string;
  customer: string;
  channel: string;
  country: string;
  weight: number;
  pieces: number;
  sellCharge: number;
  costCharge: number;
  profit: number;
  branch: string;
  seller: string;
  status: string;
  createdAt: string;
  source: "ACC" | "XQT";
  businessType: string;
}

export async function unifiedOrderRoutes(app: FastifyInstance) {
  app.get<{
    Querystring: {
      source?: "ACC" | "XQT";
      dateFrom?: string;
      dateTo?: string;
      branch?: string;
      page?: string;
      pageSize?: string;
    };
  }>("/api/orders", async (request) => {
    const { source, dateFrom, dateTo, branch, page, pageSize } = request.query;
    const results: UnifiedOrder[] = [];
    let accTotal = 0, xqtTotal = 0;

    const acc = createAccAdapter();
    const xqt = createXqtAdapter();

    try {
      const fetches: Promise<void>[] = [];

      if (!source || source === "ACC") {
        fetches.push(
          acc.listOrders({
            dateFrom,
            dateTo,
            branch,
            page: Number(page ?? 1),
            pageSize: Number(pageSize ?? 30),
          }).then(({ data, total }) => {
            accTotal = total;
            for (const o of data) {
              results.push({
                id: `acc-${o.id}`,
                orderNo: o.orderNo,
                customer: o.customerName,
                channel: o.channel,
                country: o.country,
                weight: o.weight,
                pieces: o.piece,
                sellCharge: o.sellCharge,
                costCharge: o.costCharge,
                profit: o.sellCharge - o.costCharge,
                branch: o.branch,
                seller: o.sellerName,
                status: o.status,
                createdAt: o.addTime,
                source: "ACC",
                businessType: "委托运输",
              });
            }
          })
        );
      }

      if (!source || source === "XQT") {
        fetches.push(
          xqt.listShipments({
            page: Number(page ?? 1),
            dateFrom,
            dateTo,
          }).then(({ data, total }) => {
            xqtTotal = total;
            for (const s of data) {
              results.push({
                id: `xqt-${s.id}`,
                orderNo: s.shipmentNumber,
                customer: s.customer,
                channel: s.service,
                country: s.country,
                weight: s.chargeableWeight,
                pieces: s.parcelCount,
                sellCharge: s.sellCharge,
                costCharge: s.costCharge,
                profit: s.profit,
                branch: "",
                seller: s.seller,
                status: s.status,
                createdAt: s.pickingTime
                  ? new Date(s.pickingTime * 1000).toISOString()
                  : "",
                source: "XQT",
                businessType: "集货入仓",
              });
            }
          })
        );
      }

      await Promise.all(fetches);
    } finally {
      await acc.close();
    }

    results.sort((a, b) => (b.createdAt > a.createdAt ? 1 : -1));

    return {
      data: results,
      total: { acc: accTotal, xqt: xqtTotal, combined: accTotal + xqtTotal },
      page: Number(page ?? 1),
      pageSize: Number(pageSize ?? 30),
    };
  });
}
