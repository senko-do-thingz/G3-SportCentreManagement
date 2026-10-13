import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import type { Payment } from '../../api/paymentsApi'
import { Button, Card, EmptyState, ErrorState, PageHeader, StatusBadge, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { copyText } from '../../utils/clipboard'
import { formatMoney, formatTime } from '../../utils/format'
import { MEMBER_PATHS } from './dashboardModel'
import { PaymentNav } from './PaymentNav'
import {
  accountNumberToCopy,
  amountToCopy,
  methodLabel,
  parseIdParam,
  PAYMENT_PATHS,
  paymentStatusLabel,
  paymentStatusToneCode,
  POLL_INTERVAL_MS,
  transferContentToCopy,
} from './paymentsModel'
import { usePaymentPolling } from './usePaymentPolling'

/**
 * Payment Instructions and Status (Figma F3-02): bank transfer details with copy buttons, the
 * status badge and a Refresh button. While the payment is PENDING the page asks
 * GET /payments/{id} again every 15 seconds and stops on SUCCESS, FAILED or REFUNDED, so it updates
 * by itself when the receptionist confirms the payment.
 */
export function PaymentStatusPage() {
  const { paymentId } = useParams()
  const id = parseIdParam(paymentId)
  if (id === null) {
    return (
      <div className="page">
        <PageHeader eyebrow="Member / Payments" title="Payment Instructions and Status" />
        <EmptyState
          title="Payment not found"
          description="The payment link is not valid."
          action={
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>
              My payments
            </Link>
          }
        />
      </div>
    )
  }
  return <PaymentStatusView key={id} paymentId={id} />
}

function PaymentStatusView({ paymentId }: { paymentId: number }) {
  const polling = usePaymentPolling(paymentId)
  const { payment, loadError, refresh } = polling
  const toast = useToast()
  const previousStatus = useRef<string | null>(null)

  // Tell the member once when the status changes while the page is open.
  useEffect(() => {
    const status = payment?.status ?? null
    const before = previousStatus.current
    previousStatus.current = status
    if (before === 'PENDING' && status === 'SUCCESS') {
      toast.success('Payment confirmed', 'Reception confirmed your payment.')
    } else if (before === 'PENDING' && status && status !== 'PENDING') {
      toast.info(`Payment ${paymentStatusLabel(status).toLowerCase()}`)
    }
  }, [payment?.status, toast])

  const header = (
    <PageHeader
      eyebrow="Member / Payments"
      title="Payment Instructions and Status"
      description="Pay at reception or use the bank transfer details below."
    />
  )

  if (!payment) {
    return (
      <div className="page">
        <PaymentNav paymentId={paymentId} />
        {header}
        {loadError ? (
          <ErrorState title="We could not load the payment" error={loadError} onRetry={refresh} />
        ) : (
          <Spinner label="Loading the payment" />
        )}
      </div>
    )
  }

  return (
    <div className="page">
      <PaymentNav paymentId={paymentId} />
      {header}
      <div className="payment-summary">
        <Instructions payment={payment} />
        <StatusPanel polling={polling} payment={payment} />
      </div>
    </div>
  )
}

function Instructions({ payment }: { payment: Payment }) {
  const instructions = payment.bankTransferInstructions
  if (payment.status !== 'PENDING') {
    return (
      <Card title="Payment details" aria-label="Payment details">
        <dl className="summary-list">
          <div>
            <dt>Payment reference</dt>
            <dd>{payment.code}</dd>
          </div>
          <div>
            <dt>Item</dt>
            <dd>{payment.itemDescription ?? '-'}</dd>
          </div>
          <div>
            <dt>Amount</dt>
            <dd>{formatMoney(payment.amount)}</dd>
          </div>
          <div>
            <dt>Method</dt>
            <dd>{methodLabel(payment.method)}</dd>
          </div>
        </dl>
      </Card>
    )
  }
  if (!instructions) {
    return (
      <Card title={methodLabel(payment.method)} aria-label="Payment instructions">
        <dl className="summary-list">
          <div>
            <dt>Payment reference</dt>
            <dd>
              <CopyValue label="payment reference" value={payment.code} copy={payment.code} />
            </dd>
          </div>
          <div>
            <dt>Amount to pay</dt>
            <dd>{formatMoney(payment.amount)}</dd>
          </div>
        </dl>
        <div className="notice notice--info payment-summary__note">
          <p>
            {methodLabel(payment.method)} has no online checkout yet. Switch to bank transfer on the summary, or pay at
            reception with this payment reference.
          </p>
        </div>
        <Link className={buttonClassName('secondary', 'md', true)} to={PAYMENT_PATHS.summary(payment.id)}>
          Switch to bank transfer
        </Link>
      </Card>
    )
  }
  return (
    <Card title="Bank transfer" aria-label="Bank transfer">
      <dl className="summary-list">
        <div>
          <dt>Bank</dt>
          <dd>{instructions.bank ?? '-'}</dd>
        </div>
        <div>
          <dt>Account name</dt>
          <dd>{instructions.accountName ?? '-'}</dd>
        </div>
        <div>
          <dt>Account number</dt>
          <dd>
            {instructions.accountNumber ? (
              <CopyValue
                label="account number"
                value={instructions.accountNumber}
                copy={accountNumberToCopy(instructions.accountNumber)}
              />
            ) : (
              '-'
            )}
          </dd>
        </div>
        <div>
          <dt>Transfer content</dt>
          <dd>
            <CopyValue label="transfer content" value={transferContentToCopy(payment)} copy={transferContentToCopy(payment)} />
          </dd>
        </div>
        <div>
          <dt>Amount to transfer</dt>
          <dd>
            <CopyValue label="amount" value={formatMoney(payment.amount)} copy={amountToCopy(payment.amount)} />
          </dd>
        </div>
      </dl>
      <div className="notice notice--info payment-summary__note">
        <p>Use the transfer content exactly as shown so reception can match your payment.</p>
        <p>Your request stays pending until the funds are verified.</p>
      </div>
      <Link className={buttonClassName('secondary', 'md', true)} to={PAYMENT_PATHS.summary(payment.id)}>
        Back to summary
      </Link>
    </Card>
  )
}

interface CopyValueProps {
  /** Lower case name used in the button label and the toast, for example "account number". */
  label: string
  /** Text shown on screen. */
  value: string
  /** Text put on the clipboard. */
  copy: string
}

function CopyValue({ label, value, copy }: CopyValueProps) {
  const toast = useToast()
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) {
      return
    }
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  async function handleCopy() {
    try {
      await copyText(copy)
      setCopied(true)
      toast.success(`Copied ${label}`, copy)
    } catch {
      toast.error(`Could not copy the ${label}`, 'Select the text and copy it by hand.')
    }
  }

  return (
    <span className="copy-value">
      <span className="copy-value__text">{value}</span>
      <button type="button" className="copy-value__button no-print" onClick={handleCopy} aria-label={`Copy ${label}`}>
        {copied ? 'Copied' : 'Copy'}
      </button>
    </span>
  )
}

function StatusPanel({ polling, payment }: { polling: ReturnType<typeof usePaymentPolling>; payment: Payment }) {
  const { refresh, refreshing, refreshError, checkedAt } = polling
  const content = STATUS_TEXT[payment.status] ?? STATUS_TEXT.UNKNOWN
  return (
    <Card title="Payment status" aria-label="Payment status">
      <div className="payment-status" aria-live="polite" data-status={payment.status}>
        <StatusBadge status={paymentStatusToneCode(payment.status)} label={paymentStatusLabel(payment.status)} />
        <h3 className="payment-status__title">{content.title}</h3>
        <p className="payment-status__text">{content.text}</p>
      </div>

      {payment.status === 'PENDING' && (
        <>
          <div className="notice notice--info payment-summary__note">
            <p>Reception confirms the funds received. Do not submit a second request.</p>
          </div>
          <p className="payment-status__check">
            {polling.polling
              ? `Checks again every ${POLL_INTERVAL_MS / 1000} seconds.`
              : 'Automatic checks stopped. Use Refresh status to check again.'}{' '}
            Last checked {checkedAt ? formatTime(checkedAt) : '-'}.
          </p>
          {refreshError ? (
            <p className="payment-status__error" role="alert">
              Could not check the status: {getErrorMessage(refreshError)}
            </p>
          ) : null}
          <Button variant="secondary" fullWidth loading={refreshing} onClick={refresh}>
            Refresh status
          </Button>
          <h3 className="payment-status__subtitle">Prefer cash?</h3>
          <p className="payment-status__text">Give reception your {payment.code} reference.</p>
        </>
      )}

      <div className="payment-summary__actions">
        {payment.status === 'SUCCESS' && payment.invoiceId ? (
          <Link className={buttonClassName('primary', 'md', true)} to={PAYMENT_PATHS.receipt(payment.invoiceId)}>
            View receipt
          </Link>
        ) : null}
        {payment.status === 'FAILED' && (
          <Link className={buttonClassName('primary', 'md', true)} to={MEMBER_PATHS.membership}>
            My membership and packages
          </Link>
        )}
        <Link className={buttonClassName(payment.status === 'PENDING' ? 'primary' : 'secondary', 'md', true)} to={PAYMENT_PATHS.list}>
          View my payments
        </Link>
      </div>
    </Card>
  )
}

const STATUS_TEXT: Record<string, { title: string; text: string }> = {
  PENDING: { title: 'Awaiting confirmation', text: 'Your package or card is not active yet.' },
  SUCCESS: { title: 'Payment confirmed', text: 'Your package or card is now active.' },
  FAILED: { title: 'Payment failed', text: 'This payment was not completed. Make a new request to try again.' },
  REFUNDED: { title: 'Payment refunded', text: 'This payment was refunded.' },
  UNKNOWN: { title: 'Status unknown', text: 'Contact reception with your payment reference.' },
}
