import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { walletHistoryApi, type WalletActivityRow } from '../../api/walletApi'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Notice,
  Select,
  Spinner,
  buttonClassName,
} from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'
import {
  MemberCommerceCard,
  MemberCommerceHeader,
  MemberCommerceHero,
} from './MemberCommerceComponents'
import { PAYMENT_PATHS } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

const TYPES = [
  { value: '', label: 'All activity' },
  { value: 'TOPUP', label: 'Top-ups' },
  { value: 'WALLET_PAYMENT', label: 'Wallet payments' },
  { value: 'WITHDRAWAL', label: 'Withdrawals' },
  { value: 'COMPENSATION', label: 'Compensation' },
]

const STATUSES = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'PAID', label: 'Paid' },
  { value: 'APPROVED', label: 'Approved (not paid yet)' },
  { value: 'COMPLETED', label: 'Paid out' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

const TYPE_LABELS: Record<WalletActivityRow['type'], string> = {
  TOPUP: 'Top-up',
  WALLET_PAYMENT: 'Wallet payment',
  WITHDRAWAL: 'Withdrawal',
  COMPENSATION: 'Compensation',
}

const STATUS_TEXT: Record<string, string> = {
  PENDING: 'Pending - not credited',
  CONFIRMED: 'Confirmed',
  PAID: 'Paid',
  APPROVED: 'Approved - not paid yet',
  COMPLETED: 'Paid out',
  SIMULATED_COMPLETED: 'Demo completed (no real transfer)',
  REJECTED: 'Rejected',
  FAILED: 'Failed - funds released',
  CANCELLED: 'Cancelled',
  CREDITED: 'Credited',
}

const PAGE_SIZE = 10

/** MW-08: the Member's own wallet activity. Statuses and balances come from the server; nothing is summed here. */
export function WalletHistoryPage() {
  const [type, setType] = useState('')
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<string | null>(null)
  const load = useCallback(
    (signal: AbortSignal) =>
      walletHistoryApi.history({ type, status, from, to, page, size: PAGE_SIZE }, signal),
    [type, status, from, to, page]
  )
  const [resource, reload] = useMemberResource(load)
  const reset = (apply: () => void) => {
    apply()
    setPage(0)
    setOpen(null)
  }

  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="Member Workspace"
        title="Wallet"
        highlight="Activity"
        description="Top-ups, wallet payments and withdrawals. Cash, bank and QR payments are in My Payments."
        actions={
          <>
            <Link className={buttonClassName('secondary')} to={PAYMENT_PATHS.list}>
              Payments and receipts
            </Link>
            <Link className={buttonClassName('secondary')} to="/member/wallet">
              Back to My Wallet
            </Link>
          </>
        }
      />
      <MemberCommerceHero
        kicker="SPORTIFY MEMBER WALLET"
        title="COMPLETE TRANSACTION HISTORY."
        highlight="EVERY ENTRY RECORDED."
        description="All top-ups, debit payments, and withdrawal records verified against the official center ledger."
      />
      <MemberCommerceCard title="Filter Activity">
        <div className="wallet-filters">
          <Select
            label="Type"
            value={type}
            options={TYPES}
            onChange={(e) => reset(() => setType(e.target.value))}
          />
          <Select
            label="Status"
            value={status}
            options={STATUSES}
            onChange={(e) => reset(() => setStatus(e.target.value))}
          />
          <Input
            label="From"
            type="date"
            value={from}
            onChange={(e) => reset(() => setFrom(e.target.value))}
          />
          <Input
            label="To"
            type="date"
            value={to}
            onChange={(e) => reset(() => setTo(e.target.value))}
          />
        </div>
      </MemberCommerceCard>
      {resource.status === 'loading' && <Spinner label="Loading wallet activity" />}
      {resource.status === 'error' && (
        <ErrorState
          title="We could not load your wallet activity"
          error={resource.error}
          onRetry={reload}
        />
      )}
      {resource.status === 'ready' && (
        <Card title={`Activity (${resource.data.page.totalElements})`}>
          <p className="health-meta" style={{ marginTop: 0, marginBottom: 'var(--space-3)' }}>
            Available {formatMoney(resource.data.availableBalance)}, held for withdrawal{' '}
            {formatMoney(resource.data.reservedBalance)}.
            {resource.data.reconciled ? ' Balance matches the wallet ledger.' : ''}
          </p>
          {!resource.data.reconciled && (
            <Notice tone="danger">The balance does not match the ledger. Please contact reception.</Notice>
          )}
          {resource.data.page.content.length === 0 ? (
            <EmptyState
              title="No wallet activity found"
              description="Change the filters or check back later."
            />
          ) : (
            <div className="table-scroll">
              <table className="table">
                <caption>Wallet activity, newest first</caption>
                <thead>
                  <tr>
                    <th>Date and time</th>
                    <th>Reference</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Balance after</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {resource.data.page.content.map((row) => {
                    const key = `${row.type}-${row.id}`
                    return [
                      <tr key={key}>
                        <td>{formatDateTime(row.createdAt)}</td>
                        <td>
                          <code>{row.reference}</code>
                        </td>
                        <td>
                          {TYPE_LABELS[row.type]}{' '}
                          {row.pool === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}
                        </td>
                        <td
                          className={
                            row.direction === 'CREDIT'
                              ? 'wallet-amount wallet-amount--in'
                              : 'wallet-amount wallet-amount--out'
                          }
                        >
                          {row.direction === 'CREDIT' ? '+' : row.direction === 'DEBIT' ? '-' : ''}
                          {formatMoney(row.amount)}
                        </td>
                        <td>
                          <Badge
                            tone={
                              row.status === 'PENDING' || row.status === 'APPROVED'
                                ? 'warning'
                                : row.status === 'REJECTED' ||
                                  row.status === 'FAILED' ||
                                  row.status === 'CANCELLED'
                                ? 'neutral'
                                : 'success'
                            }
                          >
                            {STATUS_TEXT[row.status] ?? row.status}
                          </Badge>
                        </td>
                        <td>{row.availableAfter === null ? '-' : formatMoney(row.availableAfter)}</td>
                        <td>
                          <Button
                            size="sm"
                            variant="secondary"
                            aria-expanded={open === key}
                            onClick={() => setOpen(open === key ? null : key)}
                          >
                            {open === key ? 'Hide' : 'View'}
                          </Button>
                        </td>
                      </tr>,
                      open === key && (
                        <tr key={`${key}-detail`} className="wallet-detail-row">
                          <td colSpan={7}>
                            <dl className="browse-facts">
                              <div>
                                <dt>Description</dt>
                                <dd>{row.detail}</dd>
                              </div>
                              <div>
                                <dt>Money source</dt>
                                <dd>
                                  {row.pool === 'SIMULATED'
                                    ? 'Demo funds (not real money)'
                                    : 'Real money'}
                                </dd>
                              </div>
                              {row.bankReference && (
                                <div>
                                  <dt>Bank reference</dt>
                                  <dd>{row.bankReference}</dd>
                                </div>
                              )}
                              {row.settledAt && (
                                <div>
                                  <dt>Settled at</dt>
                                  <dd>{formatDateTime(row.settledAt)}</dd>
                                </div>
                              )}
                              {row.paymentId && (
                                <div>
                                  <dt>Payment</dt>
                                  <dd>
                                    <Link to={PAYMENT_PATHS.summary(row.paymentId)}>
                                      Open payment and receipt
                                    </Link>
                                  </dd>
                                </div>
                              )}
                            </dl>
                          </td>
                        </tr>
                      ),
                    ]
                  })}
                </tbody>
              </table>
            </div>
          )}
          {resource.data.page.totalPages > 1 && (
            <div className="profile-form__actions" style={{ marginTop: 'var(--space-4)' }}>
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </Button>
              <span className="health-meta">
                Page {page + 1} of {resource.data.page.totalPages}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page + 1 >= resource.data.page.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
