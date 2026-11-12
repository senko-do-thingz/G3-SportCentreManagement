import { useEffect, useMemo, useState } from 'react'
import QRCode from 'qrcode'
import { demoQrApi, DEMO_TOKEN, type CreatedDemoSession, type DemoSession } from '../../api/demoQrApi'
import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney, PAYMENT_PATHS } from '../../pages/member/paymentsModel'
import { Button, Card } from '../ui'
import { DEMO_BANKS } from './demoBanks'
import './demoQr.css'

export function TokenQrPaymentCard({ payment, onSettled }: { payment: Payment; onSettled: () => void }) {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [created, setCreated] = useState<CreatedDemoSession | null>(null)
  const [tokenInput, setTokenInput] = useState('')
  const [result, setResult] = useState<DemoSession | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    let active = true
    void demoQrApi.capability().then((value) => { if (active) setEnabled(value) })
      .catch(() => { if (active) setEnabled(false) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const token = created ? new URL(created.confirmationUrl).hash.slice('#token='.length) : ''
  const qr = useMemo(() => {
    if (!token) return null
    try {
      const code = QRCode.create(token, { errorCorrectionLevel: 'M' })
      const size = code.modules.size + 8
      const path = Array.from(code.modules.data, (dark, index) => dark
        ? `M${index % code.modules.size + 4} ${Math.floor(index / code.modules.size) + 4}h1v1h-1z` : '').join('')
      return { size, path }
    } catch { return null }
  }, [token])
  const expired = !!created && now >= Date.parse(created.session.expiresAt)

  async function start() {
    const previousToken = token
    setCreated(null)
    setBusy(true); setError(''); setTokenInput(''); setResult(null)
    try {
      const next = await demoQrApi.create(payment.id, DEMO_BANKS.vietcombank.code)
      if (next.session.paymentId !== payment.id || Number(next.session.amount) !== Number(payment.amount)) {
        throw new Error('Payment details changed. Refresh the page and try again.')
      }
      const replacementToken = new URL(next.confirmationUrl).hash.slice('#token='.length)
      if (!DEMO_TOKEN.test(replacementToken) || replacementToken === previousToken) {
        throw new Error('A new verification token was not issued. Generate another QR token.')
      }
      setCreated(next)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not create a QR token.') }
    finally { setBusy(false) }
  }

  async function confirm() {
    const submitted = tokenInput.trim()
    if (!DEMO_TOKEN.test(submitted) || busy || expired) {
      setError('Enter the 43-character token shown by scanning the QR code before it expires.')
      return
    }
    setBusy(true); setError('')
    try {
      const confirmed = await demoQrApi.confirm(submitted)
      if (confirmed.paymentId !== payment.id || confirmed.state !== 'SETTLED' || confirmed.settlementType !== 'SIMULATED' || !confirmed.invoiceId) {
        throw new Error('Confirmation result did not match this payment. Check payment status before retrying.')
      }
      setResult(confirmed)
      onSettled()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Verification failed. Check the token and payment status.') }
    finally { setBusy(false) }
  }

  return <Card title="QR Payment - Demo" className="qr-demo-card">
    <p className="notice notice--warning">No real money is transferred. Your payment, entitlement, and demo receipt update when you verify the token.</p>
    <dl className="summary-list">
      <div><dt>Payment reference</dt><dd>{payment.code}</dd></div>
      <div><dt>Exact amount</dt><dd>{formatPaymentMoney(payment.amount)}</dd></div>
      {created && <div><dt>Token expires</dt><dd>{new Date(created.session.expiresAt).toLocaleString()} (5 minutes after issue)</dd></div>}
    </dl>
    {enabled === null && <p role="status">Checking QR availability...</p>}
    {enabled === false && <p role="alert">QR simulation is unavailable on this server.</p>}
    {enabled && !result && <>
      <Button disabled={busy} onClick={() => void start()}>{created ? 'Generate a new QR token' : 'Generate QR token'}</Button>
      {created && !expired && qr && <figure className="qr-demo-card__figure">
        <svg className="qr-demo-card__symbol" viewBox={`0 0 ${qr.size} ${qr.size}`} role="img" aria-label="Scannable payment verification token" shapeRendering="crispEdges">
          <rect width={qr.size} height={qr.size} className="qr-demo-card__paper" /><path d={qr.path} fill="currentColor" />
        </svg>
        <figcaption>Scan to reveal the token, then copy it into the field below.</figcaption>
      </figure>}
      {created && expired && <p role="alert">This QR token expired. Generate a new one.</p>}
      {created && !expired && <div className="qr-demo-entry">
        <label htmlFor="demo-qr-token">Verification token</label>
        <input id="demo-qr-token" value={tokenInput} onChange={(event) => setTokenInput(event.target.value)} autoComplete="off" spellCheck={false} maxLength={43} />
        <Button disabled={busy || !DEMO_TOKEN.test(tokenInput.trim())} onClick={() => void confirm()}>
          {busy ? 'Verifying...' : 'Verify and Confirm Payment'}
        </Button>
      </div>}
    </>}
    {result && <div role="status" className="notice notice--success">
      <strong>Payment Successful - Demo</strong>
      <p>Settlement provenance: SIMULATED. Invoice {result.invoiceNumber} is available.</p>
      <a className="btn btn--secondary" href={PAYMENT_PATHS.receipt(result.invoiceId!)}>View receipt</a>
    </div>}
    {error && <p role="alert" className="notice notice--danger">{error}</p>}
  </Card>
}
