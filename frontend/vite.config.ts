import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8765',
        changeOrigin: false,
      },
    },
  },
  build: {
    // Production build emits straight into Spring Boot's static resources
    // so the fat JAR serves the UI without a second runtime.
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
