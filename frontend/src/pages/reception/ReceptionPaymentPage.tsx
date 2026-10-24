import { useCallback, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, getErrorMessage, splitFieldErrors } from '../../api/errors'
import { receptionApi, type PaymentMethod } from '../../api/receptionApi'
import { receptionPaymentsApi, type StaffPayment } from '../../api/receptionPaymentsApi'
import {
  Badge,
  Button,
  Card,
  Checkbox,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Select,
  StatusBadge,
  TextArea,
  useToast,
} from '../../components/ui'
import { PaymentHero } from '../../components/PaymentHero'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, formatDateTime, humanizeCode } from '../../utils/format'
import {
  memberSearchPath,
  PAYMENT_METHOD_LABELS,
  PAYMENT_METHOD_OPTIONS,
  positiveId,
  type AfterPaymentState,
} from './receptionModel'
import {
  amountCheck,
  amountDifference,
  confirmedAmountProblem,
  FAIL_REASON_MAX,
  formatPaymentMoney as formatMoney,
  parsePaymentAmount,
  canConfirm,
  findPaidItem,
  initialConfirmForm,
  INVALID_AMOUNT_DUE_MESSAGE,
  isAmountMismatch,
  isAmountToReview,
  memberLine,
  mismatchAmounts,
  NOTE_MAX,
  PAYMENT_LIST_PATH,
  paymentReceiptPath,
  paymentStatusLabel,
  paymentStatusTone,
  REFERENCE_MAX,
  referenceRequired,
  toConfirmRequest,
  validateConfirmForm,
  validAmountDue,
  VERIFY_STATEMENT,
  type ConfirmErrors,
  type ConfirmForm,
  type MismatchAmounts,
} from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

function methodName(method: string | null | undefined): string {
  if (!method) return '-'
  return PAYMENT_METHOD_LABELS[method as PaymentMethod] ?? humanizeCode(method)
}

/**
 * One payment for the front desk at /reception/payments/:paymentId (FE-12, extends the FE-05
 * confirmation). GET /payments/{id}, then by status:
 * - PENDING: Verify and Record Payment (F3-05). PUT /payments/{id}/confirm (BE-18) with method,
 *   reference (required except for cash), amount received and note, only after the checkbox is
 *   ticked. Every payment method requires the exact amount due. A 409 PAYMENT_AMOUNT_MISMATCH
 *   shows Payment Needs Review (S3-Mismatch) and the
 *   payment stays PENDING. Mark as failed calls PUT /payments/{id}/fail with a reason.
 * - SUCCESS or REFUNDED: Payment Confirmed and Receipt (F3-06) with the invoice of
 *   GET /invoices/by-payment/{id} and the card or package the payment activated.
 * - FAILED: the failed result.
 * ?memberId= (from the member profile or a desk sale) adds the way back to the profile.
 */
export function ReceptionPaymentPage() {
  const { paymentId } = useParams()
  const [params] = useSearchParams()
  const id = positiveId(paymentId)
  const memberId = positiveId(params.get('memberId'))

  const load = useCallback(
    (signal: AbortSignal) =>
      id !== null ? receptionPaymentsApi.payment(id, { signal }) : Promise.reject(new Error('The payment link is not valid.')),
    [id],
  )
  const [payment, reload] = useResource(load)

  const actions = (
    <div className="reception-actions reception-print-hidden">
      <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_LIST_PATH}>
        Payment requests
      </Link>
      {memberId !== null && (
        <Link className={buttonClassName('ghost', 'md')} to={memberSearchPath(memberId)}>
          Member profile
        </Link>
      )}
    </div>
  )

  if (id === null) {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Verify and Record Payment" aside={actions} />
        <EmptyState title="Payment not found" description="The payment link is not valid." />
      </div>
    )
  }
  if (payment.status === 'loading') {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Verify and Record Payment" aside={actions} />
        <Spinner label="Loading the payment" />
      </div>
    )
  }
  if (payment.status === 'error') {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Verify and Record Payment" aside={actions} />
        <ErrorState title="We could not load the payment" error={payment.error} onRetry={reload} />
      </div>
    )
  }

  const data = payment.data
  if (data.status === 'PENDING') {
    return <VerifyPayment key={data.id} payment={data} actions={actions} onDone={reload} />
  }
  if (data.status === 'SUCCESS' || data.status === 'REFUNDED') {
    return <PaymentConfirmed payment={data} actions={actions} memberId={memberId} />
  }
  return <PaymentFailed payment={data} actions={actions} />
}

/* F3-05 Verify and Record Payment, with S3-Mismatch */

type Touched = Partial<Record<keyof ConfirmErrors, boolean>>

const SERVER_FIELDS: Record<string, keyof ConfirmErrors> = {
  method: 'method',
  referenceCode: 'reference',
  paymentMethod: 'method',
  paymentReference: 'reference',
  amountReceived: 'amountReceived',
  note: 'note',
}

function VerifyPayment({ payment, actions, onDone }: { payment: StaffPayment; actions: ReactNode; onDone: () => void }) {
  const toast = useToast()
  const [form, setForm] = useState<ConfirmForm>(() => initialConfirmForm(payment.method))
  const [touched, setTouched] = useState<Touched>({})
  const [serverErrors, setServerErrors] = useState<ConfirmErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [mismatch, setMismatch] = useState<MismatchAmounts | null>(null)
  const [failOpen, setFailOpen] = useState(false)
  // Set synchronously, so a second submit in the same tick (double click, Enter + click) sends nothing.
  const submitting = useRef(false)

  // Null when the backend sent no usable amount: the form shows an error and both actions stay
  // disabled, so NaN never reaches the screen or the request.
  const amountDue = validAmountDue(payment.amount)
  const errors = validateConfirmForm(form, amountDue)
  const ready = canConfirm(form, amountDue)
  const check = amountCheck(amountDue, form.amountReceived)
  const difference = amountDifference(amountDue, form.amountReceived)
  const received = parsePaymentAmount(form.amountReceived)
  const toReview = isAmountToReview(form, amountDue)
  const discount = Number(payment.discount ?? 0)

  const change = <K extends keyof ConfirmForm>(key: K, value: ConfirmForm[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setFormError(null)
    if (key in serverErrors) {
      setServerErrors((current) => ({ ...current, [key]: undefined }))
    }
  }
  const touch = (key: keyof ConfirmErrors) => setTouched((current) => ({ ...current, [key]: true }))
  const fieldError = (key: keyof ConfirmErrors) => serverErrors[key] ?? (touched[key] ? errors[key] : undefined)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!ready || saving || submitting.current) return
    submitting.current = true
    setSaving(true)
    setFormError(null)
    try {
      const result = await receptionPaymentsApi.confirm(payment.id, toConfirmRequest(form))
      // Success is claimed only for the payment asked about, once the backend says SUCCESS with the right
      // amount. A SUCCESS with an amount problem reloads into the Needs review state, without the toast.
      if (result.id === payment.id && result.status === 'SUCCESS') {
        if (confirmedAmountProblem(result) === null) {
          toast.success('Payment confirmed', 'The payment is recorded. Check the service status on this page.')
        }
        onDone()
        return
      }
      submitting.current = false
      setSaving(false)
      setFormError(
        `The payment was not confirmed (status: ${paymentStatusLabel(result.id === payment.id ? result.status : null)}). Reload the payment before trying again.`,
      )
    } catch (failure) {
      submitting.current = false
      setSaving(false)
      if (isAmountMismatch(failure) && amountDue !== null) {
        setMismatch(mismatchAmounts(amountDue, form.amountReceived, failure))
        return
      }
      if (failure instanceof ApiError && failure.hasFieldErrors) {
        const renamed: Record<string, string> = {}
        for (const [key, message] of Object.entries(failure.fieldErrors)) {
          renamed[SERVER_FIELDS[key] ?? key] = message
        }
        const split = splitFieldErrors(renamed, Object.values(SERVER_FIELDS))
        setServerErrors(split.fields as ConfirmErrors)
        setFormError(split.formMessage ?? (Object.keys(split.fields).length === 0 ? failure.message : null))
        return
      }
      setFormError(getErrorMessage(failure))
    }
  }

  const backToVerification = () => {
    setMismatch(null)
    setForm((current) => ({ ...current, verified: false }))
  }

  if (mismatch) {
    return (
      <div className="page">
        <PaymentHero
          eyebrow={EYEBROW}
          title="Payment Needs Review"
          description="The payment cannot be confirmed yet."
          aside={actions}
        />
        <Card title="Amount mismatch" aria-label="Amount mismatch">
          <dl className="summary-list payment-rows">
            <div>
              <dt>Amount due</dt>
              <dd>{formatMoney(mismatch.amountDue)}</dd>
            </div>
            <div>
              <dt>Amount received</dt>
              <dd>{formatMoney(mismatch.amountReceived)}</dd>
            </div>
            <div>
              <dt>{mismatch.outstanding >= 0 ? 'Outstanding balance' : 'Received above the amount due'}</dt>
              <dd data-testid="outstanding">{formatMoney(Math.abs(mismatch.outstanding))}</dd>
            </div>
          </dl>
          {mismatch.message && <p className="notice notice--warning" role="alert">{mismatch.message}</p>}
          <p className="notice notice--info" role="status">
            Payment remains Pending payment. Confirm only when the exact amount is verified.
          </p>
          <div className="reception-actions">
            <Button onClick={backToVerification}>Return to verification</Button>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="page">
      <PaymentHero
        eyebrow={EYEBROW}
        title="Verify and Record Payment"
        description="Confirm only after the full amount has been received."
        aside={actions}
      />
      <div className="reception-grid">
        <Card title="Payment information">
          <form className="reception-form" aria-label="Verify and record payment" noValidate onSubmit={submit}>
            <Input
              label="Registration / member"
              value={[payment.itemCode ?? payment.code, memberLine(payment.memberFullName, payment.memberCode, payment.memberId)].join(' / ')}
              readOnly
            />
            <div className="reception-form__grid">
              <Select
                label="Payment method"
                required
                options={PAYMENT_METHOD_OPTIONS}
                value={form.method}
                disabled={saving}
                error={fieldError('method')}
                onChange={(event) => change('method', event.target.value)}
                onBlur={() => touch('method')}
              />
              <Input
                label="Amount received"
                required
                inputMode="decimal"
                placeholder={amountDue !== null ? String(amountDue) : undefined}
                value={form.amountReceived}
                disabled={saving}
                error={fieldError('amountReceived')}
                hint="Up to 2 decimals, for example 810000.50; whole VND may use grouping such as 810.000."
                onChange={(event) => change('amountReceived', event.target.value)}
                onBlur={() => touch('amountReceived')}
              />
            </div>
            <Input
              label="Reference code"
              required={referenceRequired(form.method)}
              maxLength={REFERENCE_MAX}
              value={form.reference}
              disabled={saving}
              error={fieldError('reference')}
              hint={referenceRequired(form.method) ? 'Bank, card slip or wallet transaction reference.' : 'Optional for cash.'}
              onChange={(event) => change('reference', event.target.value)}
              onBlur={() => touch('reference')}
            />
            <TextArea
              label="Note"
              maxLength={NOTE_MAX}
              value={form.note}
              disabled={saving}
              error={fieldError('note')}
              onChange={(event) => change('note', event.target.value)}
              onBlur={() => touch('note')}
            />
            <p className="notice notice--info">
              Confirm the bank record or cash receipt before marking Paid. The same bank transaction must not be recorded twice.
            </p>
            <Checkbox
              label={VERIFY_STATEMENT}
              checked={form.verified}
              disabled={saving}
              onChange={(event) => change('verified', event.target.checked)}
            />
            {amountDue === null && (
              <p className="notice notice--danger" role="alert">
                {INVALID_AMOUNT_DUE_MESSAGE}
              </p>
            )}
            {!ready && amountDue !== null && (
              <p className="payment-hint" id="confirm-hint">
                {toReview
                  ? 'For every method the amount received must equal the amount due. Use Amount does not match to review the payment.'
                  : 'Tick the checkbox and fill the required fields to confirm.'}
              </p>
            )}
            {formError && (
              <p className="notice notice--danger" role="alert">
                {formError}
              </p>
            )}
            <div className="reception-form__actions">
              <Button
                type="submit"
                loading={saving}
                disabled={!ready}
                aria-describedby={!ready && amountDue !== null ? 'confirm-hint' : undefined}
              >
                Confirm payment
              </Button>
              <Button
                variant="secondary"
                disabled={saving || amountDue === null || !toReview}
                onClick={() => {
                  if (amountDue !== null) setMismatch(mismatchAmounts(amountDue, form.amountReceived))
                }}
              >
                Amount does not match
              </Button>
              <Button variant="ghost" disabled={saving} onClick={() => setFailOpen(true)}>
                Mark as failed
              </Button>
            </div>
          </form>
        </Card>
        <Card title="Amount verification" aria-label="Amount verification">
          <dl className="summary-list payment-rows">
            <div>
              <dt>Service</dt>
              <dd>{payment.itemDescription ?? '-'}</dd>
            </div>
            {payment.originalPrice !== null && payment.originalPrice !== undefined && (
              <div>
                <dt>Base price</dt>
                <dd>{formatMoney(payment.originalPrice)}</dd>
              </div>
            )}
            {discount > 0 && (
              <div>
                <dt>Discount</dt>
                <dd>{formatMoney(discount)}</dd>
              </div>
            )}
            <div>
              <dt>Amount due</dt>
              <dd data-testid="amount-due">{amountDue === null ? '-' : formatMoney(amountDue)}</dd>
            </div>
            <div>
              <dt>Received</dt>
              <dd data-testid="received">{received === null ? '-' : formatMoney(received)}</dd>
            </div>
            <div>
              <dt>Difference</dt>
              <dd data-testid="difference">{difference === null ? '-' : formatMoney(difference)}</dd>
            </div>
          </dl>
          <div className="payment-check" role="status">
            <AmountCheckBadge check={check} />
          </div>
        </Card>
      </div>
      <FailPaymentDialog
        open={failOpen}
        payment={payment}
        onClose={() => setFailOpen(false)}
        onFailed={() => {
          setFailOpen(false)
          onDone()
        }}
      />
    </div>
  )
}

/** Same exact-amount rule as validateConfirmForm, including cash. */
function AmountCheckBadge({
  check,
}: {
  check: ReturnType<typeof amountCheck>
}) {
  switch (check) {
    case 'match':
      return <Badge tone="success">Ready to confirm</Badge>
    case 'short':
      return <Badge tone="warning">Amount is short - review needed</Badge>
    case 'over':
      return <Badge tone="warning">Amount is above the amount due - review needed</Badge>
    default:
      return <Badge tone="neutral">Waiting for the amount received</Badge>
  }
}

function FailPaymentDialog({
  open,
  payment,
  onClose,
  onFailed,
}: {
  open: boolean
  payment: StaffPayment
  onClose: () => void
  onFailed: () => void
}) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const trimmed = reason.trim()
  const reasonError = !trimmed ? 'Reason is required' : trimmed.length > FAIL_REASON_MAX ? `Reason must be at most ${FAIL_REASON_MAX} characters` : null

  const close = () => {
    if (saving) return
    setReason('')
    setTouched(false)
    setError(null)
    onClose()
  }

  const submit = async () => {
    setTouched(true)
    if (reasonError || saving) return
    setSaving(true)
    setError(null)
    try {
      await receptionPaymentsApi.fail(payment.id, { reason: trimmed })
      toast.success('Payment marked as failed', 'The request stays unpaid.')
      setSaving(false)
      setReason('')
      onFailed()
    } catch (failure) {
      setSaving(false)
      setError(getErrorMessage(failure))
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      dismissible={!saving}
      title="Mark payment as failed"
      description={`Payment ${payment.code} will be closed as failed. The registration or card stays unpaid.`}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button variant="danger" onClick={submit} loading={saving}>
            Mark as failed
          </Button>
        </>
      }
    >
      <TextArea
        label="Reason"
        required
        maxLength={FAIL_REASON_MAX}
        value={reason}
        disabled={saving}
        error={touched && reasonError ? reasonError : undefined}
        onChange={(event) => setReason(event.target.value)}
        onBlur={() => setTouched(true)}
      />
      {error && (
        <p className="notice notice--danger" role="alert">
          {error}
        </p>
      )}
    </Modal>
  )
}

/* F3-06 Payment Confirmed and Receipt */

function PaymentConfirmed({ payment, actions, memberId }: { payment: StaffPayment; actions: ReactNode; memberId: number | null }) {
  const refunded = payment.status === 'REFUNDED'
  const loadInvoice = useCallback(
    (signal: AbortSignal) => receptionPaymentsApi.invoiceByPayment(payment.id, { signal }),
    [payment.id],
  )
  const [invoice, reloadInvoice] = useResource(loadInvoice)
  const invoiceData = invoice.status === 'ready' ? invoice.data : null
  const discount = Number(invoiceData?.discountAmount ?? payment.discount ?? 0)
  // UI-31: a wrong amount is never shown as a success; the receipt cannot be printed until it is resolved.
  const problem = refunded ? null : confirmedAmountProblem(payment, invoiceData?.totalAmount)
  const basePrice = invoiceData?.subtotalAmount ?? payment.originalPrice

  return (
    <div className="page">
      <PaymentHero
        eyebrow={EYEBROW}
        title={refunded ? 'Payment Refunded' : problem ? 'Payment Needs Review' : 'Payment Confirmed and Receipt'}
        description={
          refunded
            ? 'This payment was refunded to the member.'
            : problem
              ? 'The amounts of this payment do not agree.'
              : 'Payment has been recorded. Check the service status below.'
        }
        aside={actions}
      />
      <div className="reception-grid">
        <Card
          title={invoiceData ? `Receipt ${invoiceData.invoiceNumber}` : 'Receipt'}
          aria-label="Payment receipt"
          className={problem ? undefined : 'payment-print-receipt'}
        >
          {problem ? (
            <>
              <StatusBadge status="NEEDS_REVIEW" label="Needs review" />
              <p className="notice notice--danger" role="alert">
                {problem}
              </p>
            </>
          ) : (
            <StatusBadge status={paymentStatusTone(payment.status)} label={paymentStatusLabel(payment.status)} />
          )}
          <dl className="summary-list payment-rows">
            <div>
              <dt>Issued to</dt>
              <dd>{memberLine(invoiceData?.memberFullName ?? payment.memberFullName, invoiceData?.memberCode ?? payment.memberCode, payment.memberId)}</dd>
            </div>
            <div>
              <dt>Service</dt>
              <dd>{payment.itemDescription ?? '-'}</dd>
            </div>
            {basePrice !== null && basePrice !== undefined && (
              <div>
                <dt>Base price</dt>
                <dd>{formatMoney(basePrice)}</dd>
              </div>
            )}
            {discount > 0 && (
              <div>
                <dt>Discount</dt>
                <dd>-{formatMoney(discount)}</dd>
              </div>
            )}
            <div className="summary-list__total">
              <dt>Amount paid</dt>
              <dd>{formatMoney(invoiceData?.totalAmount ?? payment.amount)}</dd>
            </div>
            <div>
              <dt>Payment / reference</dt>
              <dd>
                {[methodName(invoiceData?.paymentMethod ?? payment.method), invoiceData?.paymentReference ?? payment.paymentReference]
                  .filter(Boolean)
                  .join(' / ')}
              </dd>
            </div>
            <div>
              <dt>Received at</dt>
              <dd>{formatDateTime(invoiceData?.paymentTime ?? payment.paymentTime ?? payment.updatedAt)}</dd>
            </div>
          </dl>
          {invoice.status === 'loading' && <Spinner label="Loading the invoice" inline />}
          {invoice.status === 'error' && (
            <div className="notice notice--warning payment-invoice-error" role="alert">
              <span>The invoice could not be loaded: {getErrorMessage(invoice.error)}</span>
              <Button size="sm" variant="secondary" onClick={reloadInvoice}>
                Retry
              </Button>
            </div>
          )}
          <div className="reception-actions reception-print-hidden">
            <Button onClick={() => window.print()} disabled={invoice.status !== 'ready' || problem !== null}>Print receipt</Button>
            {problem === null && (
              <Link className={buttonClassName('secondary', 'md')} to={paymentReceiptPath(payment.id)}>
                Open receipt
              </Link>
            )}
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_LIST_PATH}>
              Back to payment requests
            </Link>
            {memberId !== null && !refunded && (
              <Link
                className={buttonClassName('ghost', 'md')}
                to={memberSearchPath(memberId)}
                state={{ paidReference: payment.code } satisfies AfterPaymentState}
              >
                Back to member profile
              </Link>
            )}
          </div>
        </Card>
        {payment.memberId !== null ? (
          <ServiceUpdated paymentId={payment.id} memberId={payment.memberId} />
        ) : (
          <Card title="Service updated">
            <p className="notice notice--warning">The payment has no member, so the service cannot be shown.</p>
          </Card>
        )}
      </div>
    </div>
  )
}

/** The card or package the payment activated, read again from the backend so ACTIVE is real. */
function ServiceUpdated({ paymentId, memberId }: { paymentId: number; memberId: number }) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      const [cards, registrations] = await Promise.all([
        receptionApi.memberCards(memberId, { signal }),
        receptionApi.memberRegistrations(memberId, { signal }),
      ])
      return findPaidItem(paymentId, cards, registrations)
    },
    [memberId, paymentId],
  )
  const [item, reload] = useResource(load)

  return (
    <Card title="Service updated" aria-label="Service updated">
      {item.status === 'loading' && <Spinner label="Loading the service" />}
      {item.status === 'error' && <ErrorState title="We could not load the service" error={item.error} onRetry={reload} />}
      {item.status === 'ready' && item.data === null && (
        <p className="notice notice--warning">No card or package of this member is linked to the payment.</p>
      )}
      {item.status === 'ready' && item.data !== null && (
        <>
          <StatusBadge status={item.data.item.status} />
          {item.data.kind === 'package' ? (
            <dl className="summary-list payment-rows">
              <div>
                <dt>Registration</dt>
                <dd>{item.data.item.registrationCode}</dd>
              </div>
              <div>
                <dt>Sport / format</dt>
                <dd>{[item.data.item.sportName, humanizeCode(item.data.item.trainingFormat)].join(' / ')}</dd>
              </div>
              <div>
                <dt>Validity</dt>
                <dd>
                  {formatDate(item.data.item.startDate)} to {formatDate(item.data.item.endDate)}
                </dd>
              </div>
              <div>
                <dt>Sessions</dt>
                <dd>
                  {item.data.item.remainingSessions === null ? 'No session limit' : `${item.data.item.remainingSessions} available`}
                </dd>
              </div>
            </dl>
          ) : (
            <dl className="summary-list payment-rows">
              <div>
                <dt>Card</dt>
                <dd>{item.data.item.cardCode}</dd>
              </div>
              <div>
                <dt>Tier</dt>
                <dd>{item.data.item.tierName}</dd>
              </div>
              <div>
                <dt>Validity</dt>
                <dd>
                  {formatDate(item.data.item.startDate)} to {formatDate(item.data.item.endDate)}
                </dd>
              </div>
            </dl>
          )}
          {item.data.item.status === 'ACTIVE' ? (
            <p className="notice notice--info">
              {item.data.kind === 'package'
                ? 'Package access is active. Class places are booked in Flow 2.'
                : 'The membership card is active and its discount applies to new packages.'}
            </p>
          ) : (
            <p className="notice notice--warning" role="alert">
              The {item.data.kind === 'package' ? 'package' : 'card'} is {humanizeCode(item.data.item.status).toLowerCase()}, not
              active. Reload the page or check the request.
            </p>
          )}
        </>
      )}
    </Card>
  )
}

/* Failed payment */

function PaymentFailed({ payment, actions }: { payment: StaffPayment; actions: ReactNode }) {
  return (
    <div className="page">
      <PaymentHero eyebrow={EYEBROW} title="Payment Failed" description="This payment was closed without being received." aside={actions} />
      <Card title={`Payment ${payment.code}`} aria-label="Failed payment">
        <StatusBadge status={paymentStatusTone(payment.status)} label={paymentStatusLabel(payment.status)} />
        <dl className="summary-list payment-rows">
          <div>
            <dt>Member</dt>
            <dd>{memberLine(payment.memberFullName, payment.memberCode, payment.memberId)}</dd>
          </div>
          <div>
            <dt>Service</dt>
            <dd>{payment.itemDescription ?? '-'}</dd>
          </div>
          <div>
            <dt>Amount</dt>
            <dd>{formatMoney(payment.amount)}</dd>
          </div>
          <div>
            <dt>Reason</dt>
            <dd>{payment.notes ?? '-'}</dd>
          </div>
        </dl>
        <p className="notice notice--info">The registration or card stays unpaid. The member can request it again.</p>
      </Card>
    </div>
  )
}
