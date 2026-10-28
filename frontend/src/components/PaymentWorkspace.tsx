import type { ReactNode } from 'react'
import '@fontsource/be-vietnam-pro/700.css'
import '@fontsource/be-vietnam-pro/800.css'
import '@fontsource/be-vietnam-pro/800-italic.css'
import '../styles/home.css'
import '../styles/paymentWorkspace.css'

/** Reuse the merged Home palette without changing the frozen shared token contract. */
export function PaymentWorkspace({ children }: { children: ReactNode }) {
  return <div className="home-page payment-workspace page">{children}</div>
}
