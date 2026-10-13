import { useCallback, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { paymentsApi } from '../../api/paymentsApi'
import { receptionApi } from '../../api/receptionApi'
import { Button, Card, EmptyState, ErrorState, PageHeader, Select, StatusBadge, useToast } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import { useResource } from '../../hooks/useResource'
import {
  confirmCheck,
  isPaymentMethod,
  memberSearchPath,
  PAYMENT_METHOD_OPTIONS,
  positiveId,
  registerPath,
  type AfterPaymentState,
  type ConfirmCheck,
  type PaymentItemKind,
} from './receptionModel'

/**
 * Payment confirmation and receipt for the front desk, reached from a pending online request
 * (F1-13 Payment Handoff, F1-14 pending tab) and after a desk sale ("View receipt").
 * GET /payments/{id} shows the payment. For a PENDING payment opened with its item
 * (?item=card|package&itemId=), "Confirm payment" calls PUT /membership-cards/{id}/activate or
 * PUT /packages/registrations/{id}/activate with the method actually used, which sets the payment to
 * SUCCESS and activates the request. The item from the URL is checked first: it is loaded from the
 * member's cards or registrations and must carry this payment id and still wait for payment. FE-12 (F3-04 to F3-07) extends this screen with transfer
 * verification, invoice printing and refunds.
 */
export function ReceptionPaymentPage() {
  const { paymentId } = useParams()
  const [params] = useSearchParams()
  const id = positiveId(paymentId)
  const itemParam = params.get('item')
  const itemKind: PaymentItemKind | null = itemParam === 'card' || itemParam === 'package' ? itemParam : null
  const itemId = positiveId(params.get('itemId'))
  const memberId = positiveId(params.get('memberId'))

  const load = useCallback(
    (signal: AbortSignal) =>
      id !== null ? paymentsApi.payment(id, { signal }) : Promise.reject(new Error('The payment link is not valid.')),
    [id],
  )
  const [payment, reload] = useResource(load)

  const back = (
    <div className="reception-actions">
      <Link className={buttonClassName('secondary', 'md')} to={registerPath({ tab: 'pending' })}>
        Pending requests
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
        <PageHeader eyebrow="Payment" title="Payment confirmation" actions={back} />
        <EmptyState title="Payment not found" description="The payment link is not valid." />
      </div>
    )
  }
  if (payment.status === 'loading') {
    return (
      <div className="page">
        <PageHeader eyebrow="Payment" title="Payment confirmation" actions={back} />
        <Spinner label="Loading the payment" />
      </div>
    )
  }
  if (payment.status === 'error') {
    return (
      <div className="page">
        <PageHeader eyebrow="Payment" title="Payment confirmation" actions={back} />
        <ErrorState title="We could not load the payment" error={payment.error} onRetry={reload} />
      </div>
    )
  }

  const data = payment.data
  const paid = data.status === 'SUCCESS'
  const discount = Number(data.discount ?? 0)
  return (
    <div className="page">
      <PageHeader
        eyebrow="Payment"
        title={paid ? 'Payment receipt' : 'Payment confirmation'}
        description={paid ? 'The payment is recorded.' : 'Confirm the payment only after the money is received.'}
        actions={back}
      />
      <div className="payment-summary">
        <Card title={data.itemDescription ?? `Payment ${data.code}`} aria-label="Payment details">
          <dl className="summary-list">
            <div>
              <dt>Payment reference</dt>
              <dd>{data.code}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={data.status} label={paid ? 'Paid' : undefined} />
              </dd>
            </div>
            <div>
              <dt>Member</dt>
              <dd>{data.memberCode ?? (data.memberId !== null ? `Member #${data.memberId}` : '-')}</dd>
            </div>
            {data.originalPrice !== null && (
              <div>
                <dt>Original price</dt>
                <dd>{formatMoney(data.originalPrice)}</dd>
              </div>
            )}
            {discount > 0 && (
              <div>
                <dt>Discount</dt>
                <dd>{formatMoney(discount)}</dd>
              </div>
            )}
            <div className="summary-list__total">
              <dt>{paid ? 'Amount paid' : 'Amount payable'}</dt>
              <dd>{formatMoney(data.amount)}</dd>
            </div>
            <div>
              <dt>Payment method</dt>
              <dd>{humanizeCode(data.method)}</dd>
            </div>
            <div>
              <dt>{paid ? 'Paid at' : 'Requested'}</dt>
              <dd>{formatDateTime(paid ? (data.paymentTime ?? data.createdAt) : data.createdAt)}</dd>
            </div>
          </dl>
          {paid && (
            <div className="reception-actions reception-print-hidden">
              <Button variant="secondary" onClick={() => window.print()}>
                Print receipt
              </Button>
            </div>
          )}
        </Card>
        {data.status === 'PENDING' &&
          (itemKind && itemId !== null && memberId !== null ? (
            <ConfirmPaymentCard
              key={data.id}
              paymentId={data.id}
              memberId={memberId}
              kind={itemKind}
              itemId={itemId}
              defaultMethod={data.method}
              amount={data.amount}
              onConfirmed={reload}
            />
          ) : (
            <Card title="Confirm payment">
              <p className="notice notice--warning">
                Open this payment from the pending requests list or the member profile to confirm it, so the right request is
                checked and activated.
              </p>
            </Card>
          ))}
        {paid && memberId !== null && (
          <Card title="Next step">
            <p className="card__intro">The request is active. Go back to the profile to continue with the member.</p>
            <Link
              className={buttonClassName('primary', 'md')}
              to={memberSearchPath(memberId)}
              state={{ paidReference: data.code } satisfies AfterPaymentState}
            >
              Back to member profile
            </Link>
          </Card>
        )}
      </div>
    </div>
  )
}

const CHECK_MESSAGES: Record<Exclude<ConfirmCheck, 'ok'>, string> = {
  'not-found': 'This request was not found for the member. Open it again from the pending requests list.',
  'other-payment': 'This request belongs to another payment. Open it again from the pending requests list.',
  'not-pending': 'This request does not wait for payment any more. Nothing to confirm.',
}

function ConfirmPaymentCard({
  paymentId,
  memberId,
  kind,
  itemId,
  defaultMethod,
  amount,
  onConfirmed,
}: {
  paymentId: number
  memberId: number
  kind: PaymentItemKind
  itemId: number
  defaultMethod: string | null
  amount: number | string
  onConfirmed: () => void
}) {
  const toast = useToast()
  const [method, setMethod] = useState<string>(defaultMethod && isPaymentMethod(defaultMethod) ? defaultMethod : 'CASH')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const loadItem = useCallback(
    async (signal: AbortSignal) => {
      const items =
        kind === 'card'
          ? await receptionApi.memberCards(memberId, { signal })
          : await receptionApi.memberRegistrations(memberId, { signal })
      return confirmCheck(
        items.find((item) => item.id === itemId),
        paymentId,
      )
    },
    [kind, memberId, itemId, paymentId],
  )
  const [check, reloadCheck] = useResource(loadItem)

  if (check.status === 'loading') {
    return (
      <Card title="Confirm payment" aria-label="Confirm payment">
        <Spinner label="Checking the request" />
      </Card>
    )
  }
  if (check.status === 'error') {
    return (
      <Card title="Confirm payment" aria-label="Confirm payment">
        <ErrorState title="We could not check the request" error={check.error} onRetry={reloadCheck} />
      </Card>
    )
  }
  const mismatch = check.data === 'ok' ? null : CHECK_MESSAGES[check.data]

  const confirm = async () => {
    if (mismatch) {
      return
    }
    if (!isPaymentMethod(method)) {
      setError('Choose a payment method')
      return
    }
    setSaving(true)
    setError(null)
    try {
      if (kind === 'card') {
        await receptionApi.activateCard(itemId, method)
      } else {
        await receptionApi.activateRegistration(itemId, method)
      }
      toast.success('Payment confirmed', 'The request is active now.')
      onConfirmed()
    } catch (failure) {
      setError(getErrorMessage(failure))
      setSaving(false)
    }
  }

  return (
    <Card title="Confirm payment" aria-label="Confirm payment">
      <Select
        label="Method actually used"
        options={PAYMENT_METHOD_OPTIONS}
        value={method}
        disabled={saving}
        onChange={(event) => setMethod(event.target.value)}
      />
      <p className="notice notice--info">
        Take {formatMoney(amount)} or check the transfer first. Confirming activates the {kind === 'card' ? 'membership card' : 'sport package'}{' '}
        and issues the receipt.
      </p>
      {mismatch && (
        <p className="notice notice--warning" role="alert">
          {mismatch}
        </p>
      )}
      {error && (
        <p className="notice notice--danger" role="alert">
          {error}
        </p>
      )}
      <Button onClick={confirm} loading={saving} disabled={mismatch !== null}>
        Confirm payment
      </Button>
    </Card>
  )
}
