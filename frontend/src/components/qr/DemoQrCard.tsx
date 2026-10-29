import { useEffect, useId, useMemo, useRef, useState } from 'react'
import QRCode from 'qrcode'
import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { copyText } from '../../utils/clipboard'
import { Button, Card, Notice, Select } from '../ui'
import { demoQrPayload } from './demoQr'
import { DEFAULT_DEMO_BANK, DEMO_ACCOUNT, DEMO_BANK_OPTIONS, DEMO_BANKS, demoDetails, demoReference, normalizeDemoBank, type DemoBank } from './demoDetails'
import './demoQr.css'

/** Only id/amount are consumed. Actual recipient, member and bank instruction fields are never used. */
export function DemoQrCard({ payment }: { payment: Pick<Payment, 'id' | 'amount'> }) {
  // Remount on authorized content changes so an old payment's async feedback cannot survive.
  return <DemoQrCardContent key={`${payment.id}:${payment.amount}`} payment={payment} />
}

function DemoQrCardContent({ payment }: { payment: Pick<Payment, 'id' | 'amount'> }) {
  const titleId = useId()
  const descriptionId = useId()
  const [bank, setBank] = useState<DemoBank>(DEFAULT_DEMO_BANK)
  const [feedback, setFeedback] = useState<{ kind: 'pending' | 'success' | 'error'; text: string } | null>(null)
  const request = useRef(0)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; request.current += 1 }
  }, [])
  const reference = demoReference(payment.id)
  async function copy(kind: 'reference' | 'details') {
    const current = ++request.current
    const label = kind === 'reference' ? 'Demo reference' : 'Demo details'
    setFeedback({ kind: 'pending', text: `Copying ${label.toLowerCase()}...` })
    try {
      await copyText(kind === 'reference' ? reference : demoDetails(payment, bank), { allowFallback: false })
      if (mounted.current && current === request.current) setFeedback({ kind: 'success', text: `${label} copied. Demo only - not a real payment.` })
    } catch {
      if (mounted.current && current === request.current) setFeedback({ kind: 'error', text: 'Could not copy. Select and copy the demo text manually.' })
    }
  }
  const payload = demoQrPayload(payment.id)
  const qr = useMemo(() => QRCode.create(payload, { errorCorrectionLevel: 'M' }), [payload])
  const quietZone = 4
  const size = qr.modules.size + quietZone * 2
  const path = Array.from(qr.modules.data, (dark, index) => dark
    ? `M${index % qr.modules.size + quietZone} ${Math.floor(index / qr.modules.size) + quietZone}h1v1h-1z`
    : '').join('')
  return (
    <Card title="QR Payment (Demo)" className="qr-demo-card" aria-label="Non-payable QR demo">
      <p className="notice notice--warning">DEMO ONLY - NO MONEY WILL BE TRANSFERRED</p>
      <p className="card__intro">A generic scanner can read demo text. This is not a bank payment QR and cannot be used to pay.</p>
      <Select label="Demo bank" options={DEMO_BANK_OPTIONS} value={bank}
        hint="Bank selection changes example information only. The QR remains a fixed, non-payable Vietcombank demo sample."
        onChange={(event) => {
          request.current += 1
          setBank(normalizeDemoBank(event.target.value))
          setFeedback(null)
        }} />
      <div className="qr-demo-card__body">
        <figure className="qr-demo-card__figure">
          <svg className="qr-demo-card__symbol" viewBox={`0 0 ${size} ${size}`} role="img"
            aria-labelledby={`${titleId} ${descriptionId}`} shapeRendering="crispEdges">
            <title id={titleId}>Non-payable demo QR for payment {payment.id}</title>
            <desc id={descriptionId}>Encodes SPORTIFY_DEMO_QR plain text marked NON_PAYABLE. No beneficiary account or banking instruction is encoded.</desc>
            <rect width={size} height={size} className="qr-demo-card__paper" />
            <path d={path} fill="currentColor" />
          </svg>
          <figcaption>DEMO ONLY - NOT A REAL PAYMENT</figcaption>
        </figure>
        <dl className="summary-list">
          <div><dt>Demo bank label</dt><dd>{DEMO_BANKS[bank]}</dd></div>
          <div><dt>Payment ID</dt><dd>{payment.id}</dd></div>
          <div><dt>Amount from payment</dt><dd>{formatPaymentMoney(payment.amount)}</dd></div>
          <div><dt>Account</dt><dd>{DEMO_ACCOUNT}</dd></div>
          <div><dt>Demo reference</dt><dd>{reference}</dd></div>
        </dl>
      </div>
      <div className="qr-demo-card__copy-actions">
        <Button variant="secondary" onClick={() => void copy('reference')}>Copy demo reference</Button>
        <Button variant="secondary" onClick={() => void copy('details')}>Copy demo details</Button>
      </div>
      <div role="status" aria-live="polite" aria-atomic="true">
        {feedback && feedback.kind !== 'error' && <Notice tone={feedback.kind === 'success' ? 'success' : 'info'} live="off">{feedback.text}</Notice>}
      </div>
      <div role="alert" aria-atomic="true">
        {feedback?.kind === 'error' && <Notice tone="danger" live="off">{feedback.text}</Notice>}
      </div>
    </Card>
  )
}
