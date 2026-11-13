import { businessDate } from '../../utils/date'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { managerFinanceApi, type FinanceRevenueRow, type RevenueGroup } from '../../api/managerFinanceApi'
import { Button, CalendarDateField, Card, PageLoader, Select, StatCard, Table, validateCalendarDateFields, type TableColumn } from '../../components/ui'
import { useResource } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { FinanceRevenueChart } from './FinanceRevenueChart'
import { formatMoney } from '../../utils/format'
import { tokenStorage } from '../../api/tokenStorage'
import { OperationsSummary } from './OperationsSummary'
import '../../styles/managerFinance.css'

const GROUPS: { value: RevenueGroup; label: string }[] = [
  { value: 'ITEM_TYPE', label: 'Service / item type' },
  { value: 'METHOD', label: 'Payment method' },
  { value: 'SPORT', label: 'Sport' },
]

function today(): string {
  return businessDate()
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

function exportFilename(contentDisposition: string | null, from: string, to: string): string {
  const fallback = `revenue_${from}_${to}.csv`
  if (!contentDisposition) return fallback
  const encoded = /(?:^|;)\s*filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition)?.[1]
  const plain = /(?:^|;)\s*filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(contentDisposition)
  let candidate: string | undefined
  try {
    candidate = encoded ? decodeURIComponent(encoded.trim()) : plain?.[1] ?? plain?.[2]?.trim()
  } catch {
    return fallback
  }
  if (!candidate) return fallback
  candidate = candidate.replace(/[\\/\r\n]/g, '_')
  return /^revenue_\d{4}-\d{2}-\d{2}_\d{4}-\d{2}-\d{2}\.csv$/.test(candidate) ? candidate : fallback
}

function downloadReport(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  try {
    link.href = objectUrl
    link.download = filename
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
  } finally {
    link.remove()
    // Let the browser start consuming the URL before releasing it on the next task.
    setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
  }
}

const REPORT_COLUMNS: TableColumn<FinanceRevenueRow>[] = [
  { key: 'label', header: 'Category', render: (row) => <strong>{row.label}</strong> },
  { key: 'revenue', header: 'Revenue', align: 'right', render: (row) => formatMoney(row.revenue) },
  { key: 'transactions', header: 'Transactions', align: 'right', render: (row) => row.transactions.toLocaleString('en-US') },
  { key: 'sharePercent', header: 'Share', align: 'right', render: (row) => `${row.sharePercent.toLocaleString('en-US', { maximumFractionDigits: 2 })}%` },
]

export function ManagerRevenueReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const defaults = dateDefaults()
  const from = validDate(searchParams.get('from')) ? searchParams.get('from')! : defaults.from
  const to = validDate(searchParams.get('to')) ? searchParams.get('to')! : defaults.to
  const groupBy = selectedGroup(searchParams.get('groupBy'))
  const [exportMessage, setExportMessage] = useState('')
  const [exportFailed, setExportFailed] = useState(false)
  const [fromValid, setFromValid] = useState(true)
  const [toValid, setToValid] = useState(true)
  const filtersRef = useRef<HTMLDivElement>(null)
  const datesValid = fromValid && toValid
  const [activeExport, setActiveExport] = useState<{ controller: AbortController; from: string; to: string } | null>(null)
  const exporting = activeExport !== null && activeExport.from === from && activeExport.to === to && !activeExport.controller.signal.aborted
  const exportRequest = useRef<AbortController | null>(null)
  useEffect(() => () => { exportRequest.current?.abort() }, [from, to])

  const reportLoader = useCallback((signal: AbortSignal) => managerFinanceApi.revenue({ from, to, groupBy, signal }), [from, to, groupBy])
  const [report, reloadReport] = useResource(reportLoader)

  const updateParams = (updates: Record<string, string | undefined>) => setSearchParams((current) => {
    const next = new URLSearchParams(current)
    next.delete('sportId')
    for (const [key, value] of Object.entries(updates)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    return next
  }, { replace: true })

  const updateFrom = (value: string, valid = true) => {
    if (!valid || !validDate(value)) return
    updateParams({ from: value, ...(value > to ? { to: value } : {}) })
  }

  const updateTo = (value: string, valid = true) => {
    if (!valid || !validDate(value)) return
    updateParams({ to: value, ...(value < from ? { from: value } : {}) })
  }

  const exportCsv = async () => {
    if (!filtersRef.current || !validateCalendarDateFields(filtersRef.current)) return
    if (report.status !== 'ready' || (exportRequest.current && !exportRequest.current.signal.aborted)) return
    const controller = new AbortController()
    exportRequest.current = controller
    const epoch = tokenStorage.getEpoch()
    const isCurrent = () => !controller.signal.aborted && tokenStorage.getEpoch() === epoch
    setActiveExport({ controller, from, to })
    setExportFailed(false)
    setExportMessage('')
    try {
      const result = await managerFinanceApi.exportRevenue(from, to, { signal: controller.signal })
      if (!isCurrent()) return
      const filename = exportFilename(result.contentDisposition, from, to)
      downloadReport(result.blob, filename)
      setExportMessage(`CSV export ready: ${filename}`)
    } catch (cause) {
      if (!isCurrent()) return
      setExportFailed(true)
      setExportMessage(`The backend CSV export could not be downloaded. ${getErrorMessage(cause)}`)
    } finally {
      if (exportRequest.current === controller) {
        exportRequest.current = null
        if (!controller.signal.aborted) setActiveExport(null)
      }
    }
  }

  return <div className="page manager-page manager-finance-page manager-concept-page">
    <div className="concept-header">
      <div className="concept-header__main">
        <div className="concept-eyebrow">MANAGER WORKSPACE</div>
        <h1 className="concept-title">Revenue & Operations Reports</h1>
        <p className="concept-sub">Analyze business metrics, revenue distributions and operations reports across configurable date ranges.</p>
      </div>
    </div>

    <div className="concept-hero">
      <div>
        <small>ANALYTICS & REPORTING</small>
        <h2>COMPREHENSIVE AUDIT. <span className="concept-goldtext">STRATEGIC VISIBILITY.</span></h2>
        <p>Operational statistics, booking volumes, payment share analysis and exportable reporting ledgers.</p>
      </div>
    </div>

    <PreviewNotice />

    <Card title="Report filters">
      <div ref={filtersRef} className="manager-filters manager-filters--revenue">
        <CalendarDateField label="From date" value={from} max={to} onChange={updateFrom} onValidityChange={setFromValid} required />
        <CalendarDateField label="To date" value={to} min={from} onChange={updateTo} onValidityChange={setToValid} required />
        <Select label="Group by" value={groupBy} options={GROUPS} disabled={!datesValid} onChange={(event) => updateParams({ groupBy: event.target.value })} />
        <div className="finance-filter-hint">All report values use the selected date range.</div>
      </div>
    </Card>

    <OperationsSummary from={from} to={to} />
    {report.status === 'loading' ? <PageLoader label="Loading revenue report" /> : report.status === 'error' ? <ManagerError error={report.error} onRetry={reloadReport} /> : <>
      <section className="finance-kpis" aria-label="Revenue report totals">
        <StatCard icon="receipt" label="Revenue" value={formatMoney(report.data.totalRevenue)} />
        <StatCard icon="rows" label="Transactions" value={report.data.totalTransactions.toLocaleString('en-US')} />
        <StatCard icon="filter" label="Grouped by" value={GROUPS.find((option) => option.value === report.data.groupBy)?.label ?? report.data.groupBy} />
      </section>

      <Card title="Revenue breakdown" actions={<Button onClick={() => void exportCsv()} loading={exporting}>Export CSV</Button>}>
        <FinanceRevenueChart rows={report.data.rows} />
      </Card>
      {exportMessage && <p className={`finance-export-status${exportFailed ? ' finance-export-status--error' : ''}`} role={exportFailed ? 'alert' : 'status'}>{exportMessage}</p>}
      <Card title="Revenue details">
        <Table caption="Revenue report by category" columns={REPORT_COLUMNS} rows={report.data.rows} rowKey={(row) => row.key ?? row.label} emptyTitle="No revenue for this range" emptyDescription="Choose another date range or grouping." />
        <div className="finance-report-total"><span>Total</span><strong>{formatMoney(report.data.totalRevenue)}</strong><strong>{report.data.totalTransactions.toLocaleString('en-US')}</strong><span>-</span></div>
      </Card>
    </>}
  </div>
}
