import cors from "@fastify/cors";
import postgres from "@fastify/postgres";
import Fastify from "fastify";
import { healthRoutes } from "./routes/health.js";
import { unifiedOrderRoutes } from "./routes/unified-orders.js";
import { unifiedFinanceRoutes } from "./routes/unified-finance.js";
import { accRoutes } from "./routes/acc.js";
import { systemRoutes } from "./routes/system.js";

const server = Fastify({ logger: true });
const port = Number(process.env.API_PORT ?? 8080);

await server.register(cors, { origin: true });
await server.register(postgres, {
  connectionString:
    process.env.DATABASE_URL ??
    "postgres://xqt:xqt_dev_password@localhost:15432/xqt_saas",
});

await server.register(healthRoutes);
await server.register(unifiedOrderRoutes);
await server.register(unifiedFinanceRoutes);
await server.register(accRoutes);
await server.register(systemRoutes);

server.listen({ port, host: "0.0.0.0" });
