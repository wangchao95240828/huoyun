import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: Number(process.env.WEB_PORT ?? 5173),
    proxy: {
      "/api/auth": {
        target: `http://localhost:${process.env.SPRING_API_PORT ?? 18103}`,
        changeOrigin: true,
      },
      "/api/admin": {
        target: `http://localhost:${process.env.SPRING_API_PORT ?? 18103}`,
        changeOrigin: true,
      },
      "/api/health": {
        target: `http://localhost:${process.env.SPRING_API_PORT ?? 18103}`,
        changeOrigin: true,
      },
      "/api": {
        target: `http://localhost:${process.env.API_PORT ?? 18080}`,
        changeOrigin: true,
      },
      "/health": {
        target: `http://localhost:${process.env.API_PORT ?? 18080}`,
        changeOrigin: true,
      },
    },
  },
});
