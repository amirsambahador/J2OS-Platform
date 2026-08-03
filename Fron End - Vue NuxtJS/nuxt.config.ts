export default defineNuxtConfig({

  modules: [
    '@nuxt/eslint',
    '@nuxt/ui'
  ],

  devtools: {
    enabled: false
  },

  app: {
    head: {
      script: [
        { src: '/plugins/imask-7.6.1.min.js' },
        { src: '/plugins/jalaali-1.2.3.min.js' },
        { src: '/plugins/qrcode.min.js' },
        { src: '/plugins/purify.min.js' }
      ]
    }
  },

  css: ['~/assets/css/main.css'],

  colorMode: {
    preference: 'dark',
    fallback: 'dark'
  },

  ui: {
    fonts: false
  },
  runtimeConfig: {
    public: {
      API_BASE: 'http://localhost:8082'
    }
  },

  routeRules: {
    '/': { prerender: true }
  },

  compatibilityDate: '2025-01-15',
  vite: {
    esbuild: {
      sourcemap: false
    }
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
