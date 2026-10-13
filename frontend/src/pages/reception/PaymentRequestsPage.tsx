import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { receptionPaymentsApi, type StaffPayment } from '../../api/receptionPaymentsApi'
import {
  Button,
  Card,
  Input,
  PageHeader,
  Select,
  StatCard,
  StatusBadge,
  Table,
  type TableColumn,
} from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { useResource } from '../../hooks/useResource'
import { formatDateTime, formatMoney } from '../../utils/format'
import { receptionPaymentPath } from './receptionModel'
import {
  apiStatus,
  PAYMENT_PAGE_SIZE,
  PAYMENT_STATUS_FILTER_OPTIONS,
  paymentStatusLabel,
  paymentStatusTone,
  pendingTotal,
  QUERY_MAX,
  readPaymentListParams,
  REFUND_LIST_PATH,
  writePaymentListParams,
  type PaymentListParams,
} from './receptionPaymentsModel'

/**
 * Payment Requests (Figma F3-04) at /reception/payments: GET /payments?status=&query=&page=&size=
 * (BE-18) with a status filter, a member or reference search and paging, all kept in the URL.
 * A pending row opens Verify and Record Payment (F3-05); any other row opens its result.
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
  const loadRefundCount = useCallback(
    (signal: AbortSignal) => receptionPaymentsApi.pendingRefunds({ signal }).then((refunds) => refunds.length),
    [],
  )
  const [refundCount] = useResource(loadRefundCount)

  const update = (next: Partial<PaymentListParams>) => {
    setParams(writePaymentListParams({ ...filters, ...next }))
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    update({ query: draft.trim().slice(0, QUERY_MAX), page: 0 })
  }

  const rows = payments.status === 'ready' ? payments.data.content : []
  const total = payments.status === 'ready' ? payments.data.totalElements : null

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
      key: 'action',
      header: 'Action',
      render: (row) =>
        row.status === 'PENDING' ? (
          <Link
            className={buttonClassName('secondary', 'sm')}
            to={receptionPaymentPath(row.id)}
            aria-label={`Review payment ${row.itemCode ?? row.code}`}
          >
            Review payment
          </Link>
        ) : (
          <span className="payment-row-status">
            <StatusBadge status={paymentStatusTone(row.status)} label={paymentStatusLabel(row.status)} />
            <Link to={receptionPaymentPath(row.id)} aria-label={`Open payment ${row.itemCode ?? row.code}`}>
              Open
            </Link>
          </span>
        ),
    },
  ]

  return (
    <div className="page">
      <PageHeader
        eyebrow="Receptionist / Payments and reports"
        title="Payment Requests"
        description="Find a registration and match the payment to the correct member."
        actions={
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
          label="Refund requests"
          value={refundCount.status === 'ready' ? refundCount.data : '-'}
          hint={refundCount.status === 'error' ? 'Not available' : undefined}
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
