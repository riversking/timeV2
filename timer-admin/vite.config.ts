import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8006", // 后端地址
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""), // 去掉前缀
      },
      "/websocket": {
        target: "ws://localhost:8006", // 后端真实 WebSocket 服务地址
        ws: true, // 必须设置为 true
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/websocket/, ""), // 剥离代理标识
      },
    },
  },
});
