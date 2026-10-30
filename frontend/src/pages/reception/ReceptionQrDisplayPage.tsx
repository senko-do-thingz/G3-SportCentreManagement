import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import QRCode from 'qrcode'
import { demoQrApi, type ReceptionDeskProjection } from '../../api/demoQrApi'
import { copyText } from '../../utils/clipboard'
import { Button, Card, EmptyState, ErrorState, Notice, Select, StatusBadge } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { PaymentHero } from '../../components/PaymentHero'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { qrPaymentDemoEnabled } from '../../config/env'
import { useResource } from '../../hooks/useResource'
import { demoQrPayload } from '../../components/qr/demoQr'
import {
  DEFAULT_DEMO_BANK,
  DEMO_ACCOUNT,
  DEMO_BANK_OPTIONS,
  DEMO_BANKS,
  demoDetails,
  demoPaymentAmountAvailable,
  demoReference,
  normalizeDemoBank,
  type DemoBank,
} from '../../components/qr/demoDetails'
import '../../components/qr/demoQr.css'
import { formatPaymentMoney } from '../member/paymentsModel'
import { positiveId } from './receptionModel'
import { PAYMENT_LIST_PATH, paymentReceiptPath } from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

export function ReceptionQrDisplayPage() {
  const { paymentId } = useParams()
  const id = positiveId(paymentId)

  if (!qrPaymentDemoEnabled()) {
    return (
      <div className="page staff-layout">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" />
        <EmptyState
          title="QR Simulator Disabled"
          description="The payment simulator is not enabled in this environment."
          action={
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_LIST_PATH}>
              Payment requests
            </Link>
          }
        />
      </div>
    )
  }

  if (id === null) {
    return (
      <div className="page staff-layout">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" />
        <EmptyState
          title="Payment not found"
          description="The payment link is not valid."
          action={
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_LIST_PATH}>
              Payment requests
            </Link>
          }
        />
      </div>
    )
  }

  return <ReceptionQrDisplayContent key={id} paymentId={id} />
}

function ReceptionQrDisplayContent({ paymentId }: { paymentId: number }) {
  const load = useCallback(
    () => demoQrApi.receptionDeskProjection(paymentId),
    [paymentId],
  )
  const [resource, reload] = useResource(load)
  const [polledProjection, setPolledProjection] = useState<ReceptionDeskProjection | null>(null)
  const [bank, setBank] = useState<DemoBank>(DEFAULT_DEMO_BANK)
  const [feedback, setFeedback] = useState<{ kind: 'pending' | 'success' | 'error'; text: string } | null>(null)
  const copyRequest = useRef(0)
  const mounted = useRef(true)

  const titleId = useId()
  const descriptionId = useId()

  const projection = polledProjection ?? (resource.status === 'ready' ? resource.data : null)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  // Poll every 4 seconds while the payment is pending
  useEffect(() => {
    if (!projection || projection.paymentStatus !== 'PENDING') {
      return
    }

    const timer = setInterval(() => {
      void demoQrApi.receptionDeskProjection(paymentId).then(
        (data) => {
          if (mounted.current) {
            setPolledProjection(data)
          }
        },
        () => {
          // Gracefully suppress periodic polling network blips
        },
      )
    }, 4000)

    return () => {
      clearInterval(timer)
    }
  }, [paymentId, projection])

  const amountAvailable = projection ? demoPaymentAmountAvailable(projection.amount) : false

  const generatedQr = useMemo(() => {
    if (!amountAvailable || !projection) return null
    try {
      const payload = demoQrPayload(projection.paymentId, bank)
      return {
        qr: QRCode.create(payload, { errorCorrectionLevel: 'M' }),
        reference: demoReference(projection.paymentId),
      }
    } catch {
      return null
    }
  }, [amountAvailable, projection, bank])

  async function copy(kind: 'reference' | 'details') {
    const current = ++copyRequest.current
    const label = kind === 'reference' ? 'Demo reference' : 'Demo details'
    setFeedback({ kind: 'pending', text: `Copying ${label.toLowerCase()}...` })
    try {
      if (!generatedQr || !projection) throw new Error('Demo QR unavailable')
      const textToCopy =
        kind === 'reference'
          ? generatedQr.reference
          : demoDetails({ id: projection.paymentId, amount: projection.amount }, bank)
      await copyText(textToCopy, { allowFallback: false })
      if (mounted.current && current === copyRequest.current) {
        setFeedback({
          kind: 'success',
          text: `${label} copied. Simulated payment - no real money is transferred.`,
        })
      }
    } catch {
      if (mounted.current && current === copyRequest.current) {
        setFeedback({
          kind: 'error',
          text: 'Could not copy. Select and copy the demo text manually.',
        })
      }
    }
  }

  const actions = (
    <div className="reception-actions reception-print-hidden">
      <Link
        className={buttonClassName('secondary', 'md')}
        to={`/reception/payments/${paymentId}`}
      >
        Back to payment verification
      </Link>
      <Link className={buttonClassName('ghost', 'md')} to={PAYMENT_LIST_PATH}>
        Payment requests
      </Link>
    </div>
  )

  if (resource.status === 'loading') {
    return (
      <div className="page staff-layout">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" aside={actions} />
        <Spinner label="Loading the desk QR presentation" />
      </div>
    )
  }

  if (resource.status === 'error' || !projection) {
    return (
      <div className="page staff-layout">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" aside={actions} />
        <ErrorState
          title="Could not load desk projection"
          error={resource.status === 'error' ? resource.error : null}
          onRetry={reload}
        />
      </div>
    )
  }

  const isSuccess = projection.paymentStatus === 'SUCCESS'
  const isPending = projection.paymentStatus === 'PENDING'

  return (
    <div className="page staff-layout">
      <PaymentHero
        eyebrow={EYEBROW}
        title="Simulated QR Payment Display"
        description="Customer-facing simulated QR code presentation for front desk payments. The customer confirms on their own device. Reception staff cannot settle simulated payments directly."
        aside={actions}
      />

      <div role="status" aria-live="polite" className="reception-notice-banner">
        <p className="notice notice--warning">
          Simulated QR Payment - No real money is transferred.
        </p>
      </div>

      {isSuccess && (
        <div role="status" aria-live="polite" className="reception-notice-banner">
          <Notice tone="success">
            Simulated payment received. Provenance: {projection.settlementType ?? 'SIMULATED'} (
            {projection.settlementProvider ?? 'SPORTIFY_SIMULATOR'}). Entitlement is active.
          </Notice>
        </div>
      )}

      <div className="reception-grid">
        {/* Left column: Customer QR code presentation */}
        <Card
          title="Customer QR Presentation"
          className="qr-demo-card"
          aria-label="Non-payable QR presentation"
        >
          <p className="card__intro">
            Present this code to the customer. A generic scanner or member portal reads the demo
            payload. This is not a bank transfer and transfers no real funds.
          </p>

          <Select
            label="Demo bank"
            options={DEMO_BANK_OPTIONS}
            value={bank}
            hint="Changes the simulated bank payload for customer scanning. plain text only; no banking API or gateway integration."
            onChange={(event) => {
              copyRequest.current += 1
              setBank(normalizeDemoBank(event.target.value))
              setFeedback(null)
            }}
          />

          {generatedQr ? (
            <div className="qr-demo-card__body">
              <figure className="qr-demo-card__figure">
                <svg
                  className="qr-demo-card__symbol"
                  viewBox={`0 0 ${generatedQr.qr.modules.size + 8} ${generatedQr.qr.modules.size + 8}`}
                  role="img"
                  aria-labelledby={`${titleId} ${descriptionId}`}
                  shapeRendering="crispEdges"
                >
                  <title id={titleId}>
                    Non-payable demo QR for payment {projection.paymentId}, {DEMO_BANKS[bank].label}
                  </title>
                  <desc id={descriptionId}>
                    Encodes SPORTIFY_DEMO_QR version 2 plain text marked NON_PAYABLE with the
                    selected demo bank code.
                  </desc>
                  <rect
                    width={generatedQr.qr.modules.size + 8}
                    height={generatedQr.qr.modules.size + 8}
                    className="qr-demo-card__paper"
                  />
                  <path
                    d={Array.from(generatedQr.qr.modules.data, (dark, index) =>
                      dark
                        ? `M${(index % generatedQr.qr.modules.size) + 4} ${
                            Math.floor(index / generatedQr.qr.modules.size) + 4
                          }h1v1h-1z`
                        : '',
                    ).join('')}
                    fill="currentColor"
                  />
                </svg>
                <figcaption>Simulated QR - No real bank transfer</figcaption>
              </figure>

              <dl className="summary-list">
                <div>
                  <dt>Demo bank label</dt>
                  <dd>{DEMO_BANKS[bank].label}</dd>
                </div>
                <div>
                  <dt>Payment ID</dt>
                  <dd>{projection.paymentId}</dd>
                </div>
                <div>
                  <dt>Amount due</dt>
                  <dd>{formatPaymentMoney(projection.amount)}</dd>
                </div>
                <div>
                  <dt>Account</dt>
                  <dd>{DEMO_ACCOUNT}</dd>
                </div>
                <div>
                  <dt>Demo reference</dt>
                  <dd>{generatedQr.reference}</dd>
                </div>
              </dl>
            </div>
          ) : (
            <Notice tone="danger">
              Could not generate QR code for this payment amount.
            </Notice>
          )}

          <div className="qr-demo-card__copy-actions">
            <Button variant="secondary" onClick={() => void copy('reference')}>
              Copy demo reference
            </Button>
            <Button variant="secondary" onClick={() => void copy('details')}>
              Copy demo details
            </Button>
          </div>

          <div role="status" aria-live="polite" aria-atomic="true">
            {feedback && feedback.kind !== 'error' && (
              <Notice tone={feedback.kind === 'success' ? 'success' : 'info'} live="off">
                {feedback.text}
              </Notice>
            )}
          </div>
          <div role="alert" aria-atomic="true">
            {feedback?.kind === 'error' && (
              <Notice tone="danger" live="off">
                {feedback.text}
              </Notice>
            )}
          </div>
        </Card>

        {/* Right column: Authoritative Payment & Settlement Status */}
        <Card title="Payment & Settlement Status" aria-label="Authoritative payment status">
          <div className="reception-status-header" style={{ marginBottom: 'var(--space-4)' }}>
            <StatusBadge
              status={
                isSuccess
                  ? 'success'
                  : isPending
                  ? 'warning'
                  : 'danger'
              }
              label={
                isSuccess
                  ? 'Paid - Simulated'
                  : isPending
                  ? 'Pending payment'
                  : projection.paymentStatus
              }
            />
            {isPending && (
              <span
                style={{
                  fontSize: 'var(--text-xs)',
                  color: 'var(--color-muted)',
                  marginLeft: 'var(--space-3)',
                }}
              >
                Auto-refreshing every 4s
              </span>
            )}
          </div>

          <dl className="summary-list">
            <div>
              <dt>Payment code</dt>
              <dd>{projection.paymentCode}</dd>
            </div>
            <div>
              <dt>Member</dt>
              <dd>
                {projection.memberFullName ?? 'Unknown member'}{' '}
                {projection.memberCode ? `(${projection.memberCode})` : ''}
              </dd>
            </div>
            <div>
              <dt>Item / Service</dt>
              <dd>{projection.itemDescription ?? 'Membership or service fee'}</dd>
            </div>
            <div>
              <dt>Amount due</dt>
              <dd>{formatPaymentMoney(projection.amount)}</dd>
            </div>
            {projection.settlementType && (
              <div>
                <dt>Settlement type</dt>
                <dd>{projection.settlementType}</dd>
              </div>
            )}
            {projection.settlementProvider && (
              <div>
                <dt>Provider</dt>
                <dd>{projection.settlementProvider}</dd>
              </div>
            )}
            {projection.simulatedTransactionId && (
              <div>
                <dt>Simulated Tx ID</dt>
                <dd>TXN-{projection.simulatedTransactionId}</dd>
              </div>
            )}
          </dl>

          {/* Active session metadata projection (Strictly read-only, no tokens) */}
          {projection.activeSession && (
            <div
              style={{
                marginTop: 'var(--space-5)',
                paddingTop: 'var(--space-4)',
                borderTop: '1px solid var(--border-subtle)',
              }}
            >
              <h3 style={{ fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
                Active Simulation Session
              </h3>
              <dl className="summary-list">
                <div>
                  <dt>Session ID</dt>
                  <dd style={{ fontFamily: 'monospace', fontSize: 'var(--text-xs)' }}>
                    {projection.activeSession.sessionId}
                  </dd>
                </div>
                <div>
                  <dt>Selected Bank</dt>
                  <dd>{projection.activeSession.bankLabel}</dd>
                </div>
                <div>
                  <dt>Session State</dt>
                  <dd>
                    <StatusBadge
                      status={
                        projection.activeSession.state === 'PENDING'
                          ? 'info'
                          : projection.activeSession.state === 'SETTLED'
                          ? 'success'
                          : 'neutral'
                      }
                      label={projection.activeSession.state}
                    />
                  </dd>
                </div>
              </dl>
            </div>
          )}

          <div style={{ marginTop: 'var(--space-6)' }}>
            {isSuccess ? (
              <Link
                to={paymentReceiptPath(projection.paymentId)}
                className={buttonClassName('primary', 'md')}
              >
                View receipt
              </Link>
            ) : (
              <p
                style={{
                  fontSize: 'var(--text-sm)',
                  color: 'var(--color-muted)',
                  margin: 0,
                }}
              >
                Awaiting member authorization. Once the customer completes simulated payment on
                their device, this status updates automatically.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
