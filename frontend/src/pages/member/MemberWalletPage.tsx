import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { WALLET_ENTRY_LABELS, walletApi, type WalletEntry, type WalletOverview } from '../../api/walletApi'
import { Badge, Card, EmptyState, ErrorState, Notice, Spinner, StatCard, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'
import {
  MemberCommerceCard,
  MemberCommerceHeader,
  MemberCommerceHero,
  MemberCommerceQuickActions,
} from './MemberCommerceComponents'
import { PAYMENT_PATHS } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

/** MW-03: wallet overview. Every amount comes from the backend ledger; nothing is summed in the browser. */
export function MemberWalletPage() {
  const load = useCallback((signal: AbortSignal) => walletApi.overview(signal), [])
  const [resource, reload] = useMemberResource(load)
  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="Member Workspace"
        title="Wallet and"
        highlight="Payments"
        description="Manage balance, top ups, payment requests and recent transactions."
        actions={
          <>
            <Link className={buttonClassName()} to="/member/wallet/topup">
              Top up wallet
            </Link>
            <Link className={buttonClassName('secondary')} to="/member/wallet/withdraw">
              Withdraw
            </Link>
            <Link className={buttonClassName('secondary')} to="/member/wallet/history">
              All activity
            </Link>
          </>
        }
      />
      <MemberCommerceHero
        kicker="SPORTIFY MEMBER WALLET"
        title="PAY EASILY. STAY ACTIVE."
        highlight="TOP UP, PAY, AND TRACK."
        description="A secure and convenient way to manage all your Sportify payments."
        actionLabel="Top up now"
        actionHref="/member/wallet/topup"
      />
      {resource.status === 'loading' && <Spinner label="Loading your wallet" />}
      {resource.status === 'error' && (
        <ErrorState title="We could not load your wallet" error={resource.error} onRetry={reload} />
      )}
      {resource.status === 'ready' && <WalletContent wallet={resource.data} />}
    </div>
  )
}

function WalletContent({ wallet }: { wallet: WalletOverview }) {
  const hasDemo = wallet.demoAvailableBalance > 0 || wallet.demoReservedBalance > 0
  const quickActions = [
    {
      icon: 'T',
      title: 'Top up wallet',
      desc: 'Add funds to your wallet balance request',
      href: '/member/wallet/topup',
    },
    {
      icon: 'P',
      title: 'Pay a request',
      desc: 'Settle pending payment requests and cards',
      href: PAYMENT_PATHS.list,
    },
    {
      icon: 'W',
      title: 'Withdraw funds',
      desc: 'Transfer verified balance to your bank account',
      href: '/member/wallet/withdraw',
    },
    {
      icon: 'H',
      title: 'View transaction history',
      desc: 'Check all your balance ledger entries and source records',
      href: '/member/wallet/history',
    },
  ]

  return (
    <>
      <div className="wallet-stats">
        <StatCard
          label="Available balance"
          value={formatMoney(wallet.availableBalance, wallet.currency)}
          hint="Staff-verified money you can spend"
        />
        <StatCard
          label="Held for withdrawal"
          value={formatMoney(wallet.reservedBalance, wallet.currency)}
          hint="Not spendable while a withdrawal is open"
        />
        <StatCard
          label="Demo balance (not real money)"
          value={formatMoney(wallet.demoAvailableBalance, wallet.currency)}
          hint="Demonstration funds only"
        />
      </div>
      <Notice tone="info">{wallet.notice}</Notice>
      {hasDemo && (
        <Notice tone="warning">
          You have demo funds. They cannot pay for real services or be withdrawn to a bank.
        </Notice>
      )}

      <div className="member-commerce-grid">
        <div className="member-commerce-column">
          <Card title={`Recent activity (${wallet.activityCount})`}>
            {wallet.recentActivity.length === 0 ? (
              <EmptyState
                title="No wallet activity yet"
                description="Your top-ups, wallet payments and withdrawals will appear here."
              />
            ) : (
              <WalletActivityTable entries={wallet.recentActivity} />
            )}
            <p className="health-meta">Last balance update {formatDateTime(wallet.updatedAt)}</p>
          </Card>
        </div>

        <div className="member-commerce-column">
          <MemberCommerceCard title="Quick Actions">
            <MemberCommerceQuickActions actions={quickActions} />
          </MemberCommerceCard>

          <MemberCommerceCard title="Payment Tips">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--info" aria-hidden="true">
                  Tip
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Use your wallet balance for faster, frictionless bookings and package renewals.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--warning" aria-hidden="true">
                  Note
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Top-up requests are credited only after front desk reception confirms your bank transfer note.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--neutral" aria-hidden="true">
                  Safety
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Withdrawal amounts are held safely until reviewed and settled to your verified bank account.
                </p>
              </div>
            </div>
          </MemberCommerceCard>
        </div>
      </div>
    </>
  )
}

export function WalletActivityTable({ entries }: { entries: WalletEntry[] }) {
  return (
    <div className="table-scroll">
      <table className="table">
        <caption>Wallet activity, newest first</caption>
        <thead>
          <tr>
            <th>Date and time</th>
            <th>Activity</th>
            <th>Source</th>
            <th>Amount</th>
            <th>Available after</th>
            <th>Held after</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={entry.id}>
              <td>{formatDateTime(entry.createdAt)}</td>
              <td>
                {WALLET_ENTRY_LABELS[entry.entryType]}{' '}
                {entry.pool === 'SIMULATED' && <Badge tone="warning">Demo</Badge>}
                <div className="health-meta">{entry.description}</div>
              </td>
              <td>
                {entry.sourceType} #{entry.sourceId}
              </td>
              <td
                className={
                  entry.direction === 'CREDIT'
                    ? 'wallet-amount wallet-amount--in'
                    : 'wallet-amount wallet-amount--out'
                }
              >
                {entry.direction === 'CREDIT' ? '+' : '-'}
                {formatMoney(entry.amount)}
              </td>
              <td>{formatMoney(entry.availableAfter)}</td>
              <td>{formatMoney(entry.reservedAfter)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
