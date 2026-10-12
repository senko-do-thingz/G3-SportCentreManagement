/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_SHOW_SAMPLE_ACCOUNT?: string
  readonly VITE_SAMPLE_ACCOUNT_EMAIL?: string
  readonly VITE_SAMPLE_ACCOUNT_PASSWORD?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
