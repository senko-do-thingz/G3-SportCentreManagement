import { useEffect, useId, useMemo, useRef, useState } from 'react'
import QRCode from 'qrcode'
import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { copyText } from '../../utils/clipboard'
import { Button, Card, Notice, Select } from '../ui'
import { demoQrPayload } from './demoQr'
import { DEFAULT_DEMO_BANK, DEMO_ACCOUNT, DEMO_BANK_OPTIONS, DEMO_BANKS, demoDetails, demoPaymentAmountAvailable, demoReference, normalizeDemoBank, type DemoBank } from './demoDetails'
import './demoQr.css'
import { DemoSessionPanel } from './DemoSessionPanel'

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
  const generated = useMemo(() => {
    try {
      if (!demoPaymentAmountAvailable(payment.amount)) throw new Error('Demo amount unavailable')
      const payload = demoQrPayload(payment.id, bank)
      return { qr: QRCode.create(payload, { errorCorrectionLevel: 'M' }), reference: demoReference(payment.id) }
    } catch {
      return null
    }
  }, [payment.id, payment.amount, bank])
  async function copy(kind: 'reference' | 'details') {
    const current = ++request.current
    const label = kind === 'reference' ? 'Demo reference' : 'Demo details'
    setFeedback({ kind: 'pending', text: `Copying ${label.toLowerCase()}...` })
    try {
      if (!generated) throw new Error('Demo QR unavailable')
      await copyText(kind === 'reference' ? generated.reference : demoDetails(payment, bank), { allowFallback: false })
      if (mounted.current && current === request.current) setFeedback({ kind: 'success', text: `${label} copied. Demo only - not a real payment.` })
    } catch {
      if (mounted.current && current === request.current) setFeedback({ kind: 'error', text: 'Could not copy. Select and copy the demo text manually.' })
    }
  }
  if (!generated) return (
    <Card title="QR Payment (Demo)" className="qr-demo-card" aria-label="Non-payable QR demo">
      <Notice tone="danger">The demo QR could not be generated. No payment was made. Choose another payment or reopen this demo.</Notice>
    </Card>
  )
  const { qr, reference } = generated
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
        hint="The QR updates for the selected demo bank and authorized payment. It contains plain text only, cannot transfer money in a banking app, and does not confirm a payment."
        onChange={(event) => {
          request.current += 1
          setBank(normalizeDemoBank(event.target.value))
          setFeedback(null)
        }} />
      <div className="qr-demo-card__body">
        <figure className="qr-demo-card__figure">
          <svg className="qr-demo-card__symbol" viewBox={`0 0 ${size} ${size}`} role="img"
            aria-labelledby={`${titleId} ${descriptionId}`} shapeRendering="crispEdges">
            <title id={titleId}>Non-payable demo QR for payment {payment.id}, {DEMO_BANKS[bank].label}</title>
            <desc id={descriptionId}>Encodes SPORTIFY_DEMO_QR version 2 plain text marked NON_PAYABLE with the selected demo bank code. No beneficiary account, payment URL or banking instruction is encoded.</desc>
            <rect width={size} height={size} className="qr-demo-card__paper" />
            <path d={path} fill="currentColor" />
          </svg>
          <figcaption>DEMO ONLY - NOT A REAL PAYMENT</figcaption>
        </figure>
        <dl className="summary-list">
          <div><dt>Demo bank label</dt><dd>{DEMO_BANKS[bank].label}</dd></div>
          <div><dt>Payment ID</dt><dd>{payment.id}</dd></div>
          <div><dt>Amount from payment</dt><dd>{formatPaymentMoney(payment.amount)}</dd></div>
          <div><dt>Account</dt><dd>{DEMO_ACCOUNT}</dd></div>
          <div><dt>Demo reference</dt><dd>{reference}</dd></div>
        </dl>
      </div>
      <p aria-live="polite" aria-atomic="true" className="visually-hidden">Demo QR updated for {DEMO_BANKS[bank].label}, payment {payment.id}. Non-payable text only; no payment confirmed.</p>
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
      <DemoSessionPanel paymentId={payment.id} bank={bank} amount={payment.amount} />
    </Card>
  )
}
