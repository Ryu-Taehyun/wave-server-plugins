// 개발 중에는 /api 를 백엔드(기본 8080)로 넘김: MCM_API=http://127.0.0.1:8080 npm run dev
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const api = process.env.MCM_API || "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      "/api": { target: api, changeOrigin: false, ws: true },
    },
  },
  build: { target: "es2022", sourcemap: false, chunkSizeWarningLimit: 800 },
});
