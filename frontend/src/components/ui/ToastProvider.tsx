import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ToastContext, type ToastApi, type ToastInput, type ToastTone } from './toastContext'

interface ToastItem {
  id: number
  tone: ToastTone
  title: string
  message?: string
}

const DEFAULT_DURATION = 4000
const MAX_VISIBLE = 4

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
    setToasts((items) => items.filter((item) => item.id !== id))
  }, [])

  const show = useCallback(
    ({ tone = 'info', title, message, duration = DEFAULT_DURATION }: ToastInput) => {
      const id = nextId.current
      nextId.current += 1
      setToasts((items) => [...items, { id, tone, title, message }].slice(-MAX_VISIBLE))
      if (duration > 0) {
        timers.current.set(
          id,
          setTimeout(() => dismiss(id), duration),
        )
      }
      return id
    },
    [dismiss],
  )

  useEffect(() => {
    const activeTimers = timers.current
    return () => {
      activeTimers.forEach((timer) => clearTimeout(timer))
      activeTimers.clear()
    }
  }, [])

  const api = useMemo<ToastApi>(
    () => ({
      show,
      dismiss,
      success: (title, message) => show({ tone: 'success', title, message }),
      error: (title, message) => show({ tone: 'error', title, message, duration: 6000 }),
      info: (title, message) => show({ tone: 'info', title, message }),
    }),
    [show, dismiss],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-region" aria-live="polite" aria-relevant="additions">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast toast--${toast.tone}`}
            role={toast.tone === 'error' ? 'alert' : 'status'}
          >
            <span className="toast__bar" aria-hidden="true" />
            <div className="toast__content">
              <p className="toast__title">{toast.title}</p>
              {toast.message && <p className="toast__message">{toast.message}</p>}
            </div>
            <button
              type="button"
              className="toast__close"
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss notification"
            >
              <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true">
                <path d="M5 5l10 10M15 5L5 15" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
