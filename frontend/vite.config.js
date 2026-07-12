import { defineConfig } from 'vite';

// Frontend-ul apelează /api relativ (vezi src/config/api.js). În producție proxy-ul îl face nginx;
// în dev îl face serverul Vite, către backend-ul din Docker (127.0.0.1:8090 → containerul pe 8080).
export default defineConfig({
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_BACKEND || 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
});
