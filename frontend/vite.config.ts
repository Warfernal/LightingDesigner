import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import type { ServerOptions } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_BACKEND_PROXY_TARGET

  const server: ServerOptions = {}
  if (proxyTarget) {
    server.proxy = {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
      },
    }
  }

  return {
    plugins: [react()],
    server,
  }
})
