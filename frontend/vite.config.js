import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  // Every VITE_* value becomes public browser code. Reject unreviewed variables.
  const env = { ...loadEnv(mode, process.cwd(), ['VITE_', 'DEV_API_PROXY_TARGET']), ...process.env };
  const publicVariables = new Set(['VITE_API_BASE_URL', 'VITE_GOOGLE_OAUTH_CLIENT_ID']);
  const unexpected = Object.keys(env).filter((key) => key.startsWith('VITE_') && !publicVariables.has(key));
  if (unexpected.length) throw new Error(`Unapproved public environment variables: ${unexpected.join(', ')}`);
  if (env.VITE_GOOGLE_OAUTH_CLIENT_ID && !/^[a-zA-Z0-9-]+\.apps\.googleusercontent\.com$/.test(env.VITE_GOOGLE_OAUTH_CLIENT_ID.trim())) {
    throw new Error('VITE_GOOGLE_OAUTH_CLIENT_ID must be a public Google OAuth Client ID');
  }
  if (env.VITE_API_BASE_URL) {
    const url = new URL(env.VITE_API_BASE_URL);
    if (url.username || url.password || url.search || url.hash) throw new Error('VITE_API_BASE_URL must not contain credentials, query parameters or fragments');
    if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))
      throw new Error('VITE_API_BASE_URL requires HTTPS except on localhost');
  }
  if (env.DEV_API_PROXY_TARGET) {
    const url = new URL(env.DEV_API_PROXY_TARGET);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new Error('DEV_API_PROXY_TARGET must be an HTTP(S) URL without credentials, query parameters or fragments');
    }
  }
  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: '127.0.0.1',
      proxy: env.DEV_API_PROXY_TARGET ? {
        '/api': { target: env.DEV_API_PROXY_TARGET, changeOrigin: true },
        '/uploads': { target: env.DEV_API_PROXY_TARGET, changeOrigin: true },
        '/actuator': { target: env.DEV_API_PROXY_TARGET, changeOrigin: true },
        '/ws': { target: env.DEV_API_PROXY_TARGET, ws: true, changeOrigin: true },
      } : undefined,
    },
  };
})
