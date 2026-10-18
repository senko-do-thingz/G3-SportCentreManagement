import { useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { managerFinanceApi, type FinanceTransaction } from '../../api/managerFinanceApi'
import { Card, Input, PageHeader, PageLoader, StatCard, StatusBadge, Table, type TableColumn } from '../../components/ui'
import { useResource } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { FinanceTrendChart } from './FinanceShared'
import { formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import '../../styles/managerFinance.css'

function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
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
  { key: 'reference', header: 'Reference', render: (row) => <strong>{row.invoiceNumber ?? row.reference}</strong> },
  { key: 'service', header: 'Service', render: (row) => <div className="manager-cell"><span className="manager-cell-primary">{row.service ?? 'Service not provided'}</span><span>{row.memberName ?? row.memberCode ?? 'Member not provided'}</span></div> },
  { key: 'amount', header: 'Amount', align: 'right', render: (row) => <strong className="manager-nowrap">{formatMoney(row.amount)}</strong> },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} label={humanizeCode(row.status)} /> },
  { key: 'occurredAt', header: 'Date', render: (row) => formatDateTime(row.occurredAt) },
]

export function ManagerFinancialOverviewPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const month = validMonth(searchParams.get('month'))
  const load = useCallback(async (signal: AbortSignal) => {
    const [overview, trend, recent] = await Promise.all([
      managerFinanceApi.overview(month, { signal }),
      managerFinanceApi.trend(month, { signal }),
      managerFinanceApi.transactions({ month, page: 0, size: 5, signal }),
    ])
    return { overview, trend, recent }
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

  return <div className="page manager-page manager-finance-page">
    <PageHeader eyebrow="Manager / Payments & Reports" title="Financial Overview" description="Monitor collected payments and completed refunds." />
    <PreviewNotice />
    <div className="finance-period-row">
      <Input label="Reporting period" type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
      <Link className="btn btn--primary" to={`/manager/transactions?month=${month}`}>View transactions</Link>
    </div>

    {resource.status === 'loading' ? <PageLoader label="Loading financial overview" /> : resource.status === 'error' ? <ManagerError error={resource.error} onRetry={reload} /> : <>
      <section className="finance-kpis" aria-label="Financial key performance indicators">
        <StatCard label="Payments collected" value={formatMoney(resource.data.overview.paymentsCollected)} />
        <StatCard label="Completed refunds" value={formatMoney(resource.data.overview.completedRefunds)} />
        <StatCard label="Net collected" value={formatMoney(resource.data.overview.netCollected)} />
      </section>

      <div className="finance-overview-grid">
        <Card title="Collected by service">
          {resource.data.overview.collectedByService.length === 0 ? <p className="finance-empty">No collections for this month.</p> : <ul className="finance-breakdown">
            {resource.data.overview.collectedByService.map((item) => {
              const max = Math.max(1, ...resource.data.overview.collectedByService.map((row) => row.collected))
              const width = Math.max(0, Math.min(100, item.collected / max * 100))
              return <li key={item.service}>
                <div className="finance-breakdown__heading"><strong>{item.service}</strong><strong>{formatMoney(item.collected)}</strong></div>
                <div className="finance-breakdown__track" role="progressbar" aria-label={`${item.service} share of collections`} aria-valuemin={0} aria-valuemax={max} aria-valuenow={item.collected}>
                  <span style={{ width: `${width}%` }} />
                </div>
              </li>
            })}
          </ul>}
        </Card>
        <Card title="Needs attention">
          <ul className="finance-attention">
            <li><span>Pending payments</span><Link to="/manager/transactions?status=PENDING">{resource.data.overview.pendingPaymentsCount} requests</Link></li>
            <li><span>Awaiting refund approval</span><Link to="/manager/transactions#refund-queue">{resource.data.overview.pendingRefundApprovalCount} requests</Link></li>
            <li><span>Approved / not yet returned</span><Link to="/manager/transactions?status=APPROVED">{resource.data.overview.approvedNotReturnedCount} requests</Link></li>
          </ul>
          <Link className="btn btn--secondary finance-attention__action" to="/manager/transactions#refund-queue">Review refunds</Link>
        </Card>
      </div>

      <Card title="Six-month revenue trend">
        <FinanceTrendChart points={resource.data.trend} />
      </Card>

      <Card title="Recent transactions" actions={<Link className="btn btn--secondary" to={`/manager/transactions?month=${month}`}>All transactions</Link>}>
        <Table columns={recentColumns} rows={resource.data.recent.content} rowKey={(row) => row.id} caption="Recent transactions" emptyTitle="No recent transactions" emptyDescription="Payments collected in this reporting month will appear here." />
      </Card>

      <Link className="btn btn--primary finance-open-report" to={`/manager/reports?${new URLSearchParams({ from: monthStart(month), to: monthEnd(month) })}`}>Open revenue report</Link>
    </>}
  </div>
}
