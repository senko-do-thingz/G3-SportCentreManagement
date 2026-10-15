import { useCallback, useEffect, type ChangeEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { paymentsApi, type Payment } from '../../api/paymentsApi'
import { Card, EmptyState, PageHeader, Select, StatusBadge, Table, type TableColumn } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { formatDate } from '../../utils/format'
import { MEMBER_PATHS } from './dashboardModel'
import {
  formatPaymentMoney,
  historyPageFromParam,
  historySizeFromParam,
  methodLabel,
  PAYMENT_PATHS,
  paymentStatusLabel,
  paymentStatusToneCode,
  STATUS_FILTER_OPTIONS,
  statusFilterFromParam,
} from './paymentsModel'
import { useMemberResource } from './useMemberResource'

const COLUMNS: TableColumn<Payment>[] = [
  {
    key: 'reference',
    header: 'Reference / Service',
    render: (payment) => (
      <span className="payment-row__reference">
        <strong>{payment.invoiceNumber ?? payment.code}</strong>
        <span>{payment.itemDescription ?? '-'}</span>
      </span>
    ),
  },
  { key: 'date', header: 'Date', render: (payment) => formatDate(payment.paymentTime ?? payment.createdAt) },
  { key: 'amount', header: 'Amount', render: (payment) => formatPaymentMoney(payment.amount) },
  { key: 'method', header: 'Method', render: (payment) => methodLabel(payment.method) },
  {
    key: 'status',
    header: 'Status',
    render: (payment) => (
      <StatusBadge status={paymentStatusToneCode(payment.status)} label={paymentStatusLabel(payment.status)} />
    ),
  },
  { key: 'action', header: 'Action', render: (payment) => <PaymentAction payment={payment} /> },
]

/** Check status for a pending payment, View receipt once an invoice exists, otherwise View details. */
function PaymentAction({ payment }: { payment: Payment }) {
  const label = payment.code
  if (payment.status === 'PENDING') {
    return (
      <Link
        className={buttonClassName('secondary', 'sm')}
        to={PAYMENT_PATHS.status(payment.id)}
        aria-label={`Check status of ${label}`}
      >
        Check status
      </Link>
    )
  }
  if (payment.invoiceId) {
    return (
      <Link
        className={buttonClassName('secondary', 'sm')}
        to={PAYMENT_PATHS.receipt(payment.invoiceId)}
        aria-label={`View receipt of ${label}`}
      >
        View receipt
      </Link>
    )
  }
  return (
    <Link className={buttonClassName('ghost', 'sm')} to={PAYMENT_PATHS.summary(payment.id)} aria-label={`View details of ${label}`}>
      View details
    </Link>
  )
}

/**
 * Server-paged history. Status, zero-based page and size survive reload and back/forward.
 */
export function MyPaymentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = statusFilterFromParam(searchParams.get('status'))
  const page = historyPageFromParam(searchParams.get('page'))
  const size = historySizeFromParam(searchParams.get('size'))
  const load = useCallback((signal: AbortSignal) => paymentsApi.myPayments({ status, page, size, signal }), [status, page, size])
  const [payments, reload] = useMemberResource(load)
  const data = payments.status === 'ready' ? payments.data : null
  const lastPage = data ? Math.max(0, data.totalPages - 1) : page
  const correctedPage = Math.min(page, lastPage)
  const correctingPage = correctedPage !== page

  // Replace malformed links and pages removed since the last visit without adding a history entry.
  useEffect(() => {
    const next = new URLSearchParams(searchParams)
    if (status) next.set('status', status)
    else next.delete('status')
    next.set('page', String(correctedPage))
    next.set('size', String(size))
    if (next.toString() !== searchParams.toString()) setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, status, correctedPage, size])

  function handleStatusChange(event: ChangeEvent<HTMLSelectElement>) {
    const next = new URLSearchParams(searchParams)
    if (event.target.value) {
      next.set('status', event.target.value)
    } else {
      next.delete('status')
    }
    next.set('page', '0')
    setSearchParams(next)
  }

  function handlePageChange(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  // Filtering and ordering belong to BE-20; changing one page locally invalidates its totals.
  const rows = data && !correctingPage ? data.content : []
  const filtered = status !== ''

  return (
    <div className="page">
      <PageHeader
        eyebrow="Member / Payments"
        title="My Payments and Receipts"
        description="Track payments and open receipts for confirmed transactions."
      />
      <Card
        title="Payment history"
        aria-label="Payment history"
        actions={
          <Select
            label="Status"
            className="payment-filter"
            value={status}
            onChange={handleStatusChange}
            options={STATUS_FILTER_OPTIONS}
            placeholder="All statuses"
          />
        }
      >
        {data && !correctingPage && data.totalElements === 0 && !filtered ? (
          <EmptyState
            title="No payments yet"
            description="Payments appear here after you request a membership card or a sport package."
            action={
              <Link className={buttonClassName('primary', 'md')} to={MEMBER_PATHS.packages}>
                Browse sport packages
              </Link>
            }
          />
        ) : (
          <Table
            caption="Payment history"
            columns={COLUMNS}
            rows={rows}
            rowKey={(payment) => payment.id}
            loading={payments.status === 'loading' || correctingPage}
            error={payments.status === 'error' ? payments.error : undefined}
            onRetry={reload}
            emptyTitle={`No ${paymentStatusLabel(status).toLowerCase()} payments`}
            emptyDescription="Choose another status to see more payments."
            pagination={data && !correctingPage ? {
              page: data.page,
              pageSize: data.size,
              totalItems: data.totalElements,
              onPageChange: handlePageChange,
            } : undefined}
          />
        )}
        <div className="notice notice--info payment-summary__note">
          <p>Pending payments do not activate a card, package or single visit.</p>
          <p>Receipts are available after reception has confirmed the payment.</p>
        </div>
      </Card>
    </div>
  )
}
