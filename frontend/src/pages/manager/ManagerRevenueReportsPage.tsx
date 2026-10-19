import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../api/errors'
import { managerFinanceApi, type FinanceRevenueRow, type RevenueGroup } from '../../api/managerFinanceApi'
import { Button, Card, Input, PageHeader, PageLoader, Select, StatCard, Table, type TableColumn } from '../../components/ui'
import { useResource } from './useManagerTools'
import { ManagerError, PreviewNotice } from './ManagerShared'
import { FinanceRevenueChart } from './FinanceRevenueChart'
import { formatMoney } from '../../utils/format'
import { tokenStorage } from '../../api/tokenStorage'
import '../../styles/managerFinance.css'

const GROUPS: { value: RevenueGroup; label: string }[] = [
  { value: 'ITEM_TYPE', label: 'Service / item type' },
  { value: 'METHOD', label: 'Payment method' },
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

  const updateFrom = (value: string) => {
    if (!validDate(value)) return
    updateParams({ from: value, ...(value > to ? { to: value } : {}) })
  }

  const updateTo = (value: string) => {
    if (!validDate(value)) return
    updateParams({ to: value, ...(value < from ? { from: value } : {}) })
  }

  const exportCsv = async () => {
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

  return <div className="page manager-page manager-finance-page">
    <PageHeader eyebrow="Manager / Payments & Reports" title="Revenue Reports" description="Review revenue, transaction counts and shares by service, payment method or sport." />
    <PreviewNotice />

    <Card title="Report filters">
      <div className="manager-filters manager-filters--revenue">
        <Input label="From date" type="date" value={from} max={to} onChange={(event) => updateFrom(event.target.value)} />
        <Input label="To date" type="date" value={to} min={from} onChange={(event) => updateTo(event.target.value)} />
        <Select label="Group by" value={groupBy} options={GROUPS} onChange={(event) => updateParams({ groupBy: event.target.value })} />
        <div className="finance-filter-hint">All report values use the selected date range.</div>
      </div>
    </Card>

    {report.status === 'loading' ? <PageLoader label="Loading revenue report" /> : report.status === 'error' ? <ManagerError error={report.error} onRetry={reloadReport} /> : <>
      <section className="finance-kpis" aria-label="Revenue report totals">
        <StatCard label="Revenue" value={formatMoney(report.data.totalRevenue)} />
        <StatCard label="Transactions" value={report.data.totalTransactions.toLocaleString('en-US')} />
        <StatCard label="Grouped by" value={GROUPS.find((option) => option.value === report.data.groupBy)?.label ?? report.data.groupBy} />
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
