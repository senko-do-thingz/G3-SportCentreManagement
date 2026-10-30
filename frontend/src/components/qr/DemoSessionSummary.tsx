import { useEffect, useState } from 'react'
import type { DemoSession } from '../../api/demoQrApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'

/** Advisory elapsed time comes from a server-time snapshot and a monotonic browser clock. */
export function DemoSessionSummary({ session, onDeadline }: { session: DemoSession; onDeadline?: () => void }) {
  const [remaining, setRemaining] = useState<number | null>(null)
  useEffect(() => {
    if (session.state !== 'PENDING') return
    const start = performance.now()
    const lifetime = Math.max(0, Date.parse(session.expiresAt) - Date.parse(session.serverTime))
    let announced = false
    const tick = () => {
      const seconds = Math.max(0, Math.ceil((lifetime - (performance.now() - start)) / 1000))
      setRemaining(seconds)
      if (seconds === 0 && !announced) { announced = true; onDeadline?.() }
    }
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [session.state, session.expiresAt, session.serverTime, onDeadline])
  return <>
    <dl className="summary-list">
      <div><dt>Demo bank</dt><dd>{session.bankLabel}</dd></div>
      <div><dt>Demo reference</dt><dd>{session.reference}</dd></div>
      <div><dt>Demo amount snapshot</dt><dd>{formatPaymentMoney(session.amount)}</dd></div>
      <div><dt>Demo session state</dt><dd><span role="status" aria-live="polite" aria-atomic="true">{session.state}</span></dd></div>
      <div><dt>Expires at (your local time)</dt><dd>{new Date(session.expiresAt).toLocaleString('en-GB')}</dd></div>
    </dl>
    {session.state === 'PENDING' && <p>Time remaining: <span role="timer" aria-live="off">{remaining ?? '-'} seconds</span>. The server decides expiry.</p>}
    {session.state === 'AUTHORIZED_PENDING_SETTLEMENT' && <p className="notice notice--info">Confirmation intent received; payment not yet completed.</p>}
    {session.state === 'CANCELED' && <p className="notice notice--info">This demo session was canceled. The real payment remains unchanged.</p>}
    {session.state === 'EXPIRED' && <p className="notice notice--warning">This demo session expired. Start a new session on the desktop. The real payment remains unchanged.</p>}
  </>
}
