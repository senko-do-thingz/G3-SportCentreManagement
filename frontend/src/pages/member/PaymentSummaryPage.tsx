import { useCallback, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { parseSessionReference } from '../../api/sessionIdentity'
import { getErrorMessage } from '../../api/errors'
import { memberApi, type MemberCard, type PackageRegistration } from '../../api/memberApi'
import { MEMBER_PAYMENT_METHODS, paymentsApi, type Payment } from '../../api/paymentsApi'
import { PaymentHero } from '../../components/PaymentHero'
import { PaymentWorkspace } from '../../components/PaymentWorkspace'
import { Button, Card, EmptyState, ErrorState, StatusBadge } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatDateTime } from '../../utils/format'
import { MEMBER_PATHS, trainingFormatLabel } from './dashboardModel'
import { PaymentNav } from './PaymentNav'
import {
  cardForPayment,
  discountPercent,
  formatPaymentMoney as formatMoney,
  isPending,
  DEFAULT_PAYMENT_METHOD,
  isMethodAvailable,
  isSimulatedSettlement,
  isSingleVisitRegistration,
  methodLabel,
  parseIdParam,
  PAYMENT_PATHS,
  paymentStatusLabel,
  paymentStatusToneCode,
  paymentSummaryNote,
  priceBreakdown,
  simulatedProvenanceLabel,
  registrationForPayment,
  validityRange,
  type PaymentItemKind,
} from './paymentsModel'
import { sessionsLabel } from './packagesModel'
import { useMemberResource } from './useMemberResource'
import { CancelPayment } from './PaymentCancellation'

const INVALID_LINK_MESSAGE = 'The payment link is not valid.'
const MISMATCH_MESSAGE = 'The payment returned does not match this link. Open it again from My payments.'
const EYEBROW = 'Member / Payments'

interface PaymentItems {
  registrations: PackageRegistration[]
  cards: MemberCard[]
}

/** The paid item is extra detail: a failed list call leaves it out instead of failing the page. */
function loadItems(signal: AbortSignal): Promise<PaymentItems> {
  return Promise.all([
    memberApi.myRegistrations({ signal }).catch(() => [] as PackageRegistration[]),
    memberApi.myCards({ signal }).catch(() => [] as MemberCard[]),
  ]).then(([registrations, cards]) => ({ registrations, cards }))
}

/**
 * Payment Summary (Figma F3-01) and Single Visit Payment Summary (S3-Single):
 * GET /payments/{id} for the item, original price, discount and total, the member's
 * registrations and cards for the item details, and the payment method choice
 * (PUT /payments/{id}/method) before "Continue to payment" opens the instructions and status
 * screen (F3-02).
 */
export function PaymentSummaryPage() {
  const { paymentId } = useParams()
  const id = parseIdParam(paymentId)
  if (id === null) {
    return (
      <PaymentWorkspace>
        <PaymentHero eyebrow={EYEBROW} title="Payment Summary" />
        <EmptyState
          title="Payment not found"
          description={INVALID_LINK_MESSAGE}
          action={
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>
              My payments
            </Link>
          }
        />
      </PaymentWorkspace>
    )
  }
  return <PaymentSummaryView key={id} paymentId={id} />
}

function PaymentSummaryView({ paymentId }: { paymentId: number }) {
  const location = useLocation()
  const selectedSession = parseSessionReference(new URLSearchParams(location.search).get('sessionId'))
  // Stable per payment id, as useMemberResource requires.
  const loadPayment = useCallback((signal: AbortSignal) => paymentsApi.payment(paymentId, { signal }), [paymentId])
  const [payment, reload] = useMemberResource(loadPayment)
  const [items] = useMemberResource(loadItems)

  if (payment.status !== 'ready') {
    return (
      <PaymentWorkspace>
        <div className="member-commerce">
          <PaymentNav paymentId={paymentId} />
          <PaymentHero eyebrow={EYEBROW} title="Payment Summary" />
          {payment.status === 'loading' ? (
            <Spinner label="Loading the payment" />
          ) : (
            <div>
              <ErrorState title="We could not load the payment" error={payment.error} onRetry={reload} />
              <div style={{ marginTop: 'var(--space-4)' }}>
                <Link className={buttonClassName('secondary')} to={PAYMENT_PATHS.list}>
                  Back to My payments
                </Link>
              </div>
            </div>
          )}
        </div>
      </PaymentWorkspace>
    )
  }

  const data = payment.data
  // The amounts shown must belong to the payment in the link, never to another one.
  if (data.id !== paymentId) {
    return (
      <PaymentWorkspace>
        <div className="member-commerce">
          <PaymentNav paymentId={paymentId} />
          <PaymentHero eyebrow={EYEBROW} title="Payment Summary" />
          <EmptyState
            title="Payment not found"
            description={MISMATCH_MESSAGE}
            action={
              <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>
                My payments
              </Link>
            }
          />
        </div>
      </PaymentWorkspace>
    )
  }
  const registrations = items.status === 'ready' ? items.data.registrations : []
  const cards = items.status === 'ready' ? items.data.cards : []
  const registration = registrationForPayment(registrations, data.id)
  const card = registration ? null : cardForPayment(cards, data.id)
  const singleVisit = isSingleVisitRegistration(registration)
  const kind: PaymentItemKind = singleVisit ? 'single-visit' : registration ? 'package' : card ? 'card' : 'unknown'
  const note = paymentSummaryNote(kind, priceBreakdown(data)?.discount ?? 0)

  return (
    <PaymentWorkspace>
      <div className="member-commerce">
        <PaymentNav paymentId={paymentId} />
        <PaymentHero
          eyebrow={EYEBROW}
          title={singleVisit ? 'Single Visit Payment Summary' : 'Payment Summary'}
          description={
            singleVisit
              ? 'Single visits use a separate price and do not receive a card discount.'
              : 'Review your request and card benefits before paying.'
          }
        />
        {selectedSession && <Link className={buttonClassName('secondary')} to={`/member/sessions/${selectedSession}`}>Return to selected session</Link>}
        <div className="payment-summary">
        <Card title={data.itemDescription ?? `Payment ${data.code}`} aria-label="Payment details">
          {registration?.totalSessions !== null && registration?.totalSessions !== undefined && (
            <p className="payment-summary__chip">{sessionsLabel(registration.totalSessions)}</p>
          )}
          <dl className="summary-list">
            <div>
              <dt>Payment reference</dt>
              <dd>{data.code}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={paymentStatusToneCode(data.status)} label={paymentStatusLabel(data.status, data.settlementType)} />
              </dd>
            </div>
            {isSimulatedSettlement(data) && (
              <div>
                <dt>Settlement provenance</dt>
                <dd>{simulatedProvenanceLabel(data)}</dd>
              </div>
            )}
            {isSimulatedSettlement(data) && data.referenceCode && (
              <div>
                <dt>Simulated transaction</dt>
                <dd>{data.referenceCode}</dd>
              </div>
            )}
            {registration && <RegistrationRows registration={registration} singleVisit={singleVisit} />}
            {card && <CardRows card={card} />}
            <div>
              <dt>Member</dt>
              <dd>{data.memberCode ?? '-'}</dd>
            </div>
            <div>
              <dt>Requested</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
            </div>
          </dl>
          {items.status === 'loading' && <Spinner label="Loading the item details" inline />}
          {note && (
            <div className="notice notice--info payment-summary__note">
              <p>{note}</p>
            </div>
          )}
        </Card>
        <OrderSummary payment={data} singleVisit={singleVisit} onStale={reload} />
        <CancelPayment payment={data} onChanged={reload} />
      </div>
      </div>
    </PaymentWorkspace>
  )
}

function RegistrationRows({ registration, singleVisit }: { registration: PackageRegistration; singleVisit: boolean }) {
  return (
    <>
      <div>
        <dt>Registration</dt>
        <dd>{registration.registrationCode}</dd>
      </div>
      {registration.trainingFormat && (
        <div>
          <dt>Training format</dt>
          <dd>{trainingFormatLabel(registration.trainingFormat)}</dd>
        </div>
      )}
      {singleVisit ? (
        <div>
          <dt>Visit date</dt>
          <dd>{formatDate(registration.startDate)}</dd>
        </div>
      ) : (
        <>
          <div>
            <dt>Package start date</dt>
            <dd>{formatDate(registration.startDate)}</dd>
          </div>
          <div>
            <dt>Package validity</dt>
            <dd>{validityRange(registration.startDate, registration.endDate)}</dd>
          </div>
        </>
      )}
    </>
  )
}

function CardRows({ card }: { card: MemberCard }) {
  return (
    <>
      <div>
        <dt>Card request</dt>
        <dd>{card.cardCode}</dd>
      </div>
      <div>
        <dt>Membership card</dt>
        <dd>{card.tierName}</dd>
      </div>
      <div>
        <dt>Card validity</dt>
        <dd>{validityRange(card.startDate, card.endDate)}</dd>
      </div>
    </>
  )
}

interface OrderSummaryProps {
  payment: Payment
  singleVisit: boolean
  /** Reloads the payment, for example after a 400 because it is no longer pending. */
  onStale: () => void
}

function OrderSummary({ payment, singleVisit, onStale }: OrderSummaryProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const pending = isPending(payment)
  // Bank transfer opens the VietQR instructions; Cash leaves the payment pending for reception.
  const [method, setMethod] = useState<string>(payment.method ?? DEFAULT_PAYMENT_METHOD)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const breakdown = priceBreakdown(payment)
  const discount = breakdown?.discount ?? 0
  const percent = breakdown ? discountPercent(breakdown.original, breakdown.discount) : null

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) {
      return
    }
    setError(null)
    if (method === payment.method) {
      navigate(`${PAYMENT_PATHS.status(payment.id)}${location.search}`)
      return
    }
    setSaving(true)
    try {
      await paymentsApi.updateMethod(payment.id, method)
      navigate(`${PAYMENT_PATHS.status(payment.id)}${location.search}`)
    } catch (caught) {
      setSaving(false)
      setError(getErrorMessage(caught))
    }
  }

  return (
    <Card title="Order summary" aria-label="Order summary">
      <dl className="summary-list">
        {breakdown && (
          <>
            <div>
              <dt>{singleVisit ? 'Price' : 'Original price'}</dt>
              <dd>{formatMoney(breakdown.original)}</dd>
            </div>
            <div>
              <dt>{percent !== null ? `Discount (${percent}%)` : singleVisit ? 'Card discount' : 'Discount'}</dt>
              <dd>
                {discount > 0 ? `-${formatMoney(discount)}` : singleVisit ? `${formatMoney(0)} - Single visits excluded` : formatMoney(0)}
              </dd>
            </div>
          </>
        )}
        <div className="summary-list__total">
          <dt>Total due</dt>
          <dd>{formatMoney(payment.amount)}</dd>
        </div>
      </dl>
      {!breakdown && (
        <p className="payment-methods__hint">Price details are not available for this payment. The total due is the amount to pay.</p>
      )}
      <p className="payment-summary__amount" data-testid="payment-amount">
        {formatMoney(payment.amount)}
      </p>

      {pending ? (
        <form className="payment-method-form" onSubmit={handleSubmit} noValidate>
          <fieldset className="payment-methods" disabled={saving}>
            <legend className="payment-methods__legend">Payment method</legend>
            {MEMBER_PAYMENT_METHODS.map((option) => {
              const available = isMethodAvailable(option)
              return (
                <label
                  key={option}
                  className={`payment-methods__option${available ? '' : ' payment-methods__option--disabled'}`}
                >
                  <input
                    type="radio"
                    name="paymentMethod"
                    value={option}
                    checked={method === option}
                    disabled={!available}
                    aria-describedby={available ? undefined : `payment-method-${option}-soon`}
                    onChange={() => setMethod(option)}
                  />
                  <span>{methodLabel(option)}</span>
                  {!available && (
                    // Hidden from the label name and read as the radio description instead.
                    <span className="payment-methods__soon" id={`payment-method-${option}-soon`} aria-hidden="true">
                      Not available yet
                    </span>
                  )}
                </label>
              )
            })}
          </fieldset>
          <p className="payment-methods__hint">
            {method === 'CASH'
              ? 'Pay at reception and provide your payment reference. The receptionist confirms after receiving the cash.'
              : 'The next screen shows your VietQR transfer QR. Reception confirms real transfers after checking the received funds.'}
          </p>
          {error && (
            <div className="notice notice--danger" role="alert">
              <p>{error}</p>
              <button type="button" className="link-button" onClick={onStale}>
                Reload the payment
              </button>
            </div>
          )}
          <Button type="submit" fullWidth loading={saving}>
            Continue to payment
          </Button>
        </form>
      ) : (
        <div className="payment-summary__actions">
          <p className="payment-methods__hint">
            {payment.status === 'SUCCESS' ? (isSimulatedSettlement(payment) ? 'Paid (Simulated) with' : 'Paid with') : 'Method'}: {methodLabel(payment.method)}
          </p>
          {payment.invoiceId ? (
            <Link className={buttonClassName('primary', 'md', true)} to={PAYMENT_PATHS.receipt(payment.invoiceId)}>
              View receipt
            </Link>
          ) : null}
          <Link className={buttonClassName('secondary', 'md', true)} to={PAYMENT_PATHS.status(payment.id)}>
            View payment status
          </Link>
        </div>
      )}
      <Link className={buttonClassName('ghost', 'md', true, 'no-print')} to={MEMBER_PATHS.membership}>
        My membership and packages
      </Link>
    </Card>
  )
}
