import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { packagesApi } from '../../api/packagesApi'
import { Card, EmptyState, ErrorState, PageHeader, StatusBadge } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import { MEMBER_PATHS } from './dashboardModel'
import { useMemberResource } from './useMemberResource'

/**
 * Payment Summary (Figma F3-01), read only: GET /payments/{id} (BE-17). Built in FE-03 so that a
 * new registration lands on the summary of its payment. Changing the payment method and the full
 * instructions and status screen (F3-02, F3-03) are task FE-11.
 */
export function PaymentSummaryPage() {
  const { paymentId } = useParams()
  const id = Number(paymentId)
  const valid = Number.isInteger(id) && id > 0
  // Stable per payment id, as useMemberResource requires.
  const load = useCallback(
    (signal: AbortSignal) =>
      valid ? packagesApi.payment(id, { signal }) : Promise.reject(new Error('The payment link is not valid.')),
    [id, valid],
  )
  const [payment, reload] = useMemberResource(load)

  const header = (
    <PageHeader
      eyebrow="Payment"
      title="Payment Summary"
      description="Your request starts after the payment is confirmed."
      actions={
        <Link className={buttonClassName('secondary', 'md')} to={MEMBER_PATHS.membership}>
          My membership and packages
        </Link>
      }
    />
  )

  if (!valid) {
    return (
      <div className="page">
        {header}
        <EmptyState title="Payment not found" description="The payment link is not valid." />
      </div>
    )
  }
  if (payment.status === 'loading') {
    return (
      <div className="page">
        {header}
        <Spinner label="Loading the payment" />
      </div>
    )
  }
  if (payment.status === 'error') {
    return (
      <div className="page">
        {header}
        <ErrorState title="We could not load the payment" error={payment.error} onRetry={reload} />
      </div>
    )
  }

  const data = payment.data
  const discount = Number(data.discount ?? 0)
  const instructions = data.bankTransferInstructions
  return (
    <div className="page">
      {header}
      <div className="payment-summary">
        <Card title={data.itemDescription ?? `Payment ${data.code}`} aria-label="Payment summary">
          <dl className="summary-list">
            <div>
              <dt>Payment reference</dt>
              <dd>{data.code}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={data.status} />
              </dd>
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
                <dd>-{formatMoney(discount)}</dd>
              </div>
            )}
            <div className="summary-list__total">
              <dt>Amount payable</dt>
              <dd data-testid="payment-amount">{formatMoney(data.amount)}</dd>
            </div>
            <div>
              <dt>Payment method</dt>
              <dd>{humanizeCode(data.method)}</dd>
            </div>
            <div>
              <dt>Requested</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
            </div>
          </dl>
          {data.status === 'PENDING' && (
            <p className="notice notice--info">
              Payment must be verified before your package or card is activated. Pay by transfer with the details
              below, or at the front desk.
            </p>
          )}
        </Card>
        {instructions && (
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
                <dd>{instructions.accountNumber ?? '-'}</dd>
              </div>
              <div>
                <dt>Transfer content</dt>
                <dd>{instructions.transferContent ?? data.code}</dd>
              </div>
            </dl>
          </Card>
        )}
      </div>
    </div>
  )
}
