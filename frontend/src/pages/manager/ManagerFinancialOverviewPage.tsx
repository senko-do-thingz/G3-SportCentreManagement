import { businessDate } from '../../utils/date'
import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { managerFinanceApi, type FinanceTransaction } from '../../api/managerFinanceApi'
import { Card, Input, PageLoader, StatCard, StatusBadge, Table, type TableColumn } from '../../components/ui'
import { useResource } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { FinanceTrendChart } from './FinanceShared'
import { formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import '../../styles/managerFinance.css'

function currentMonth(): string {
  return businessDate().slice(0, 7)
}

function validMonth(value: string | null): string {
  return value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value) ? value : currentMonth()
}

function monthStart(month: string): string {
  return `${month}-01`
}

function monthEnd(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return `${month}-${String(new Date(year, monthNumber, 0).getDate()).padStart(2, '0')}`
}

const recentColumns: TableColumn<FinanceTransaction>[] = [
  { key: 'reference', header: 'Reference', render: (row) => <strong>{row.invoiceNumber ?? row.code}</strong> },
  { key: 'service', header: 'Service', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.itemName ?? row.itemCode ?? row.sportName ?? 'Service not provided'}</span><span>{row.memberName ?? row.memberCode ?? 'Member not provided'}</span></div> },
  { key: 'amount', header: 'Amount', align: 'right', render: (row) => <strong className="manager-nowrap">{formatMoney(row.amount)}</strong> },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} label={row.status === 'SUCCESS' && row.settlementType === 'SIMULATED' ? 'Paid - Simulated' : humanizeCode(row.status)} /> },
  { key: 'time', header: 'Date', render: (row) => formatDateTime(row.time) },
]

export function ManagerFinancialOverviewPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const month = validMonth(searchParams.get('month'))
  const load = useCallback(async (signal: AbortSignal) => {
    const [overview, trend, recent, serviceBreakdown, pendingRefunds] = await Promise.all([
      managerFinanceApi.overview(month, { signal }),
      managerFinanceApi.trend({ signal }),
      managerFinanceApi.transactions({ month, page: 0, size: 5, signal }),
      managerFinanceApi.revenue({ from: monthStart(month), to: monthEnd(month), groupBy: 'ITEM_TYPE', signal }),
      managerFinanceApi.pendingRefunds({ signal }),
    ])
    return { overview, trend, recent, serviceBreakdown, pendingRefunds }
  }, [month])
  const [resource, reload] = useResource(load)

  const setMonth = (value: string) => {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('month', value)
      return next
    })
  }

  return <div className="page manager-page manager-finance-page manager-concept-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Financial Overview</h1>
        <p className="concept-sub">Monitor paid revenue, pending payments, refunds and monthly trends.</p>
      </div>
      <span className="concept-tag">DESIGN CONCEPT - SAMPLE DATA</span>
    </div>

    <div className="concept-hero">
      <div>
        <small>FINANCIAL DASHBOARD</small>
        <h2>REVENUE DISCIPLINE. <span className="concept-goldtext">AUDITED METRICS.</span></h2>
        <p>Real-time performance indicators and service revenue breakdowns across all sports.</p>
      </div>
    </div>

    <PreviewNotice />
    <div className="finance-period-row">
      <Input label="Reporting period" type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
      <Link className="btn btn--primary" to={`/manager/transactions?month=${month}`}>View transactions</Link>
    </div>

    {resource.status === 'loading' ? <PageLoader label="Loading financial overview" /> : resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <>
      <div className="concept-stats">
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">R</span>
          <div>
            <strong>{formatMoney(resource.data.overview.paidRevenue)}</strong>
            <small>Paid Revenue ({resource.data.overview.paidTransactions} txns)</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">P</span>
          <div>
            <strong>{formatMoney(resource.data.overview.pendingAmount)}</strong>
            <small>Pending Payments ({resource.data.overview.pendingTransactions} txns)</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">F</span>
          <div>
            <strong>{formatMoney(resource.data.overview.refundedAmount)}</strong>
            <small>Refunded Amount ({resource.data.overview.refundedTransactions} completed)</small>
          </div>
        </div>
        <div className="concept-stat">
          <span className="concept-stat__icon" aria-hidden="true">N</span>
          <div>
            <strong>{formatMoney(resource.data.overview.netRevenue)}</strong>
            <small>Net Revenue (After refunds)</small>
          </div>
        </div>
      </div>

      <section className="finance-kpis" aria-label="Financial key performance indicators">
        <StatCard label="Paid revenue" value={formatMoney(resource.data.overview.paidRevenue)} hint={`${resource.data.overview.paidTransactions} successful transactions`} />
        <StatCard label="Pending payments" value={formatMoney(resource.data.overview.pendingAmount)} hint={`${resource.data.overview.pendingTransactions} pending transactions`} />
        <StatCard label="Refunded amount" value={formatMoney(resource.data.overview.refundedAmount)} hint={`${resource.data.overview.refundedTransactions} completed refunds`} />
        <StatCard label="Net revenue" value={formatMoney(resource.data.overview.netRevenue)} hint="Paid revenue minus refunds paid out this month" />
        {(resource.data.overview.simulatedTransactions > 0 || resource.data.overview.simulatedRevenue > 0) && (
          <StatCard label="Simulated QR volume (non-cash)" value={formatMoney(resource.data.overview.simulatedRevenue)} hint={`${resource.data.overview.simulatedTransactions} simulated transactions (excluded from paid revenue)`} />
        )}
      </section>

      <div className="finance-overview-grid">
        <Card title="Collected by service">
          {resource.data.serviceBreakdown.rows.length === 0 ? <p className="finance-empty">No collections for this month.</p> : <ul className="finance-breakdown">
            {resource.data.serviceBreakdown.rows.map((item) => {
              const max = Math.max(1, ...resource.data.serviceBreakdown.rows.map((row) => row.revenue))
              const width = Math.max(0, Math.min(100, item.revenue / max * 100))
              return <li key={item.key ?? item.label}>
                <div className="finance-breakdown__heading"><strong>{item.label}</strong><strong>{formatMoney(item.revenue)}</strong></div>
                <div className="finance-breakdown__track" role="progressbar" aria-label={`${item.label} share of collections`} aria-valuemin={0} aria-valuemax={max} aria-valuenow={item.revenue}>
                  <span style={{ width: `${width}%` }} />
                </div>
              </li>
            })}
          </ul>}
        </Card>
        <Card title="Needs attention">
          <ul className="finance-attention">
            <li><span>Pending payments</span><Link to="/manager/transactions?status=PENDING">{resource.data.overview.pendingTransactions} transactions</Link></li>
            <li><span>Awaiting refund approval</span><Link to="/manager/transactions#refund-queue">{resource.data.pendingRefunds.length} requests</Link></li>
          </ul>
          <Link className="btn btn--secondary finance-attention__action" to="/manager/transactions#refund-queue">Review refunds</Link>
        </Card>
      </div>

      <Card title="Six-month revenue trend through current month">
        <FinanceTrendChart points={resource.data.trend} />
      </Card>

      <Card title="Recent transactions" actions={<Link className="btn btn--secondary" to={`/manager/transactions?month=${month}`}>All transactions</Link>}>
        <Table columns={recentColumns} rows={resource.data.recent.content} rowKey={(row) => row.id} caption="Recent transactions" emptyTitle="No recent transactions" emptyDescription="Payments collected in this reporting month will appear here." />
      </Card>

      <Link className="btn btn--primary finance-open-report" to={`/manager/reports?${new URLSearchParams({ from: monthStart(month), to: monthEnd(month) })}`}>Open revenue report</Link>
    </>}
  </div>
}
