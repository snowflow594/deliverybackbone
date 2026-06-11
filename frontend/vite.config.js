import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El proxy evita CORS en desarrollo: el navegador habla solo con :5173.
// El WebSocket STOMP se conecta directo a :8080 (ver useInventoryStream.js).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
