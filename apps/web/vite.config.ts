import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, "index.html"),
        customerPortal: resolve(__dirname, "customer-portal.html"),
      },
    },
  },
  server: {
    port: Number(process.env.WEB_PORT ?? 5174),
    proxy: {
      "/api": {
        target: `http://localhost:${process.env.SPRING_API_PORT ?? 18103}`,
        changeOrigin: true,
      },
      "/health": {
        target: `http://localhost:${process.env.SPRING_API_PORT ?? 18103}`,
        changeOrigin: true,
      },
    },
  },
});