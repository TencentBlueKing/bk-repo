import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue2'
import vueJsx from '@vitejs/plugin-vue2-jsx'
import { createHtmlPlugin } from 'vite-plugin-html'
import monacoEditorPlugin from 'vite-plugin-monaco-editor'
import path from 'path'
import { fileURLToPath } from 'url'
import fs from 'fs'

// 在 ESM 模块中获取 __dirname
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export default defineConfig(({ mode, command }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  const envDist = process.env.dist || 'frontend'

  // 从环境变量中获取 VITE_APP_BASE_DIR，如果没有则使用默认值 'devops-op'
  const baseDir = env.VITE_APP_BASE_DIR || '/'

  // 使用相对路径，避免 monaco-editor 插件路径拼接错误
  const dist = `../../${envDist}/${baseDir}`

  // 判断是否为生产环境打包
  const isProduction = process.env.ENV !== 'development'

  return {
    base: baseDir ? `/${baseDir}/` : '/',
    root: process.cwd(),
    publicDir: 'public',
    build: {
      outDir: dist,
      assetsDir: 'static',
      sourcemap: false,
      chunkSizeWarningLimit: 600,
      rollupOptions: {
        output: {
          manualChunks: {
            'chunk-vendors': ['vue', 'vue-router', 'vuex'],
            'chunk-elementUI': ['element-ui'],
            'chunk-commons': ['axios', 'lodash', 'moment']
          },
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
        }
      }
    },
    plugins: [
      vue(),
      vueJsx({
        // 启用 Vue 2 函数式组件支持
        compositionAPI: true
      }),
      monacoEditorPlugin({
        languageWorkers: ['editorWorkerService', 'json', 'typescript', 'css', 'html']
      }),
      // 生产环境打包时，确保 ui 目录存在
      (() => {
        if (process.env.ENV !== 'development') {
          const uiDir = path.resolve(__dirname, '../../frontend/ui')
          if (!fs.existsSync(uiDir)) {
            fs.mkdirSync(uiDir, { recursive: true })
          }
        }
      })(),
      createHtmlPlugin({
        minify: true,
        inject: {
          data: {
            BASE_URL: '/'
          }
        },
        template: 'index.html',
        entry: '/src/main.js',
        // 生产环境打包时，将 HTML 输出到 ui 文件夹（相对于 outDir）
        // filename: isProduction ? '../ui/frontend_admin_index.html' : 'index.html'
      })
    ],
    resolve: {
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue'],
      alias: {
        '@': path.resolve(__dirname, 'src'),
        'path': 'path-browserify'
      }
    },
    server: {
      port: 8086,
      open: true,
      force: true,
      proxy: {
        // 用于处理本地开发时跨域问题
        '/web': {
          target: 'http://bkrepo.example.com', // 接口域名
          secure: false, // 如果是https接口，需要配置这个参数
          changeOrigin: true // 是否跨域
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
        }
      }
    }
  }
})
