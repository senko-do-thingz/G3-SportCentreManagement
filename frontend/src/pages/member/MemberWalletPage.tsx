import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { WALLET_ENTRY_LABELS, walletApi, type WalletEntry, type WalletOverview } from '../../api/walletApi'
import { Badge, Card, EmptyState, ErrorState, Notice, PageHeader, Spinner, StatCard, buttonClassName } from '../../components/ui'
import { formatDateTime } from '../../utils/dateDisplay'
import { formatMoney } from '../../utils/format'
import { useMemberResource } from './useMemberResource'

/** MW-03: wallet overview. Every amount comes from the backend ledger; nothing is summed in the browser. */
export function MemberWalletPage() {
  const load = useCallback((signal: AbortSignal) => walletApi.overview(signal), [])
  const [resource, reload] = useMemberResource(load)
  return (
    <div className="page">
      <PageHeader
        eyebrow="Payments"
        title="My Wallet"
        description="Your internal Sportify Wallet balance and its activity."
        actions={<Link className={buttonClassName()} to="/member/wallet/topup">Top up wallet</Link>}
      />
      {resource.status === 'loading' && <Spinner label="Loading your wallet" />}
      {resource.status === 'error' && <ErrorState title="We could not load your wallet" error={resource.error} onRetry={reload} />}
      {resource.status === 'ready' && <WalletContent wallet={resource.data} />}
    </div>
  )
}

function WalletContent({ wallet }: { wallet: WalletOverview }) {
  const hasDemo = wallet.demoAvailableBalance > 0 || wallet.demoReservedBalance > 0
  return (
    <>
      <div className="wallet-stats">
        <StatCard label="Available balance" value={formatMoney(wallet.availableBalance, wallet.currency)} hint="Staff-verified money you can spend" />
        <StatCard label="Held for withdrawal" value={formatMoney(wallet.reservedBalance, wallet.currency)} hint="Not spendable while a withdrawal is open" />
        <StatCard label="Demo balance (not real money)" value={formatMoney(wallet.demoAvailableBalance, wallet.currency)} hint="Demonstration funds only" />
      </div>
      <Notice tone="info">{wallet.notice}</Notice>
      {hasDemo && <Notice tone="warning">You have demo funds. They cannot pay for real services or be withdrawn to a bank.</Notice>}
      <Card title={`Recent activity (${wallet.activityCount})`}>
        {wallet.recentActivity.length === 0 ? (
          <EmptyState title="No wallet activity yet" description="Your top-ups, wallet payments and withdrawals will appear here." />
        ) : (
          <WalletActivityTable entries={wallet.recentActivity} />
        )}
        <p className="health-meta">Last balance update {formatDateTime(wallet.updatedAt)}</p>
      </Card>
    </>
  )
}

export function WalletActivityTable({ entries }: { entries: WalletEntry[] }) {
  return (
    <div className="table-scroll">
      <table className="table">
        <caption>Wallet activity, newest first</caption>
        <thead>
          <tr><th>Date and time</th><th>Activity</th><th>Source</th><th>Amount</th><th>Available after</th><th>Held after</th></tr>
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
              <td>{entry.sourceType} #{entry.sourceId}</td>
              <td className={entry.direction === 'CREDIT' ? 'wallet-amount wallet-amount--in' : 'wallet-amount wallet-amount--out'}>
                {entry.direction === 'CREDIT' ? '+' : '-'}{formatMoney(entry.amount)}
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
