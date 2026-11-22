import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// relative paths: the site is served from the artifact's own files
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: {
    outDir: "dist", assetsInlineLimit: 0, chunkSizeWarningLimit: 900,
    // fixed names: a republish replaces the same files
    rollupOptions: { output: { entryFileNames: "assets/app.js", chunkFileNames: "assets/[name].js", assetFileNames: "assets/[name][extname]" } },
  },
});
