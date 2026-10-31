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
import {
  classifyFinancialPresentation,
  mergePolledProjection,
  resolveBankSession,
} from './receptionQrDisplayModel'

const EYEBROW = 'Receptionist / Payments and reports'

export function ReceptionQrDisplayPage() {
  const { paymentId } = useParams()
  const id = positiveId(paymentId)

  if (!qrPaymentDemoEnabled()) {
    return (
      <div className="page reception-qr-display-page">
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
      <div className="page reception-qr-display-page">
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
  const [previewBank, setPreviewBank] = useState<DemoBank>(DEFAULT_DEMO_BANK)
  const [feedback, setFeedback] = useState<{ kind: 'pending' | 'success' | 'error'; text: string } | null>(null)
  const copyRequest = useRef(0)
  const mounted = useRef(true)

  const titleId = useId()
  const descriptionId = useId()

  const projection = polledProjection ?? (resource.status === 'ready' ? resource.data : null)
  const presentation = classifyFinancialPresentation(projection)
  const bankSession = resolveBankSession(projection?.activeSession, previewBank)
  const effectiveBank = bankSession.effectiveBank

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
            setPolledProjection((prev) => mergePolledProjection(prev, data))
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
    if (!amountAvailable || !projection || presentation.isTerminal) return null
    try {
      const payload = demoQrPayload(projection.paymentId, effectiveBank)
      return {
        qr: QRCode.create(payload, { errorCorrectionLevel: 'M' }),
        reference: demoReference(projection.paymentId),
      }
    } catch {
      return null
    }
  }, [amountAvailable, projection, effectiveBank, presentation.isTerminal])

  async function copy(kind: 'reference' | 'details') {
    if (presentation.isTerminal) return
    const current = ++copyRequest.current
    const label = kind === 'reference' ? 'Demo reference' : 'Demo details'
    setFeedback({ kind: 'pending', text: `Copying ${label.toLowerCase()}...` })
    try {
      if (!generatedQr || !projection) throw new Error('Demo QR unavailable')
      const textToCopy =
        kind === 'reference'
          ? generatedQr.reference
          : demoDetails({ id: projection.paymentId, amount: projection.amount }, effectiveBank)
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
      <div className="page reception-qr-display-page">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" aside={actions} />
        <Spinner label="Loading the desk QR presentation" />
      </div>
    )
  }

  if (resource.status === 'error' || !projection) {
    return (
      <div className="page reception-qr-display-page">
        <PaymentHero eyebrow={EYEBROW} title="Simulated QR Payment Display" aside={actions} />
        <ErrorState
          title="Could not load desk projection"
          error={resource.status === 'error' ? resource.error : null}
          onRetry={reload}
        />
      </div>
    )
  }

  return (
    <div className="page reception-qr-display-page">
      <PaymentHero
        eyebrow={EYEBROW}
        title="Simulated QR Payment Display"
        description="Customer-facing informational QR presentation for front desk payments. The customer confirms on their own device. Reception staff cannot settle simulated payments directly."
        aside={actions}
      />

      {presentation.classification !== 'ACTUAL' && (
        <div role="status" aria-live="polite" className="reception-notice-banner">
          <p className="notice notice--warning">
            Simulated QR Payment - No real money is transferred.
          </p>
        </div>
      )}

      {presentation.bannerMessage && (
        <div role="status" aria-live="polite" className="reception-notice-banner">
          <Notice tone={presentation.bannerTone ?? 'info'}>
            {presentation.bannerMessage}
          </Notice>
        </div>
      )}

      <div className="reception-grid">
        {/* Left column: Informational QR code presentation */}
        <Card
          title="Informational Desk QR (Not Payable)"
          className="qr-demo-card"
          aria-label="Informational QR presentation"
        >
          <div className="card__intro">
            <p>
              <strong>Informational QR - Not payable:</strong> Scanning this code does not transfer funds or confirm payment. Front desk staff cannot settle payments directly.
            </p>
            <div
              style={{
                marginTop: 'var(--space-3)',
                padding: 'var(--space-3)',
                background: 'var(--color-surface-subtle)',
                borderRadius: 'var(--radius-md)',
              }}
            >
              <p style={{ fontWeight: 600, margin: '0 0 var(--space-2)' }}>
                Member Self-Confirmation Steps:
              </p>
              <ol
                style={{
                  margin: 0,
                  paddingLeft: 'var(--space-4)',
                  fontSize: 'var(--text-sm)',
                  color: 'var(--color-muted)',
                }}
              >
                <li>Customer signs in at <code>/login</code> with their Sportify Member account.</li>
                <li>Customer opens <strong>My Payments</strong> or <strong>Payment Summary</strong> (<code>/member/payments/{projection.paymentId}</code>).</li>
                <li>Customer opens simulated payment (<code>/member/payments/{projection.paymentId}/qr-demo</code>).</li>
                <li>Customer reviews details and confirms on their own device.</li>
              </ol>
            </div>
            {bankSession.statusNotice && (
              <p
                style={{
                  marginTop: 'var(--space-3)',
                  fontSize: 'var(--text-xs)',
                  color: 'var(--color-muted)',
                }}
              >
                {bankSession.statusNotice}
              </p>
            )}
          </div>

          {/* Bank selector or locked session bank display */}
          {bankSession.isLockedToActiveSession ? (
            <div
              style={{
                marginBottom: 'var(--space-4)',
                padding: 'var(--space-3)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
              }}
            >
              <label
                style={{
                  display: 'block',
                  fontSize: 'var(--text-xs)',
                  fontWeight: 600,
                  color: 'var(--color-muted)',
                  marginBottom: 'var(--space-1)',
                }}
              >
                Session bank (authoritative)
              </label>
              <div style={{ fontWeight: 600, fontSize: 'var(--text-sm)' }}>
                {bankSession.sessionBankLabel}
              </div>
              <p
                style={{
                  margin: 'var(--space-1) 0 0',
                  fontSize: 'var(--text-xs)',
                  color: 'var(--color-muted)',
                }}
              >
                Bank selection is locked to the customer active session. Front desk cannot modify the session bank.
              </p>
            </div>
          ) : (
            <Select
              label="Demo bank"
              options={DEMO_BANK_OPTIONS}
              value={previewBank}
              hint="Informational preview only - Does not change the Member session or settlement bank."
              onChange={(event) => {
                copyRequest.current += 1
                setPreviewBank(normalizeDemoBank(event.target.value))
                setFeedback(null)
              }}
            />
          )}

          {/* QR representation or terminal inactive state */}
          {presentation.isTerminal ? (
            <div
              className="qr-demo-card__body"
              style={{
                textAlign: 'center',
                padding: 'var(--space-6) var(--space-4)',
                border: '1px dashed var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                marginBottom: 'var(--space-4)',
              }}
              role="status"
            >
              <p
                className="notice notice--info"
                style={{ display: 'inline-block', marginBottom: 'var(--space-3)' }}
              >
                QR Code Inactive - Payment is {presentation.statusBadgeLabel}
              </p>
              <p style={{ color: 'var(--color-muted)', fontSize: 'var(--text-sm)', margin: 0 }}>
                This payment has reached a terminal state ({presentation.statusBadgeLabel}). Payment instructions and QR code are no longer active.
              </p>
            </div>
          ) : generatedQr ? (
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
                    Informational demo QR for payment {projection.paymentId}, {DEMO_BANKS[effectiveBank].label}
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
                <figcaption>Informational QR - Not payable</figcaption>
              </figure>

              <dl className="summary-list">
                <div>
                  <dt>Demo bank label</dt>
                  <dd>{DEMO_BANKS[effectiveBank].label}</dd>
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

          {/* Copy actions: only accessible while payment is pending */}
          {!presentation.isTerminal && (
            <div className="qr-demo-card__copy-actions">
              <Button variant="secondary" onClick={() => void copy('reference')}>
                Copy demo reference
              </Button>
              <Button variant="secondary" onClick={() => void copy('details')}>
                Copy demo details
              </Button>
            </div>
          )}

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
              status={presentation.statusBadgeTone}
              label={presentation.statusBadgeLabel}
            />
            {presentation.isPending && (
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
            <div>
              <dt>Settlement provenance</dt>
              <dd>{presentation.provenanceLabel}</dd>
            </div>
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
                Member Simulation Session
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
            {presentation.isSimulatedSuccess || presentation.classification === 'ACTUAL' ? (
              <Link
                to={paymentReceiptPath(projection.paymentId)}
                className={buttonClassName('primary', 'md')}
              >
                View receipt
              </Link>
            ) : presentation.isPending ? (
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
            ) : (
              <p
                style={{
                  fontSize: 'var(--text-sm)',
                  color: 'var(--color-muted)',
                  margin: 0,
                }}
              >
                Payment is finalized in status {presentation.statusBadgeLabel}. No further customer authorization is expected.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
