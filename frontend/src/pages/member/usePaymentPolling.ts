import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/errors'
import { paymentsApi, type Payment } from '../../api/paymentsApi'
import { isPollingStatus, POLL_INTERVAL_MS } from './paymentsModel'

export interface PaymentPolling {
  /** Last payment the backend returned, null until the first answer. */
  payment: Payment | null
  /** Error of the first load; the page shows an error state with Retry. */
  loadError: unknown
  /** Error of a later check; the last payment stays on screen. */
  refreshError: unknown
  /** True while a check started with refresh() is running. */
  refreshing: boolean
  /** Time of the last successful answer. */
  checkedAt: Date | null
  /** True while the next automatic check is scheduled. */
  polling: boolean
  /** Asks the backend now and restarts the 15 second timer; ignored while a check is running. */
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
 * Only a network error (status 0) or a server error (5xx) may go away by itself, so only those keep
 * the automatic checks going. A 4xx (403, 404 and so on) will answer the same every time, and any
 * other error (for example a malformed answer) is not retried either: polling stops and the member
 * can use Refresh.
 */
export function isRetryableError(error: unknown): boolean {
  return error instanceof ApiError && (error.isNetworkError || error.status >= 500)
}

/**
 * Loads one payment and checks it again every intervalMs while it is PENDING (Figma F3-02).
 * - At most one request runs at a time: the next check is scheduled only after the previous
 *   answer, and refresh() does nothing while a check is running.
 * - Polling stops for good on SUCCESS, FAILED or REFUNDED, and on an error that is not retryable.
 * - A retryable error keeps the last payment and tries again at the next interval.
 * - A failed first load stops until refresh() is called.
 * - Unmounting aborts the running request and clears the timer, so nothing is sent afterwards.
 *
 * Mount the screen with key={paymentId} so a new payment id starts from a clean state.
 */
export function usePaymentPolling(paymentId: number, intervalMs = POLL_INTERVAL_MS): PaymentPolling {
  const [state, setState] = useState<PollState>(INITIAL)
  const [cycle, setCycle] = useState(0)
  const hasPayment = useRef(false)
  const inFlight = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined

    const schedule = () => {
      timer = setTimeout(check, intervalMs)
    }

    function check() {
      inFlight.current = true
      paymentsApi.payment(paymentId, { signal: controller.signal }).then(
        (payment) => {
          if (controller.signal.aborted) {
            return
          }
          inFlight.current = false
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
          inFlight.current = false
          if (!hasPayment.current) {
            setState({ ...INITIAL, loadError: error })
            return
          }
          const retry = isRetryableError(error)
          setState((previous) => ({ ...previous, refreshError: error, refreshing: false, polling: retry }))
          if (retry) {
            schedule()
          }
        },
      )
    }

    check()
    return () => {
      controller.abort()
      inFlight.current = false
      if (timer !== undefined) {
        clearTimeout(timer)
      }
    }
  }, [paymentId, intervalMs, cycle])

  const refresh = useCallback(() => {
    if (inFlight.current) {
      return
    }
    setState((previous) => (hasPayment.current ? { ...previous, refreshing: true } : { ...INITIAL }))
    setCycle((current) => current + 1)
  }, [])

  return { ...state, refresh }
}
