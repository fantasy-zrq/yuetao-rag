import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    port: 5174,
    proxy: {
      "/yuetaoRag": {
        target: "http://localhost:9596",
        changeOrigin: true,
        secure: false
      }
    }
  }
});
