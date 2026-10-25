import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { receptionPaymentsApi, type StaffPayment } from '../../api/receptionPaymentsApi'
import { PaymentHero } from '../../components/PaymentHero'
import {
  Button,
  Card,
  Input,
  Select,
  StatCard,
  StatusBadge,
  Table,
  type TableColumn,
} from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { useResource } from '../../hooks/useResource'
import { formatDateTime } from '../../utils/format'
import { receptionPaymentPath } from './receptionModel'
import {
  apiStatus,
  formatPaymentMoney as formatMoney,
  needsReview,
  PAYMENT_PAGE_SIZE,
  PAYMENT_STATUS_FILTER_OPTIONS,
  pendingTotal,
  QUERY_MAX,
  queueStatus,
  readPaymentListParams,
  REFUND_LIST_PATH,
  writePaymentListParams,
  type PaymentListParams,
} from './receptionPaymentsModel'

/**
 * Payment Requests (Figma F3-04) at /reception/payments: GET /payments?status=&query=&page=&size=
 * (BE-18) with a status filter, a member or reference search and paging, all kept in the URL.
 * The backend sets the order (PENDING first); the page keeps it. Every row has one status badge
 * (Needs review for a pending payment with a recorded amount mismatch) and exactly one action:
 * Review payment for a pending row (F3-05), Open for any other row.
 */
export function PaymentRequestsPage() {
  const [params, setParams] = useSearchParams()
  const filters = readPaymentListParams(params)
  const { status, query, page } = filters
  const [draft, setDraft] = useState(query)
  const [draftFor, setDraftFor] = useState(query)
  // When the URL query changes (Back, a link), the search box follows it.
  if (draftFor !== query) {
    setDraftFor(query)
    setDraft(query)
  }

  const loadPayments = useCallback(
    (signal: AbortSignal) =>
      receptionPaymentsApi.payments({ status: apiStatus(status), query, page, size: PAYMENT_PAGE_SIZE, signal }),
    [status, query, page],
  )
  const [payments, reloadPayments] = useResource(loadPayments)

  const update = (next: Partial<PaymentListParams>) => {
    setParams(writePaymentListParams({ ...filters, ...next }))
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    update({ query: draft.trim().slice(0, QUERY_MAX), page: 0 })
  }

  const rows = payments.status === 'ready' ? payments.data.content : []
  const total = payments.status === 'ready' ? payments.data.totalElements : null
  const toReview = rows.filter(needsReview).length

  const columns: TableColumn<StaffPayment>[] = [
    {
      key: 'reference',
      header: 'Registration',
      render: (row) => (
        <>
          <span className="reception-cell__title">{row.itemCode ?? row.code}</span>
          {row.itemCode && <span className="reception-cell__meta">{row.code}</span>}
        </>
      ),
    },
    {
      key: 'member',
      header: 'Member',
      render: (row) => (
        <>
          <span className="reception-cell__title">{row.memberFullName ?? row.memberCode ?? '-'}</span>
          {row.memberFullName && row.memberCode && <span className="reception-cell__meta">{row.memberCode}</span>}
        </>
      ),
    },
    { key: 'service', header: 'Service', render: (row) => row.itemDescription ?? '-' },
    { key: 'amount', header: 'Amount due', render: (row) => formatMoney(row.amount) },
    { key: 'createdAt', header: 'Requested', render: (row) => formatDateTime(row.createdAt) },
    {
      key: 'status',
      header: 'Status',
      render: (row) => {
        const badge = queueStatus(row)
        return <StatusBadge status={badge.code} label={badge.label} />
      },
    },
    {
      key: 'action',
      header: 'Action',
      render: (row) =>
        row.status === 'PENDING' ? (
          <Link
            className={buttonClassName(needsReview(row) ? 'primary' : 'secondary', 'sm')}
            to={receptionPaymentPath(row.id)}
            aria-label={`Review payment ${row.itemCode ?? row.code}`}
          >
            Review payment
          </Link>
        ) : (
          <Link
            className={buttonClassName('ghost', 'sm')}
            to={receptionPaymentPath(row.id)}
            aria-label={`Open payment ${row.itemCode ?? row.code}`}
          >
            Open
          </Link>
        ),
    },
  ]

  return (
    <div className="page">
      <PaymentHero
        eyebrow="Receptionist / Payments and reports"
        title="Payment Requests"
        description="Find a registration and match the payment to the correct member."
        aside={
          <Link className={buttonClassName('secondary', 'md')} to={REFUND_LIST_PATH}>
            Refund requests
          </Link>
        }
      />
      <div className="payment-stats">
        <StatCard
          label={status === 'PENDING' ? 'Pending requests' : 'Requests found'}
          value={total === null ? '-' : total}
        />
        <StatCard label="Amount due on this page" value={formatMoney(pendingTotal(rows))} />
        <StatCard
          label="Needs review on this page"
          value={payments.status === 'ready' ? toReview : '-'}
          hint="Pending payments with a recorded amount mismatch. Confirm only when the full amount is verified."
        />
      </div>
      <Card title="Registrations awaiting payment">
        <form className="payment-filters" role="search" aria-label="Filter payment requests" onSubmit={onSearch}>
          <Input
            label="Search member or reference"
            value={draft}
            maxLength={QUERY_MAX}
            placeholder="Taylor Nguyen / REG-1041"
            onChange={(event) => setDraft(event.target.value)}
          />
          <Select
            label="Status"
            options={PAYMENT_STATUS_FILTER_OPTIONS}
            value={status}
            onChange={(event) => update({ status: event.target.value, page: 0 })}
          />
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>
        <Table
          caption="Payment requests"
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          loading={payments.status === 'loading'}
          error={payments.status === 'error' ? payments.error : undefined}
          onRetry={reloadPayments}
          emptyTitle="No payment requests"
          emptyDescription={query ? 'No payment matches this search.' : 'There is nothing to review for this status.'}
          pagination={
            payments.status === 'ready'
              ? {
                  page: payments.data.page,
                  pageSize: PAYMENT_PAGE_SIZE,
                  totalItems: payments.data.totalElements,
                  onPageChange: (next) => update({ page: next }),
                }
              : undefined
          }
        />
        <p className="notice notice--info payment-note">
          Open the existing registration from Flow 1 or Flow 2. Do not create it again.
        </p>
      </Card>
    </div>
  )
}
