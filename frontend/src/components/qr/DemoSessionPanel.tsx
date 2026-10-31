import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { demoQrApi, type CreatedDemoSession } from '../../api/demoQrApi'
import { copyText } from '../../utils/clipboard'
import { qrPaymentDemoEnabled } from '../../config/env'
import { Button } from '../ui'
import { DEMO_BANKS, type DemoBank } from './demoBanks'
import { DemoSessionSummary } from './DemoSessionSummary'
import './demoSession.css'

/** A key owned by the bank/payment context ensures old links disappear immediately. */
export function DemoSessionPanel({ paymentId, bank, amount }: { paymentId: number; bank: DemoBank; amount: number | string }) {
  if (!qrPaymentDemoEnabled()) return null
  return <DemoSessionPanelContent key={`${paymentId}:${bank}:${amount}`} paymentId={paymentId} bank={bank} amount={amount} />
}

function DemoSessionPanelContent({ paymentId, bank, amount }: { paymentId: number; bank: DemoBank; amount: number | string }) {
  const [capability, setCapability] = useState<boolean | null>(null)
  const [capabilityAttempt, setCapabilityAttempt] = useState(0)
  useEffect(() => {
    let active = true
    void demoQrApi.capability().then((enabled) => { if (active) setCapability(enabled) })
      .catch(() => { if (active) setCapability(false) })
    return () => { active = false }
  }, [capabilityAttempt])
  const [created, setCreated] = useState<CreatedDemoSession | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [deadline, setDeadline] = useState(false)
  const sending = useRef(false)
  const mounted = useRef(false)
  const sessionId = useRef<string | null>(null)
  const feedbackRequest = useRef(0)
  const qrTitle = useId()
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      feedbackRequest.current += 1
      // Best effort only. A disconnected browser cannot guarantee cancellation; server expiry still applies.
      if (sessionId.current) void demoQrApi.cancel(sessionId.current).catch(() => undefined)
    }
  }, [])
  const reachedDeadline = useCallback(() => setDeadline(true), [])
  const qr = useMemo(() => {
    if (!created || created.session.state !== 'PENDING' || deadline) return null
    try { return QRCode.create(created.confirmationUrl, { errorCorrectionLevel: 'M' }) }
    catch { return null }
  }, [created, deadline])

  async function operation(action: 'start' | 'refresh' | 'cancel') {
    if (sending.current) return
    sending.current = true
    feedbackRequest.current += 1
    setBusy(true); setError(''); setFeedback('')
    try {
      if (action === 'start') {
        const result = await demoQrApi.create(paymentId, DEMO_BANKS[bank].code)
        if (result.session.paymentId !== paymentId || result.session.bankCode !== DEMO_BANKS[bank].code
          || Number(result.session.amount) !== Number(amount)) {
          void demoQrApi.cancel(result.session.id).catch(() => undefined)
          throw new Error('Payment context changed; reload the payment before starting a new session')
        }
        if (!mounted.current) { void demoQrApi.cancel(result.session.id).catch(() => undefined); return }
        sessionId.current = result.session.id
        setCreated(result); setDeadline(false)
      } else if (created) {
        const session = action === 'refresh' ? await demoQrApi.get(created.session.id) : await demoQrApi.cancel(created.session.id)
        if (session.state !== 'PENDING') sessionId.current = null
        if (mounted.current) { setCreated({ ...created, session }); setDeadline(false) }
      }
    } catch {
      if (action !== 'start' && created) {
        try {
          const reconciled = await demoQrApi.get(created.session.id)
          if (reconciled.state !== 'PENDING') sessionId.current = null
          if (mounted.current && reconciled.state !== 'PENDING') {
            setCreated({ ...created, session: reconciled }); setDeadline(false); return
          }
        } catch {
          // Server reconciliation also failed; fall through to unknown-outcome alert.
        }
      }
      if (mounted.current) setError('Could not update the demo session. Check your connection and demo configuration, then refresh Simulation Status to verify the authoritative server state before retrying. Reload the payment if its details changed.')
    } finally {
      sending.current = false
      if (mounted.current) setBusy(false)
    }
  }
  async function copy() {
    if (!created || !qr || busy) return
    const current = ++feedbackRequest.current
    setFeedback(''); setError('')
    try {
      await copyText(created.confirmationUrl, { allowFallback: false })
      if (mounted.current && feedbackRequest.current === current) setFeedback('Time-limited demo link copied. Share only with the payment owner.')
    } catch {
      if (mounted.current && feedbackRequest.current === current) setError('Could not copy the demo link. Use Open simulation confirmation instead.')
    }
  }
  const size = qr ? qr.modules.size + 8 : 0
  const path = qr ? Array.from(qr.modules.data, (dark, index) => dark
    ? `M${index % qr.modules.size + 4} ${Math.floor(index / qr.modules.size) + 4}h1v1h-1z` : '').join('') : ''
  if (capability !== true) return <section className="demo-session-panel" aria-label="Simulated QR Payment">
    <h3>Simulated QR Payment</h3>
    <p aria-live="polite">{capability === null ? 'Checking simulation availability...' : 'Simulation is unavailable. The payment remains pending. Legacy links must be reissued when simulation becomes available.'}</p>
    {capability === false && <Button variant="secondary" onClick={() => { setCapability(null); setCapabilityAttempt((value) => value + 1) }}>Retry simulation availability</Button>}
  </section>
  return <section className="demo-session-panel" aria-label="Simulated confirmation session">
    <h3>Simulated QR Payment</h3>
    <p>The simulation session is a separate authenticated application link. It is not a bank transfer QR. Confirming as the authenticated payment owner finalizes this payment as Paid - Simulated and activates the purchased entitlement. No real bank transfer or cash movement occurs.</p>
    <p className="notice notice--warning">Simulated QR Payment - No real money is transferred.</p>
    <p>Changing the bank or leaving this page attempts to cancel an unconfirmed session. A disconnected session expires on the server.</p>
    {created && <DemoSessionSummary session={created.session} onDeadline={reachedDeadline} />}
    {deadline && created?.session.state === 'PENDING' && <p className="notice notice--warning">The displayed deadline has passed. Refresh Simulation Status to read the server result.</p>}
    {qr && <figure className="qr-demo-card__figure">
      <svg className="qr-demo-card__symbol" viewBox={`0 0 ${size} ${size}`} role="img" aria-labelledby={qrTitle} shapeRendering="crispEdges">
        <title id={qrTitle}>Scan to review simulation, {DEMO_BANKS[bank].label}, payment {paymentId}</title>
        <rect width={size} height={size} className="qr-demo-card__paper" />
        <path d={path} fill="currentColor" />
      </svg>
      <figcaption>Simulated Confirmation Link - time-limited simulation only</figcaption>
    </figure>}
    {created?.session.state === 'PENDING' && !deadline && !qr && <p role="alert">The demo link QR could not be generated. Cancel this session and retry.</p>}
    <div className="demo-session-actions">
      {(!created || (created.session.state !== 'PENDING' && created.session.state !== 'SETTLED') || (created.session.state === 'PENDING' && deadline)) && <Button disabled={busy} onClick={() => void operation('start')}>Start simulation session</Button>}
      {created && <Button variant="secondary" disabled={busy} onClick={() => void operation('refresh')}>Refresh Simulation Status</Button>}
      {created?.session.state === 'PENDING' && <Button variant="secondary" disabled={busy} onClick={() => void operation('cancel')}>Cancel Simulation Session</Button>}
      {created && qr && <>
        <a className="btn btn--secondary" href={created.confirmationUrl} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Open simulation confirmation</a>
        <Button variant="secondary" disabled={busy} onClick={() => void copy()}>Copy simulation session link</Button>
      </>}
    </div>
    {busy && <p aria-live="polite">Updating demo session...</p>}
    {feedback && <p role="status">{feedback}</p>}
    {error && <p className="notice notice--danger" role="alert">{error}</p>}
  </section>
}
