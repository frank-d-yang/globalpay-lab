import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      proxy: {
        '/api/v1/payments': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
          headers: {
            'X-API-Key': env.SANDBOX_MERCHANT_API_KEY ?? '',
          },
        },
      },
    },
  }
})

