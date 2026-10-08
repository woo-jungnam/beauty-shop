import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  // Every VITE_* value becomes public browser code. Reject unreviewed variables.
  const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env };
  const unexpected = Object.keys(env).filter((key) => key.startsWith('VITE_') && key !== 'VITE_API_BASE_URL');
  if (unexpected.length) throw new Error(`Unapproved public environment variables: ${unexpected.join(', ')}`);
  if (env.VITE_API_BASE_URL) {
    const url = new URL(env.VITE_API_BASE_URL);
    if (url.username || url.password || url.search || url.hash) throw new Error('VITE_API_BASE_URL must not contain credentials, query parameters or fragments');
    if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))
      throw new Error('VITE_API_BASE_URL requires HTTPS except on localhost');
  }
  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: '127.0.0.1',
      proxy: {
        '/api': { target: 'http://localhost:8080', changeOrigin: true },
        '/uploads': { target: 'http://localhost:8080', changeOrigin: true },
        '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
        '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true },
      },
    },
  };
})
