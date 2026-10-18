import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { managerFinanceApi, type ManagerRevenueReport, type RevenueGroup } from '../../api/managerFinanceApi'
import { packagesApi } from '../../api/packagesApi'
import { Button, Card, Input, PageHeader, PageLoader, Select, StatCard, Table, type TableColumn } from '../../components/ui'
import { useResource } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { FinanceRevenueChart } from './FinanceRevenueChart'
import { revenueReportToCsv } from './managerRevenueCsv'
import { formatMoney } from '../../utils/format'
import '../../styles/managerFinance.css'

const GROUPS: { value: RevenueGroup; label: string }[] = [
  { value: 'ITEM_TYPE', label: 'Service / item type' },
  { value: 'PAYMENT_METHOD', label: 'Payment method' },
  { value: 'SPORT', label: 'Sport' },
]

function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function validDate(value: string | null): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function dateDefaults() {
  const end = today()
  return { from: `${end.slice(0, 7)}-01`, to: end }
}

function selectedGroup(value: string | null): RevenueGroup {
  return GROUPS.some((option) => option.value === value) ? value as RevenueGroup : 'ITEM_TYPE'
}

function downloadReport(report: ManagerRevenueReport): string {
  const blob = new Blob([`\uFEFF${revenueReportToCsv(report)}`], { type: 'text/csv;charset=utf-8' })
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const filename = `revenue-${report.from}-${report.to}.csv`
  link.href = objectUrl
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  try {
    link.click()
  } finally {
    link.remove()
    URL.revokeObjectURL(objectUrl)
  }
  return filename
}

const REPORT_COLUMNS: TableColumn<ManagerRevenueReport['rows'][number]>[] = [
  { key: 'category', header: 'Category', render: (row) => <strong>{row.category}</strong> },
  { key: 'collected', header: 'Collected', align: 'right', render: (row) => formatMoney(row.collected) },
  { key: 'refunded', header: 'Refunded', align: 'right', render: (row) => formatMoney(row.refunded) },
  { key: 'netCollected', header: 'Net collected', align: 'right', render: (row) => <strong>{formatMoney(row.netCollected)}</strong> },
]

export function ManagerRevenueReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const defaults = dateDefaults()
  const from = validDate(searchParams.get('from')) ? searchParams.get('from')! : defaults.from
  const to = validDate(searchParams.get('to')) ? searchParams.get('to')! : defaults.to
  const groupBy = selectedGroup(searchParams.get('groupBy'))
  const sportId = searchParams.get('sportId') && /^\d+$/.test(searchParams.get('sportId')!) ? Number(searchParams.get('sportId')) : undefined
  const [exportMessage, setExportMessage] = useState('')

  const reportLoader = useCallback((signal: AbortSignal) => managerFinanceApi.revenue({ from, to, groupBy, sportId, signal }), [from, to, groupBy, sportId])
  const [report, reloadReport] = useResource(reportLoader)
  const sportsLoader = useCallback((signal: AbortSignal) => packagesApi.sports({ signal }), [])
  const [sports] = useResource(sportsLoader)

  const updateParams = (updates: Record<string, string | undefined>) => setSearchParams((current) => {
    const next = new URLSearchParams(current)
    for (const [key, value] of Object.entries(updates)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    return next
  }, { replace: true })

  const updateFrom = (value: string) => {
    if (!validDate(value)) return
    updateParams({ from: value, ...(value > to ? { to: value } : {}) })
  }

  const updateTo = (value: string) => {
    if (!validDate(value)) return
    updateParams({ to: value, ...(value < from ? { from: value } : {}) })
  }

  const changeGroup = (value: RevenueGroup) => updateParams({ groupBy: value, sportId: value === 'SPORT' ? String(sportId ?? '') : undefined })

  const exportCsv = () => {
    if (report.status !== 'ready') return
    try {
      const filename = downloadReport(report.data)
      setExportMessage(`CSV export ready: ${filename}`)
    } catch {
      setExportMessage('The report is ready, but this browser could not start the CSV download. Try again or use the table values.')
    }
  }

  return <div className="page manager-page manager-finance-page">
    <PageHeader eyebrow="Manager / Payments & Reports" title="Revenue Reports" description="Compare collections, refunds and net revenue by service, payment method or sport." />
    <PreviewNotice />

    <Card title="Report filters">
      <div className="manager-filters manager-filters--revenue">
        <Input label="From date" type="date" value={from} max={to} onChange={(event) => updateFrom(event.target.value)} />
        <Input label="To date" type="date" value={to} min={from} onChange={(event) => updateTo(event.target.value)} />
        <Select label="Group by" value={groupBy} options={GROUPS} onChange={(event) => changeGroup(event.target.value as RevenueGroup)} />
        {groupBy === 'SPORT' ? <Select
          label="Sport"
          placeholder="All sports"
          value={sportId === undefined ? '' : String(sportId)}
          options={sports.status === 'ready' ? sports.data.map((sport) => ({ value: String(sport.id), label: sport.name })) : []}
          onChange={(event) => updateParams({ sportId: event.target.value || undefined })}
          hint={sports.status === 'error' ? 'Sport list is unavailable. You can still report across all sports.' : undefined}
        /> : <div className="finance-filter-hint">All report values use the selected date range.</div>}
      </div>
    </Card>

    {report.status === 'loading' ? <PageLoader label="Loading revenue report" /> : report.status === 'error' ? <ManagerError error={report.error} onRetry={reloadReport} /> : <>
      <section className="finance-kpis" aria-label="Revenue report totals">
        <StatCard label="Collected" value={formatMoney(report.data.totals.collected)} />
        <StatCard label="Refunded" value={formatMoney(report.data.totals.refunded)} />
        <StatCard label="Net collected" value={formatMoney(report.data.totals.netCollected)} />
      </section>

      <Card title="Revenue breakdown" actions={<Button onClick={exportCsv}>Export CSV</Button>}>
        <FinanceRevenueChart rows={report.data.rows} />
      </Card>
      {exportMessage && <p className="finance-export-status" role="status">{exportMessage}</p>}
      <Card title="Revenue details">
        <Table caption="Revenue report by category" columns={REPORT_COLUMNS} rows={report.data.rows} rowKey={(row) => row.category} emptyTitle="No revenue for this range" emptyDescription="Choose another date range or grouping." />
        <div className="finance-report-total"><span>Total</span><strong>{formatMoney(report.data.totals.collected)}</strong><strong>{formatMoney(report.data.totals.refunded)}</strong><strong>{formatMoney(report.data.totals.netCollected)}</strong></div>
      </Card>
    </>}
  </div>
}
