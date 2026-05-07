import type { FastifyInstance } from "fastify";

export async function healthRoutes(app: FastifyInstance) {
  app.get("/health", async () => {
    return {
      ok: true,
      service: "xqt-portal",
      upstreams: {
        acc: { available: false, connected: false, referenceOnly: true },
        xqt: { available: false, connected: false, referenceOnly: true },
        postgres: { available: true, connected: true },
      },
    };
  });
}
