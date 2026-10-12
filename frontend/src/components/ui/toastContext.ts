import { createContext, useContext } from 'react'

export type ToastTone = 'success' | 'error' | 'info' | 'warning'

export interface ToastInput {
  tone?: ToastTone
  title: string
  message?: string
  /** Milliseconds before the toast closes by itself. Use 0 to keep it open. */
  duration?: number
}

export interface ToastApi {
  show(toast: ToastInput): number
  success(title: string, message?: string): number
  error(title: string, message?: string): number
  info(title: string, message?: string): number
  dismiss(id: number): void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used inside ToastProvider')
  }
  return context
}
