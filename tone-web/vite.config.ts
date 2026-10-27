import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: true,
      port: Number(env.VITE_PORT) || 5173,
      open: false,
      // 开发期走代理：前端 /api 转到后端 8080，避免跨域与 cookie 问题
      proxy: {
        [env.VITE_API_PREFIX || '/api']: {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          // 后端 context-path 是 /，这里要把 /api 前缀去掉
          rewrite: (path: string) => path.replace(new RegExp('^' + (env.VITE_API_PREFIX || '/api')), '')
        },
        // 上传的文件由后端把本地目录静态映射成 /upload/**，
        // 开发期不打这条代理的话，文件预览会打到前端 dev server 上直接 404
        '/upload': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // 把三方依赖拆出来，业务代码改动时浏览器缓存不用整体失效
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            element: ['element-plus', '@element-plus/icons-vue']
          }
        }
      }
    }
  }
})
