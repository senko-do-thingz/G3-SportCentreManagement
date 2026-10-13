import { useCallback, useEffect, useRef, useState } from 'react'
import { paymentsApi, type Payment } from '../../api/paymentsApi'
import { isPollingStatus, POLL_INTERVAL_MS } from './paymentsModel'

export interface PaymentPolling {
  /** Last payment the backend returned, null until the first answer. */
  payment: Payment | null
  /** Error of the first load; the page shows an error state with Retry. */
  loadError: unknown
  /** Error of a later check; the last payment stays on screen and polling goes on. */
  refreshError: unknown
  /** True while a check started with refresh() is running. */
  refreshing: boolean
  /** Time of the last successful answer. */
  checkedAt: Date | null
  /** True while the next automatic check is scheduled. */
  polling: boolean
  /** Asks the backend now and restarts the 15 second timer. */
  refresh: () => void
}

interface PollState {
  payment: Payment | null
  loadError: unknown
  refreshError: unknown
  refreshing: boolean
  checkedAt: Date | null
  polling: boolean
}

const INITIAL: PollState = {
  payment: null,
  loadError: null,
  refreshError: null,
  refreshing: false,
  checkedAt: null,
  polling: false,
}

/**
 * Loads one payment and checks it again every intervalMs while it is PENDING (Figma F3-02). The
 * next check is scheduled only after the previous answer, so two checks never overlap; polling
 * stops for good on SUCCESS, FAILED or REFUNDED. A failed check keeps the last payment and tries
 * again at the next interval; a failed first load stops until refresh() is called.
 *
 * Mount the screen with key={paymentId} so a new payment id starts from a clean state.
 */
export function usePaymentPolling(paymentId: number, intervalMs = POLL_INTERVAL_MS): PaymentPolling {
  const [state, setState] = useState<PollState>(INITIAL)
  const [cycle, setCycle] = useState(0)
  const hasPayment = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined

    const schedule = () => {
      timer = setTimeout(check, intervalMs)
    }

    function check() {
      paymentsApi.payment(paymentId, { signal: controller.signal }).then(
        (payment) => {
          if (controller.signal.aborted) {
            return
          }
          hasPayment.current = true
          const keepPolling = isPollingStatus(payment.status)
          setState({
            payment,
            loadError: null,
            refreshError: null,
            refreshing: false,
            checkedAt: new Date(),
            polling: keepPolling,
          })
          if (keepPolling) {
            schedule()
          }
        },
        (error: unknown) => {
          if (controller.signal.aborted) {
            return
          }
          if (hasPayment.current) {
            setState((previous) => ({ ...previous, refreshError: error, refreshing: false, polling: true }))
            schedule()
          } else {
            setState({ ...INITIAL, loadError: error })
          }
        },
      )
    }

    check()
    return () => {
      controller.abort()
      if (timer !== undefined) {
        clearTimeout(timer)
      }
    }
  }, [paymentId, intervalMs, cycle])

  const refresh = useCallback(() => {
    setState((previous) => (hasPayment.current ? { ...previous, refreshing: true } : { ...INITIAL }))
    setCycle((current) => current + 1)
  }, [])

  return { ...state, refresh }
}
