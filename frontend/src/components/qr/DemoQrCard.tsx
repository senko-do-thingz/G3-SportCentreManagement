import { useId, useMemo } from 'react'
import QRCode from 'qrcode'
import type { Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { Card } from '../ui'
import { demoQrPayload } from './demoQr'
import './demoQr.css'

/** Only id/amount are consumed. Actual recipient, member and bank instruction fields are never used. */
export function DemoQrCard({ payment }: { payment: Pick<Payment, 'id' | 'amount'> }) {
  const titleId = useId()
  const descriptionId = useId()
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
          <div><dt>Bank label</dt><dd>Vietcombank (demo)</dd></div>
          <div><dt>Payment ID</dt><dd>{payment.id}</dd></div>
          <div><dt>Amount from payment</dt><dd>{formatPaymentMoney(payment.amount)}</dd></div>
          <div><dt>Account</dt><dd>DEMO - NOT AVAILABLE</dd></div>
          <div><dt>Demo reference</dt><dd>DEMO-{payment.id}</dd></div>
        </dl>
      </div>
    </Card>
  )
}
