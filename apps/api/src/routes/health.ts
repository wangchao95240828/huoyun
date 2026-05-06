import type { FastifyInstance } from "fastify";
import { createAccAdapter } from "../adapters/acc-adapter.js";
import { createXqtAdapter } from "../adapters/xqt-adapter.js";

export async function healthRoutes(app: FastifyInstance) {
  app.get("/health", async () => {
    const acc = createAccAdapter();
    const xqt = createXqtAdapter();

    const [accStatus, xqtStatus] = await Promise.all([
      acc.testConnection(),
      xqt.testConnection(),
    ]);
    await acc.close();

    return {
      ok: true,
      service: "xqt-portal",
      upstreams: {
        acc: { available: acc.available, connected: accStatus.ok, error: accStatus.error },
        xqt: { available: xqt.available, connected: xqtStatus.ok, error: xqtStatus.error },
        postgres: { available: true, connected: true },
      },
    };
  });
}
