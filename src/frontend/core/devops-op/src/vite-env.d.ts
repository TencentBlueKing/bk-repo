/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_BASE_API: string
  readonly VITE_APP_BASE_DIR: string
  readonly VITE_APP_MODE_CONFIG: string
  readonly VITE_APP_RELEASE_MODE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
