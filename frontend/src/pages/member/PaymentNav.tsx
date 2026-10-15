import { NavLink } from 'react-router-dom'
import { PAYMENT_PATHS } from './paymentsModel'

export interface PaymentNavProps {
  /** Payment the summary and status links point to. */
  paymentId: number
}

/**
 * Sub navigation of the payment screens (Figma F3-01, F3-02, F3-03): Payment summary, Payment
 * status and My payments. Hidden when printing.
 */
export function PaymentNav({ paymentId }: PaymentNavProps) {
  const linkClass = ({ isActive }: { isActive: boolean }) => `payment-nav__link${isActive ? ' payment-nav__link--active' : ''}`
  return (
    <nav className="payment-nav no-print" aria-label="Payment pages">
      <NavLink to={PAYMENT_PATHS.summary(paymentId)} end className={linkClass}>
        Payment summary
      </NavLink>
      <NavLink to={PAYMENT_PATHS.status(paymentId)} end className={linkClass}>
        Payment status
      </NavLink>
      <NavLink to={PAYMENT_PATHS.list} end className={linkClass}>
        My payments
      </NavLink>
    </nav>
  )
}
